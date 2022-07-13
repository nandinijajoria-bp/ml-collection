//package com.bharatpe.lending.service;
//
//import com.bharatpe.common.dao.ExperianDao;
//import com.bharatpe.common.dao.LendingPancardDao;
//import com.bharatpe.common.entities.Experian;
//import com.bharatpe.common.entities.LendingPancard;
//import com.bharatpe.lending.common.dao.CrifRequestResponseDao;
//import com.bharatpe.lending.common.dao.LendingMerchantDropoffDao;
//import com.bharatpe.lending.common.entity.CrifRequestResponse;
//import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
//import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
//import com.bharatpe.lending.common.service.merchant.service.MerchantService;
//import com.bharatpe.lending.constant.CrifConstants;
//import com.bharatpe.lending.dto.CrifResponseDTO;
//import com.bharatpe.lending.util.LoanUtil;
//import com.bharatpe.lending.util.creditresponse.CrifResponseUtil;
//import com.bharatpe.lending.util.creditresponse.ResponseUtil;
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//
//@Service
//public class CrifService {
//
//    private final Logger logger = LoggerFactory.getLogger(CrifService.class);
//
//    @Autowired
//    APIGatewayService apiGatewayService;
//
//    @Autowired
//    LendingPancardDao lendingPancardDao;
//
//    @Autowired
//    MerchantService merchantService;
//
//    @Autowired
//    LoanEligibleService loanEligibleService;
//
//    @Autowired
//    ObjectMapper objectMapper;
//
//    @Autowired
//    ExperianDao experianDao;
//
//    @Autowired
//    LoanUtil loanUtil;
//
//    @Autowired
//    CrifRequestResponseDao crifRequestResponseDao;
//
//    @Autowired
//    LendingMerchantDropoffDao lendingMerchantDropoffDao;
//
//    public CrifResponseDTO getCrif(BasicDetailsDto merchant, String pancard) {
//        try {
//            Experian experian = experianDao.getByMerchantId(merchant.getId());
//            if (experian == null) {
//                logger.info("Experian entry not found for merchant:{}", merchant.getId());
//                return new CrifResponseDTO(true, null);
//            }
//            Map<String, String> merchantName = getFirstLastName(merchant, pancard);
//            String firstName = merchantName.get("first");
//            String lastName = merchantName.get("last");
//            JsonNode crifResponse = getCrifReport(merchant.getMobile().substring(2), pancard, merchant.getId(), firstName, lastName);
//            if (crifResponse != null && crifResponse.get("status") != null && crifResponse.get("status").asText().equals("S11")) {
//                return new CrifResponseDTO(crifResponse.get("buttonbehaviour").asText(), crifResponse.get("question").asText(), objectMapper.readValue(crifResponse.get("optionsList").toString(), new TypeReference<List<String>>(){}));
//            } else if (crifResponse != null) {
//                experian.setResponse(crifResponse.toString());
//                experian.setBureau("CRIF");
//                ResponseUtil responseUtil = new CrifResponseUtil(crifResponse, experianDao, lendingMerchantDropoffDao);
//                Double bureauScore = responseUtil.getBureauScore();
//                if(bureauScore != null) experian.setExperianScore(bureauScore);
//                experian.setReportDate(responseUtil.getReportDate());
//                experianDao.save(experian);
//                loanUtil.auditExperian(experian);
//            }
//        } catch (Exception e) {
//            logger.error("Exception in crif for merchant:{}", merchant.getId(), e);
//            return new CrifResponseDTO(false, "something went wrong");
//        }
//        return new CrifResponseDTO(true, null);
//    }
//
//    public CrifResponseDTO crifAnswer(BasicDetailsDto merchant, String answer) {
//        try {
//            CrifRequestResponse crifRequestResponse = crifRequestResponseDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
//            if (crifRequestResponse == null || crifRequestResponse.getOrderId() == null || crifRequestResponse.getReportId() == null) {
//                logger.info("Crif not found for merchant:{}", merchant.getId());
//                return new CrifResponseDTO(false, "invalid request");
//            }
//            Experian experian = experianDao.getByMerchantId(merchant.getId());
//            if (experian == null) {
//                logger.info("Experian entry not found for merchant:{}", merchant.getId());
//                return new CrifResponseDTO(true, null);
//            }
//            JsonNode crifResponse = getCrifUserAns(crifRequestResponse, answer, merchant.getId(), experian.getPancardNumber(), merchant.getMobile().substring(2));
//            if (crifResponse != null && crifResponse.get("status") != null && crifResponse.get("status").asText().equals("S11")) {
//                return new CrifResponseDTO(crifResponse.get("buttonbehaviour").asText(), crifResponse.get("question").asText(), objectMapper.readValue(crifResponse.get("optionsList").toString(), new TypeReference<List<String>>(){}));
//            } else if (crifResponse != null) {
//                experian.setResponse(crifResponse.toString());
//                experian.setBureau("CRIF");
//                ResponseUtil responseUtil = new CrifResponseUtil(crifResponse, experianDao, lendingMerchantDropoffDao);
//                Double bureauScore = responseUtil.getBureauScore();
//                if(bureauScore != null) experian.setExperianScore(bureauScore);
//                experian.setReportDate(responseUtil.getReportDate());
//                experianDao.save(experian);
//                loanUtil.auditExperian(experian);
//            }
//        } catch (Exception e) {
//            logger.error("Exception in crif user answer for merchant:{}", merchant.getId(), e);
//            return new CrifResponseDTO(false, "something went wrong");
//        }
//        return new CrifResponseDTO(true, null);
//    }
//
//    private Map<String, String> getFirstLastName(BasicDetailsDto merchant, String pancard) {
//        final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
//        BankDetailsDto merchantBankDetail = null;
//        if (bankDetailsDtoOptional.isPresent())
//            merchantBankDetail = bankDetailsDtoOptional.get();
//        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchant.getId());
//        String firstName;
//        String lastName;
//        if (lendingPancard == null || lendingPancard.getName() == null || !lendingPancard.getPancardNumber().equalsIgnoreCase(pancard)) {
//            lendingPancard = loanEligibleService.fetchPanName(pancard, merchant.getId());
//        }
//        if (lendingPancard != null && lendingPancard.getName() != null && !lendingPancard.getName().trim().equalsIgnoreCase("") && lendingPancard.getPancardNumber() != null && lendingPancard.getPancardNumber().equalsIgnoreCase(pancard)) {
//            firstName = loanEligibleService.getFirstName(lendingPancard.getName());
//            lastName = loanEligibleService.getLastName(lendingPancard.getName());
//        } else {
//            firstName = loanEligibleService.getFirstName(merchantBankDetail.getBeneficiaryName());
//            lastName = loanEligibleService.getLastName(merchantBankDetail.getBeneficiaryName());
//        }
//        return new HashMap<String, String>(){{
//            put("first", firstName);
//            put("last", lastName);
//        }};
//    }
//
//
//    private JsonNode getCrifReport(String contact, String panCard, Long merchantId, String firstName, String lastName) {
//        JsonNode stage1Response = apiGatewayService.crifStage1(firstName, lastName, panCard, contact, merchantId);
//        if (stage1Response != null && stage1Response.get("status") != null && stage1Response.get("status").asText().equals("S06")) {
//            logger.info("Crif stage1 success for merchant:{}", merchantId);
//            JsonNode stage2Response = apiGatewayService.crifStage2(merchantId, stage1Response.get("orderId").asText(), stage1Response.get("reportId").asText(), stage1Response.get("redirectURL").asText(), false, "");
//            if (stage2Response != null && stage2Response.get("status") != null && (stage2Response.get("status").asText().equals("S10") || stage2Response.get("status").asText().equals("S01"))) {
//                logger.info("Crif stage2 success for merchant:{}", merchantId);
//                JsonNode stage3Response = apiGatewayService.crifStage2(merchantId, stage1Response.get("orderId").asText(), stage1Response.get("reportId").asText(), stage1Response.get("redirectURL").asText(), true, "");
//                if (stage3Response != null) {
//                    logger.info("Found crif report for merchant:{}", merchantId);
//                    if (isValidReport(panCard, contact, stage3Response)) {
//                        return stage3Response;
//                    } else {
//                        logger.info("Invalid crif report for merchant:{}", merchantId);
//                    }
//                }
//            } else if (stage2Response != null && stage2Response.get("status") != null && stage2Response.get("status").asText().equals("S11")) {
//                logger.info("Crif stage2 Questionnaire for merchant:{}", merchantId);
//                return stage2Response;
//            }
//        }
//        return null;
//    }
//
//    private JsonNode getCrifUserAns(CrifRequestResponse crif, String userAns, Long merchantId, String pancard, String mobile) {
//        JsonNode stage2Response = apiGatewayService.crifStage2(merchantId, crif.getOrderId(), crif.getReportId(), null, false, userAns);
//        if (stage2Response != null && stage2Response.get("status") != null && (stage2Response.get("status").asText().equals("S10") || stage2Response.get("status").asText().equals("S01"))) {
//            logger.info("Crif stage2 success for merchant:{}", crif.getMerchantId());
//            JsonNode stage3Response = apiGatewayService.crifStage2(merchantId, crif.getOrderId(), crif.getReportId(), null, true, "");
//            if (stage3Response != null) {
//                logger.info("Found crif report for merchant:{}", crif.getMerchantId());
//                if (isValidReport(pancard, mobile, stage3Response)) {
//                    return stage3Response;
//                } else {
//                    logger.info("Invalid crif report for merchant:{}", merchantId);
//                }
//            }
//        } else if (stage2Response != null && stage2Response.get("status") != null && stage2Response.get("status").asText().equals("S11")) {
//            logger.info("Crif stage2 Questionnaire for merchant:{}", crif.getMerchantId());
//            return stage2Response;
//        }
//        return null;
//    }
//
//    private boolean isValidReport(String panCard, String phoneNumber, JsonNode response) {
//        boolean checkPan = false;
//        boolean checkPhone = false;
//        if (response != null) {
//            JsonNode personalData = response.get(CrifConstants.REPORT_HEADER)
//                    .get(CrifConstants.PERSONAL_VARIATIONS);
//            if (personalData == null || personalData.toString().equalsIgnoreCase("\"\"")) {
//                return false;
//            }
//            if (personalData.get(CrifConstants.PAN_VARIATIONS) == null
//                    || personalData.get(CrifConstants.PAN_VARIATIONS).toString().equalsIgnoreCase("\"\"")) {
//                return false;
//            }
//            if (personalData.get(CrifConstants.PHONE_VARIATIONS) == null
//                    || personalData.get(CrifConstants.PHONE_VARIATIONS).toString().equalsIgnoreCase("\"\"")) {
//                return false;
//            }
//            List<JsonNode> panVariations = LoanUtil
//                    .jsonNodeArrayUtil(personalData.get(CrifConstants.PAN_VARIATIONS).get(CrifConstants.VARIATION));
//            List<JsonNode> phoneVariations = LoanUtil
//                    .jsonNodeArrayUtil(personalData.get(CrifConstants.PHONE_VARIATIONS).get(CrifConstants.VARIATION));
//            if (phoneNumber.length() > 10) {
//                phoneNumber = phoneNumber.substring(2);// remove 91
//            }
//            for (JsonNode pan : panVariations) {
//                checkPan = pan.get("VALUE").asText().equalsIgnoreCase(panCard);
//                if(checkPan) break;
//            }
//            for (JsonNode phone : phoneVariations) {
//                checkPhone = phone.get("VALUE").asText().equalsIgnoreCase(phoneNumber) || phone.get("VALUE").asText().equalsIgnoreCase("91" + phoneNumber);
//                if(checkPhone) break;
//            }
//        }
//        return checkPan && checkPhone;
//    }
//
//}
