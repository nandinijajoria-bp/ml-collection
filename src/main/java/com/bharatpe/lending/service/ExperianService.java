package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
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
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(headers);
        Long a = DateTime.now().getMillis();
        String url = "https://cbv2cpu.uat.experian.in:16443/ECV-P2/content/singleAction.action?clientName=BHARATPE_FM&allowInput=1&allowEdit=1&allowCaptcha=1&allowConsent=1&allowEmailVerify=1&allowVoucher=1&voucherCode=BharatPepZBZv&firstName="+firstName+"&surName="+lastName+"&dateOfBirth="+experianDetailsDTO.getDob()+"&gender="+experianDetailsDTO.getGender()+"&mobileNo="+contact+"&flatno="+experianDetailsDTO.getAddress()+"&city="+experianDetailsDTO.getCity()+"&state="+experianDetailsDTO.getState()+"&pincode="+experianDetailsDTO.getPincode()+"&pan="+panCard+"&noValidationByPass=0&emailConditionalByPass=1";
        logger.info("ExperianV2 long API request for merchant: {} is {}", merchantId, url);
        String response = restTemplate.postForObject(url, request, String.class);
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
                return objectMapper.readTree(jsonObject.toString());
            } else if (!jsonNode.get("errorString").isNull() && jsonNode.get("errorString").textValue().contains("Validation Failed")) {
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

    private List<String> fetchMaskedMobileNumbers(String stageOneId, String stageTwoId, Long merchantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(headers);
        Long a = DateTime.now().getMillis();
        String url = "https://cbv2cpu.uat.experian.in:16443/ECV-P2/content/generateMaskedDeliveryData.action?clientName=BHARATPE_FM&stgOneHitId="+stageOneId+"&stgTwoHitId="+stageTwoId;
        logger.info("ExperianV2 mobile API request for merchant: {} is {}", merchantId, url);
        String response = restTemplate.postForObject(url, request, String.class);
        Long b = DateTime.now().getMillis();
        logger.info("ExperianV2 mobile API response time---" + (b-a) + "ms");
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode != null && !jsonNode.get("maskMobileno").isNull()) {
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
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(headers);
            Long a = DateTime.now().getMillis();
            String url = "https://cbv2cpu.uat.experian.in:16443/ECV-P2/content/authenticateDeliveryData.action?stgOneHitId="+experianDetails.getStageOneId()+"&ActualEmailADDR&stgTwoHitId="+experianDetails.getStageTwoId()+"&ActualMobileNumber="+mobile;
            logger.info("ExperianV2 authenticate API request for merchant: {} is {}", merchantId, url);
            String response = restTemplate.postForObject(url, request, String.class);
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
