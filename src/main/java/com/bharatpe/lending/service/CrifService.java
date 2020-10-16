package com.bharatpe.lending.service;

import com.bharatpe.common.dao.LendingPancardDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.LendingPancard;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.lending.common.dao.CrifAuditTrailDao;
import com.bharatpe.lending.common.dao.CrifDao;
import com.bharatpe.lending.common.entity.Crif;
import com.bharatpe.lending.common.entity.CrifAuditTrail;
import com.bharatpe.lending.dto.CrifResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CrifService {

    private final Logger logger = LoggerFactory.getLogger(CrifService.class);

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    CrifDao crifDao;

    @Autowired
    CrifAuditTrailDao crifAuditTrailDao;

    @Autowired
    LendingPancardDao lendingPancardDao;

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    LoanEligibleService loanEligibleService;

    @Autowired
    ObjectMapper objectMapper;

    public CrifResponseDTO getCrif(Merchant merchant, String pancard) {
        try {
            Crif crif = crifDao.findByMerchantId(merchant.getId());
            if (crif != null && crif.getResponse() != null) {
                logger.info("Crif report already exist for merchant:{}", merchant.getId());
                return new CrifResponseDTO(true, null);
            }
            Map<String, String> merchantName = getFirstLastName(merchant);
            String firstName = merchantName.get("first");
            String lastName = merchantName.get("last");
            if (crif == null) {
                crif = new Crif(merchant.getId(), firstName, lastName, pancard, merchant.getMobile().substring(2));
            } else {
                crif.setFirstName(firstName);
                crif.setLastName(lastName);
                crif.setPancard(pancard);
            }
            JsonNode crifResponse = getCrifReport(merchant.getMobile().substring(2), pancard, merchant.getId(), firstName, lastName, crif);
            crifDao.save(crif);
            crifAuditTrailDao.save(CrifAuditTrail.createObject(crif));
            if (crifResponse != null && crifResponse.get("status") != null && crifResponse.get("status").asText().equals("S11")) {
                return new CrifResponseDTO(crifResponse.get("buttonbehaviour").asText(), crifResponse.get("question").asText(), objectMapper.readValue(crifResponse.get("optionsList").toString(), new TypeReference<List<String>>(){}));
            }
        } catch (Exception e) {
            logger.error("Exception in crif for merchant:{}", merchant.getId(), e);
            return new CrifResponseDTO(false, "something went wrong");
        }
        return new CrifResponseDTO(true, null);
    }

    public CrifResponseDTO crifAnswer(Merchant merchant, String answer) {
        try {
            Crif crif = crifDao.findByMerchantId(merchant.getId());
            if (crif == null || crif.getOrderId() == null || crif.getReportId() == null || crif.getResponse() != null) {
                logger.info("Crif not found for merchant:{}", merchant.getId());
                return new CrifResponseDTO(false, "invalid request");
            }
            JsonNode crifResponse = getCrifUserAns(crif, answer, merchant.getId());
            crifDao.save(crif);
            crifAuditTrailDao.save(CrifAuditTrail.createObject(crif));
            if (crifResponse != null && crifResponse.get("status") != null && crifResponse.get("status").asText().equals("S11")) {
                return new CrifResponseDTO(crifResponse.get("buttonbehaviour").asText(), crifResponse.get("question").asText(), objectMapper.readValue(crifResponse.get("optionsList").toString(), new TypeReference<List<String>>(){}));
            }
        } catch (Exception e) {
            logger.error("Exception in crif user answer for merchant:{}", merchant.getId(), e);
            return new CrifResponseDTO(false, "something went wrong");
        }
        return new CrifResponseDTO(true, null);
    }

    private Map<String, String> getFirstLastName(Merchant merchant) {
        MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchant.getId());
        String firstName;
        String lastName;
        if (lendingPancard != null && lendingPancard.getName() != null && !lendingPancard.getName().trim().equalsIgnoreCase("")) {
            firstName = loanEligibleService.getFirstName(lendingPancard.getName());
            lastName = loanEligibleService.getLastName(lendingPancard.getName());
        } else {
            firstName = loanEligibleService.getFirstName(merchantBankDetail.getBeneficiaryName());
            lastName = loanEligibleService.getLastName(merchantBankDetail.getBeneficiaryName());
        }
        return new HashMap<String, String>(){{
            put("first", firstName);
            put("last", lastName);
        }};
    }


    private JsonNode getCrifReport(String contact, String panCard, Long merchantId, String firstName, String lastName, Crif crif) {
        JsonNode stage1Response = apiGatewayService.crifStage1(firstName, lastName, panCard, contact, merchantId);
        if (stage1Response != null && stage1Response.get("status") != null && stage1Response.get("status").asText().equals("S06")) {
            logger.info("Crif stage1 success for merchant:{}", merchantId);
            crif.setOrderId(stage1Response.get("orderId").asText());
            crif.setReportId(stage1Response.get("reportId").asText());
            JsonNode stage2Response = apiGatewayService.crifStage2(merchantId, stage1Response.get("orderId").asText(), stage1Response.get("reportId").asText(), stage1Response.get("redirectURL").asText(), false, "");
            if (stage2Response != null && stage2Response.get("status") != null && stage2Response.get("status").asText().equals("S10")) {
                logger.info("Crif stage2 success for merchant:{}", merchantId);
                JsonNode stage3Response = apiGatewayService.crifStage2(merchantId, stage1Response.get("orderId").asText(), stage1Response.get("reportId").asText(), stage1Response.get("redirectURL").asText(), true, "");
                if (stage3Response != null) {
                    logger.info("Found crif report for merchant:{}", merchantId);
                    crif.setResponse(stage3Response.toString());
                    return stage3Response;
                }
            } else if (stage2Response != null && stage2Response.get("status") != null && stage2Response.get("status").asText().equals("S11")) {
                logger.info("Crif stage2 Questionnaire for merchant:{}", merchantId);
                return stage2Response;
            }
        }
        return null;
    }

    private JsonNode getCrifUserAns(Crif crif, String userAns, Long merchantId) {
        JsonNode stage2Response = apiGatewayService.crifStage2(merchantId, crif.getOrderId(), crif.getReportId(), null, false, userAns);
        if (stage2Response != null && stage2Response.get("status") != null && stage2Response.get("status").asText().equals("S01")) {
            logger.info("Crif stage2 success for merchant:{}", crif.getMerchantId());
            JsonNode stage3Response = apiGatewayService.crifStage2(merchantId, crif.getOrderId(), crif.getReportId(), null, true, "");
            if (stage3Response != null) {
                logger.info("Found crif report for merchant:{}", crif.getMerchantId());
                crif.setResponse(stage3Response.toString());
                return stage3Response;
            }
        } else if (stage2Response != null && stage2Response.get("status") != null && stage2Response.get("status").asText().equals("S11")) {
            logger.info("Crif stage2 Questionnaire for merchant:{}", crif.getMerchantId());
            return stage2Response;
        }
        return null;
    }

}
