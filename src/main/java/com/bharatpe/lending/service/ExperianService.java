package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dto.ExperianDetailsDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.handlers.GupShupOTPHandler;
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
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExperianService {

    Logger logger = LoggerFactory.getLogger(ExperianService.class);

    @Autowired
    ExperianDao experianDao;

    @Autowired
    ExperianAuditTrailDao experianAuditTrailDao;

    @Autowired
    LendingPancardDao lendingPancardDao;

    @Autowired
    LoanEligibleService loanEligibleService;

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ExperianDetailsDao experianDetailsDao;

    @Autowired
    GupShupOTPHandler gupShupOTPHandler;

    public ResponseDTO updateDetails(ExperianDetailsDTO experianDetailsDTO, Long merchantId, String contact) {
        Experian experian = experianDao.getByMerchantId(merchantId);
        if (!validateRequest(experianDetailsDTO) || experian == null) {
            return new ResponseDTO(false, "Invalid request", null);
        }
        MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "ACTIVE");
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
        ExperianDetails experianDetails = new ExperianDetails(merchantId, experian.getId(), experianDetailsDTO.getPincode(), experianDetailsDTO.getState(), experianDetailsDTO.getCity(), experianDetailsDTO.getAddress(), experianDetailsDTO.getGender(), experianDetailsDTO.getDob());
        List<String> maskedMobiles = new ArrayList<>();
        JsonNode experianResponse = fetchExperianDetails(firstName, lastName, contact, experian.getPancardNumber(), experianDetailsDTO, merchantId, maskedMobiles, experianDetails);
        experianDetailsDao.deleteByMerchantIdAndExperianId(merchantId, experian.getId());
        experianDetailsDao.save(experianDetails);
        if (experianResponse != null) {
            experian.setResponse(experianResponse.toString());
            experianDao.save(experian);
            experianAuditTrailDao.save(ExperianAuditTrail.createObject(experian));
            return new ResponseDTO(true, null, null);
        } else if (!maskedMobiles.isEmpty()){
            return new ResponseDTO(false, null, maskedMobiles);
        } else {
            return new ResponseDTO(true, null, null);
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
        Long a = DateTime.now().getMillis();
        logger.info("ExperianV2 long API request for merchant: {} is {}", merchantId, body.toString());
        String response = restTemplate.postForObject(ExperianConstants.LONG_API_URL, request, String.class);
        Long b = DateTime.now().getMillis();
        logger.info("ExperianV2 long API response time---" + (b-a) + "ms");
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode == null) {
                return null;
            }
            if (!jsonNode.get("showHtmlReportForCreditReport").isNull()) {
                String xmlResponse = jsonNode.get("showHtmlReportForCreditReport").textValue().replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("&quot;", "\"");
                JSONObject jsonObject = XML.toJSONObject(xmlResponse);
                logger.info("Successfully found experian report for merchant: {}", merchantId);
                return objectMapper.readTree(jsonObject.toString());
            } else if (!jsonNode.get("errorString").isNull() && jsonNode.get("errorString").textValue().contains("Validation Failed")) {
                logger.info("Validation Failed for merchant: {}", merchantId);
                String stageOneId = jsonNode.get("stageOneId_").textValue();
                String stageTwoId = jsonNode.get("stageTwoId_").textValue();
                experianDetails.setStageOneId(stageOneId);
                experianDetails.setStageTwoId(stageTwoId);
                maskedMobiles.addAll(fetchMaskedMobileNumbers(stageOneId, stageTwoId, merchantId));
                if (!maskedMobiles.isEmpty()) {
                    experianDetails.setMaskedMobile(maskedMobiles.toString());
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("Exception while parsing experianV2 long API response", e);
            logger.info("ExperianV2 long API response is---" + response);
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
        body.add("voucherCode", "BharatPepZBZv");
        body.add("firstName", firstName);
        body.add("surName", lastName);
        body.add("dateOfBirth", experianDetailsDTO.getDob());
        body.add("gender", experianDetailsDTO.getGender());
        body.add("mobileNo", contact);
        body.add("flatno", experianDetailsDTO.getAddress());
        body.add("city", experianDetailsDTO.getCity());
        body.add("state", experianDetailsDTO.getState());
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
            logger.error("Exception while parsing experianV2 mobile API response", e);
            logger.info("ExperianV2 mobile API response is---" + response);
        }
        logger.info("Masked mobile numbers not found for merchant: {}", merchantId);
        return new ArrayList<>();
    }

    public ResponseDTO sendOtp(String mobile, Merchant merchant) {
        String message = "BharatPe: %code% is your OTP to register yourself on BharatPe Merchant App. BharatPe.com";
        Boolean otp1 = gupShupOTPHandler.sendOTP(mobile, message);
        if (otp1) {
            logger.info("OTP sent on mobile: {} for merchant: {}", mobile, merchant.getId());
        }
        Boolean otp2 = gupShupOTPHandler.sendOTP(merchant.getMobile(), message);
        if (otp2) {
            logger.info("OTP sent on mobile: {} for merchant: {}", merchant.getMobile(), merchant.getId());
        }
        return new ResponseDTO(true, null, null);
    }

    public ResponseDTO verifyOtp(String mobile, Merchant merchant, String otp) {
        Boolean isOTPVerified = gupShupOTPHandler.verifyOTP(merchant.getMobile(), otp);
        if (isOTPVerified) {
            authenticateExperian(merchant.getId(), mobile);
            return new ResponseDTO(true, null, null);
        }
        isOTPVerified = gupShupOTPHandler.verifyOTP(mobile, otp);
        if (isOTPVerified) {
            authenticateExperian(merchant.getId(), mobile);
            return new ResponseDTO(true, null, null);
        }
        return new ResponseDTO(false, "Invalid OTP", null);
    }

    private void authenticateExperian(Long merchantId, String mobile){
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
                    experianDao.save(experian);
                    experianAuditTrailDao.save(ExperianAuditTrail.createObject(experian));
                    logger.info("Successfully found experian report for merchant: {}", merchantId);
                } else {
                    logger.info("Experian Report not found for merchant: {} with mobile: {}", merchantId, mobile);
                }
            } catch (IOException e) {
                logger.error("Exception while parsing experianV2 authenticate API response", e);
                logger.info("ExperianV2 authenticate API response is---" + response);
            }

        }
    }
}
