package com.bharatpe.lending.scheduler;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.service.EligibilityComputationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ComputeEligibilityScheduler {

    @Autowired
    LendingCache lendingCache;

    @Autowired
    EligibilityComputationService eligibilityComputationService;

    @Value("${compute.eligibility.from.prod:false}")
    private boolean isComputeEligibilityEnabled;

    @Scheduled(initialDelay = 1000 * 60 * 1, fixedDelay = 1000 * 60 * 5)
    public void computeEligibility() {

        if (!isComputeEligibilityEnabled) return;

        String key = LendingConstants.COMPUTE_GLOBAL_LIMIT;
        Long size = lendingCache.sizeOfSet(key);
        log.info("size of redis set of key: {} is {}", key, size);
        size = size < 1000 ? size : 1000;

        if (size == 0 || !lendingCache.isKeyExist(key)) return;

        List<Object> poppedValues = lendingCache.popValuesFromSet(key, size);
        eligibilityComputationService.processBatch(poppedValues);
    }

}
