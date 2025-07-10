package com.bharatpe.lending.service;

import com.bharatpe.lending.common.dao.LendingMerchantReferencesDao;
import com.bharatpe.lending.common.entity.LendingMerchantReferences;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for handling merchant references data.
 * This service will be used for the merchant reference stage and related data.
 * Code related to this stage will be migrated here in the future.
 */
@Service
@Slf4j
public class MerchantReferencesDataService {
    private final LendingMerchantReferencesDao lendingMerchantReferencesRepo;


    public MerchantReferencesDataService(LendingMerchantReferencesDao lendingMerchantReferencesRepo) {
        this.lendingMerchantReferencesRepo = lendingMerchantReferencesRepo;
    }

    public List<LendingMerchantReferences> getMerchantReferencesByMerchantIdAndLender(long merchantId, String lender) {
        log.info("Fetching merchant references for merchantId: {} and lender: {}", merchantId, lender);
        List<Object[]> results = lendingMerchantReferencesRepo.findByMerchantIdAndLender(merchantId, lender);
        if (CollectionUtils.isEmpty(results)) {
            log.warn("No merchant references found for merchantId: {} and lender: {}", merchantId, lender);
            return Collections.emptyList();
        }

        return results.stream()
                .map(result -> {
                    try {
                        LendingMerchantReferences lmr = new LendingMerchantReferences();
                        lmr.setApplicationId(result[0] != null ? ((Number) result[0]).longValue() : null);
                        lmr.setReferenceName(result[1] != null ? String.valueOf(result[1]) : null);
                        lmr.setReferenceNumber(result[2] != null ? String.valueOf(result[2]) : null);
                        lmr.setInferredRelation(result[3] != null ? String.valueOf(result[3]) : null);
                        lmr.setUpdatedAt(result[4] != null ? (Date) result[4] : null);
                        return lmr;
                    } catch (Exception e) {
                        log.error("Error while Parsing DB results of merchant references: {}", result, e);
                        return null;
                    }

                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
