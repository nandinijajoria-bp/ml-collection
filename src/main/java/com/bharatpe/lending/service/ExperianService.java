package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.handlers.EmailHandler;
import com.bharatpe.lending.common.dao.ExperianRawResponseDao;
import com.bharatpe.lending.common.entity.ExperianRawResponse;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.ExperianDetailsDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.handlers.GupShupOTPHandler;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ExperianService {

    Logger logger = LoggerFactory.getLogger(ExperianService.class);

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingPancardDao lendingPancardDao;

    @Autowired
    LoanEligibleService loanEligibleService;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ExperianDetailsDao experianDetailsDao;

    @Autowired
    GupShupOTPHandler gupShupOTPHandler;

    @Autowired
    EmailHandler emailHandler;
    
    @Autowired
    ExperianRawResponseDao experianRawResponseDao;

    @Autowired
    MerchantService merchantService;

    public ResponseDTO updateDetails(ExperianDetailsDTO experianDetailsDTO, Long merchantId, String contact) {
        Experian experian = experianDao.getByMerchantId(merchantId);
        if (!validateRequest(experianDetailsDTO) || experian == null) {
            return new ResponseDTO(false, "Invalid request", null,null);
        }
        final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
        BankDetailsDto merchantBankDetail = null;
        if (bankDetailsDtoOptional.isPresent())
            merchantBankDetail = bankDetailsDtoOptional.get();
        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
        String firstName;
        String lastName;
        if (lendingPancard != null && lendingPancard.getName() != null && !lendingPancard.getName().trim().equalsIgnoreCase("")) {
            firstName = loanEligibleService.getFirstName(lendingPancard.getName());
            lastName = loanEligibleService.getLastName(lendingPancard.getName());
        } else {
            firstName = loanEligibleService.getFirstName(merchantBankDetail.getBeneficiaryName());
            lastName = loanEligibleService.getLastName(merchantBankDetail.getBeneficiaryName());
        }
        ExperianDetails experianDetails = new ExperianDetails(merchantId, experian.getId(), experianDetailsDTO.getPincode(), "07", experianDetailsDTO.getCity(), experianDetailsDTO.getAddress(), experianDetailsDTO.getGender(), experianDetailsDTO.getDob());
        List<String> maskedMobiles = new ArrayList<>();
        JsonNode experianResponse;
        try {
            experianResponse = fetchExperianDetails(firstName, lastName, contact, experian.getPancardNumber(), experianDetailsDTO, merchantId, maskedMobiles, experianDetails);
        } catch (Exception e) {
            experianResponse = null;
            if (!experianDetailsDTO.isRetry()) {
                return new ResponseDTO(false, "timeout", null,null);
            }
        }
        experianDetailsDao.deleteByMerchantId(merchantId);
        experianDetailsDao.save(experianDetails);
        if (experianResponse != null) {
            experian.setResponse(experianResponse.toString());
            experian.setBureau(LendingConstants.BUREAU_TYPES.EXPERIAN.name());
            experianDao.save(experian);
            loanUtil.auditExperian(experian);
            return new ResponseDTO(true, null, null,null);
        } else {
            ResponseDTO responseDTO = new ResponseDTO(true, null, null,null);
            responseDTO.setCrif(true);
            return responseDTO;
        }
    }

    private boolean validateRequest(ExperianDetailsDTO experianDetailsDTO) {
        return experianDetailsDTO.getPincode() != null && experianDetailsDTO.getState() != null && experianDetailsDTO.getCity() != null &&
                experianDetailsDTO.getAddress() != null && experianDetailsDTO.getGender() != null && experianDetailsDTO.getDob() != null;
    }

    private JsonNode fetchExperianDetails(String firstName, String lastName, String contact, String panCard, ExperianDetailsDTO experianDetailsDTO, Long merchantId, List<String> maskedMobiles, ExperianDetails experianDetails) {
        if (contact.length() > 10) {
            contact = contact.substring(2);//remove 91
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        setLongApiParams(body, firstName, lastName, contact, panCard, experianDetailsDTO);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            Long a = DateTime.now().getMillis();
            logger.info("ExperianV2 long API request for merchant: {} is {}", merchantId, body.toString());
            String response = restTemplate.postForObject(ExperianConstants.LONG_API_URL, request, String.class);
            Long b = DateTime.now().getMillis();
            logger.info("ExperianV2 long API response time---" + (b-a) + "ms");
            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode == null) {
                insertExperianCallRecord(null, "LONG_API_URL", objectMapper.writeValueAsString(request), merchantId, null, panCard, contact);
                return null;
            }
            if (!jsonNode.get("showHtmlReportForCreditReport").isNull()) {
                String xmlResponse = jsonNode.get("showHtmlReportForCreditReport").textValue().replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("&quot;", "\"");
                JSONObject jsonObject = XML.toJSONObject(xmlResponse);
                logger.info("Successfully found experian report for merchant: {}", merchantId);
                insertExperianCallRecord(objectMapper.readTree(jsonObject.toString()).toString(), "LONG_API_URL", objectMapper.writeValueAsString(request), merchantId, null, panCard, contact);
                return objectMapper.readTree(jsonObject.toString());
            } else if (!jsonNode.get("errorString").isNull() && jsonNode.get("errorString").textValue().contains("Validation Failed")) {
                logger.info("Validation Failed for merchant: {}", merchantId);
                insertExperianCallRecord(null, "LONG_API_URL", objectMapper.writeValueAsString(request), merchantId, null, panCard, contact);
                String stageOneId = jsonNode.get("stageOneId_").textValue();
                String stageTwoId = jsonNode.get("stageTwoId_").textValue();
                experianDetails.setStageOneId(stageOneId);
                experianDetails.setStageTwoId(stageTwoId);
//                maskedMobiles.addAll(fetchMaskedMobileNumbers(stageOneId, stageTwoId, merchantId));
//                if (!maskedMobiles.isEmpty()) {
//                    experianDetails.setMaskedMobile(maskedMobiles.toString());
//                }
            }
            return null;
        } catch (ResourceAccessException e) {
            logger.info("ExperianV2 API timeout", e);
            throw new RuntimeException("Timeout");
        } catch (Exception e) {
            emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");}}, "Experian Long API Exception", "");
            logger.error("Exception while parsing experianV2 long API response", e);
            return null;
        }
    }

    private void setLongApiParams(MultiValueMap<String, Object> body, String firstName, String lastName, String contact, String panCard, ExperianDetailsDTO experianDetailsDTO) {
        body.add("clientName", "BHARATPE_FM");
        body.add("allowInput", "1");
        body.add("allowEdit", "1");
        body.add("allowCaptcha", "1");
        body.add("allowConsent", "1");
        body.add("allowEmailVerify", "1");
        body.add("allowVoucher", "1");
//        body.add("voucherCode", "BharatPebxIYY");
        body.add("voucherCode", "BharatPeOVaSv");
        body.add("firstName", firstName);
        body.add("surName", lastName);
        body.add("dateOfBirth", experianDetailsDTO.getDob());
        body.add("gender", experianDetailsDTO.getGender());
        body.add("mobileNo", contact);
        body.add("flatno", experianDetailsDTO.getAddress());
        body.add("city", experianDetailsDTO.getCity());
        body.add("state", "07");
        body.add("pincode", experianDetailsDTO.getPincode());
        body.add("pan", panCard);
        body.add("noValidationByPass", "0");
        body.add("emailConditionalByPass", "1");
    }

    private List<String> fetchMaskedMobileNumbers(String stageOneId, String stageTwoId, Long merchantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("clientName", "BHARATPE_FM");
        body.add("stgOneHitId", stageOneId);
        body.add("stgTwoHitId", stageTwoId);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        Long a = DateTime.now().getMillis();
        logger.info("ExperianV2 mobile API request for merchant: {} is {}", merchantId, body.toString());
        String response = restTemplate.postForObject(ExperianConstants.MASKED_MOBILE_URL, request, String.class);
        try {
			insertExperianCallRecord(response, "MASKED_MOBILE_URL", objectMapper.writeValueAsString(request), merchantId, null, null, null);
		} catch (Exception e) {
			logger.error("Error occured while inserting experian call record",e);
		}
        Long b = DateTime.now().getMillis();
        logger.info("ExperianV2 mobile API response time---" + (b-a) + "ms");
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode != null && !jsonNode.get("maskMobileno").isNull()) {
                logger.info("Found Masked mobile numbers for merchant: {}", merchantId);
                List<String> maskedMobiles = new ArrayList<>();
                for (JsonNode maskMobileno : jsonNode.get("maskMobileno")) {
                    maskedMobiles.add(maskMobileno.textValue());
                }
                return maskedMobiles;
            }
        } catch (Exception e) {
            emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");}}, "Experian Masked Mobile Exception", "");
            logger.error("Exception while parsing experianV2 mobile API response", e);
            logger.info("ExperianV2 mobile API response is---" + response);
        }
        logger.info("Masked mobile numbers not found for merchant: {}", merchantId);
        return new ArrayList<>();
    }

    public ResponseDTO sendOtp(String mobile, BasicDetailsDto merchant) {
        String message = "BharatPe: %code% is your OTP to register yourself on BharatPe Merchant App. BharatPe.com";
        Boolean otp1 = gupShupOTPHandler.sendOTP(mobile, message);
        if (otp1) {
            logger.info("OTP sent on mobile: {} for merchant: {}", mobile, merchant.getId());
        }
        Boolean otp2 = gupShupOTPHandler.sendOTP(merchant.getMobile(), message);
        if (otp2) {
            logger.info("OTP sent on mobile: {} for merchant: {}", merchant.getMobile(), merchant.getId());
        }
        return new ResponseDTO(true, null, null,null);
    }

    public ResponseDTO verifyOtp(String mobile, BasicDetailsDto merchant, String otp, boolean retry) {
        Boolean isOTPVerified = gupShupOTPHandler.verifyOTP(merchant.getMobile(), otp);
        ExperianDetails experianDetails = experianDetailsDao.findByMerchantId(merchant.getId());
        try {
            if (isOTPVerified) {
                boolean experianFound = authenticateExperian(merchant.getId(), mobile);
                experianDetails.setOtpVerified(true);
                experianDetailsDao.save(experianDetails);
                ResponseDTO responseDTO = new ResponseDTO(true, null, null,null);
                if (!experianFound) {
                    responseDTO.setCrif(true);
                }
                return responseDTO;
            }
            isOTPVerified = gupShupOTPHandler.verifyOTP(mobile, otp);
            if (isOTPVerified) {
                boolean experianFound = authenticateExperian(merchant.getId(), mobile);
                experianDetails.setOtpVerified(true);
                experianDetailsDao.save(experianDetails);
                ResponseDTO responseDTO = new ResponseDTO(true, null, null,null);
                if (!experianFound) {
                    responseDTO.setCrif(true);
                }
                return responseDTO;
            }
        } catch (Exception e) {
            if (!retry) {
                return new ResponseDTO(false, "timeout", null,null);
            } else {
                experianDetails.setOtpVerified(true);
                experianDetailsDao.save(experianDetails);
                return new ResponseDTO(true, null, null,null);
            }
        }
        return new ResponseDTO(false, "Invalid OTP", null,null);
    }

    private boolean authenticateExperian(Long merchantId, String mobile){
        ExperianDetails experianDetails = experianDetailsDao.findByMerchantId(merchantId);
        if (experianDetails != null && experianDetails.getStageOneId() != null && experianDetails.getStageTwoId() != null) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("stgOneHitId", experianDetails.getStageOneId());
            body.add("ActualEmailADDR", "");
            body.add("stgTwoHitId", experianDetails.getStageTwoId());
            body.add("ActualMobileNumber", mobile);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            Long a = DateTime.now().getMillis();
            logger.info("ExperianV2 authenticate API request for merchant: {} is {}", merchantId, body.toString());
            String response = restTemplate.postForObject(ExperianConstants.AUTHENTICATE_MOBILE_URL, request, String.class);
            Long b = DateTime.now().getMillis();
            logger.info("ExperianV2 authenticate API response time---" + (b-a) + "ms");
            try {
                JsonNode jsonNode = objectMapper.readTree(response);
                if (jsonNode != null && !jsonNode.get("showHtmlReportForCreditReport").isNull()) {
                    String xmlResponse = jsonNode.get("showHtmlReportForCreditReport").textValue().replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("&quot;", "\"");
                    JSONObject jsonObject = XML.toJSONObject(xmlResponse);
                    JsonNode experianResponse = objectMapper.readTree(jsonObject.toString());
                    Experian experian = experianDao.getByMerchantId(merchantId);
                    experian.setResponse(experianResponse.toString());
                    experian.setBureau(LendingConstants.BUREAU_TYPES.EXPERIAN.name());
                    experianDao.save(experian);
                    loanUtil.auditExperian(experian);
                    insertExperianCallRecord(experianResponse.toString(), "AUTHENTICATE_MOBILE_URL", objectMapper.writeValueAsString(request), merchantId, null, null, mobile);
                    logger.info("Successfully found experian report for merchant: {}", merchantId);
                    return true;
                } else {
                    insertExperianCallRecord(null, "AUTHENTICATE_MOBILE_URL", objectMapper.writeValueAsString(request), merchantId, null, null, mobile);
                    logger.info("Experian Report not found for merchant: {} with mobile: {}", merchantId, mobile);
                }
            } catch (IOException e) {
                emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");}}, "Experian Authenticate Exception", "");
                logger.error("Exception while parsing experianV2 authenticate API response", e);
                logger.info("ExperianV2 authenticate API response is---" + response);
            }

        }
        return false;
    }
	
	public void insertExperianCallRecord(String response,String apiName,String request,Long merchantId,Double bpScore, String pancard, String mobile) {
		try {
			logger.info("Inserting experian call detail into ExperianRawResponse");
			ExperianRawResponse experianRawResponse=new ExperianRawResponse();
			experianRawResponse.setBpScore(bpScore);
			experianRawResponse.setMerchantId(merchantId);
			experianRawResponse.setMobile(mobile);
			experianRawResponse.setPancard(pancard);
			experianRawResponse.setApiName(apiName);
			experianRawResponse.setRequest(request);
			experianRawResponse.setResponse(response);
			experianRawResponseDao.save(experianRawResponse);
		}
		catch(Exception e){
			logger.error("Error occured while inserting experian call details",e);
		}
	}
}
