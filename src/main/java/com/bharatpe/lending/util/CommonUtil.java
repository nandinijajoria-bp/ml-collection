package com.bharatpe.lending.util;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.stream.IntStream;

@Slf4j
@Component
public class CommonUtil {

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    public String fetchLoanPurposeByApplicatioId(Long applicationId){
        if(Objects.isNull(applicationId)){
            log.info("invalid applicationId");
            return null;
        }
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
        if(!ObjectUtils.isEmpty(lendingApplicationDetails)){
            return lendingApplicationDetails.getLoanPurpose();
        }
        log.info("lendingApplicationDetails not found with applicationId : {}", applicationId);
        return null;
    }

    public String loanPurposeMapping(String loanPurpose) {
        if (loanPurpose == null) {
            return null;
        }

        switch (loanPurpose.toLowerCase()) {
            case "business_expansion":
                return "Business Expansion";
            case "working_capital_requirement":
                return "Working Capital Requirement";
            case "shop_renovation":
                return "Shop Renovation";
            case "purchase_of_equipment":
                return "Purchase of Equipment";
            case "others":
                return "Others";
            default:
                return null;
        }
    }

    public void saveApplicationRejectionAudit(LendingApplication lendingApplication, String newStatus, String oldStatus, String type, String remarks){
        LendingAuditTrial auditLender = new LendingAuditTrial();
        auditLender.setApplicationId(lendingApplication.getId());
        auditLender.setMerchantId(lendingApplication.getMerchantId());
        auditLender.setType(type);
        auditLender.setLoanId(lendingApplication.getExternalLoanId());
        auditLender.setOldStatus(oldStatus);
        auditLender.setNewStatus(newStatus);
        auditLender.setRemarks(remarks);
        log.info("Audit Trail: {}", auditLender);
        lendingAuditTrialDao.save(auditLender);
    }

    public boolean isAllConsecutiveLetters(String name) {
        String strippedName = name.replaceAll("\\s+", "");
        if (strippedName.length() < 2) return false; // Less than 2 chars cannot be consecutive

        for (int i = 0; i < strippedName.length() - 1; i++) {
            if (strippedName.charAt(i + 1) - strippedName.charAt(i) != 1) {
                return false; // If any two chars are not consecutive, return false
            }
        }

        return true; // All letters are consecutive
    }

    public boolean hasAtLeastThreeConsecutiveChars(String name) {
        String[] parts = name.split("\\s+"); // Split by one or more spaces

        for (String part : parts) {
            if (part.length() >= 3) {
                return true; // If any part has 3 or more consecutive characters
            }
        }
        return false; // No part had 3 consecutive characters
    }

    public boolean hasMoreThanFourConsecutiveNumbers(String mobile) {
        return IntStream.range(0, mobile.length() - 4)
                .anyMatch(i ->
                        IntStream.range(1, 5)
                                .allMatch(j -> Character.getNumericValue(mobile.charAt(i + j)) == Character.getNumericValue(mobile.charAt(i)) + j)
                                || IntStream.range(1, 5)
                                .allMatch(j -> Character.getNumericValue(mobile.charAt(i + j)) == Character.getNumericValue(mobile.charAt(i)) - j)
                );
    }

    public boolean hasMoreThanFourSameDigits(String mobile) {
        return IntStream.range(0, mobile.length() - 4)
                .anyMatch(i -> mobile.substring(i, i + 5).chars()
                        .allMatch(c -> c == mobile.charAt(i)));
    }

    public String calculateHmacHex(String payload, String secret) {
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        Mac mac = null;
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] result = mac.doFinal(payload.getBytes());
            return Hex.encodeHexString(result);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Exception hashing payload", e);
        }
    }

    public String getPayload(Object object) throws JsonProcessingException {
        ObjectMapper objectMap = new ObjectMapper();
        return objectMap.writeValueAsString(object);
    }
}
