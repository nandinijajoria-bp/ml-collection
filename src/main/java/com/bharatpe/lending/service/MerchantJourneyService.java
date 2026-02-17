package com.bharatpe.lending.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.lending.common.dto.MerchantListBankResponseDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.constant.CommonConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class MerchantJourneyService {
    private static final String LIST_BANK_CACHE_KEY = "LENDING_MERCHANT_LIST_BANK";

    private final MerchantService merchantService;
    private final LendingCache lendingCache;
    
    @Value("${merchant.listbank.cache.minutes:60}")
    private int merchantListBankCacheMinutes;

    public static final String FAILURE_MESSAGE = "Failed to fetch bank list. Please try after sometime";

    public MerchantListBankResponseDto getBankList(String token, BasicDetailsDto merchant) {
        try {
            Object cachedValue = lendingCache.get(LIST_BANK_CACHE_KEY);
            if (!ObjectUtils.isEmpty(cachedValue)) {
                log.info("Returning list bank response from cache for merchantId: {}", merchant.getId());
                return (MerchantListBankResponseDto) cachedValue;
            }
        } catch (Exception e) {
            log.info("Exception while fetching cache for: {}, error: {}", merchant.getId(), e.getMessage(), e);
        }

        MerchantListBankResponseDto bankListResponse = merchantService.getBankList(token);
        if (bankListResponse != null && !CollectionUtils.isEmpty(bankListResponse.getData())) {
            try {
                AddCacheDto addCacheDto = new AddCacheDto();
                addCacheDto.setKey(LIST_BANK_CACHE_KEY);
                addCacheDto.setTtl(merchantListBankCacheMinutes);
                addCacheDto.setValue(bankListResponse);
                lendingCache.add(addCacheDto, TimeUnit.MINUTES);
                log.info("List bank response cached successfully");
            } catch (Exception e) {
                log.info("Failed to cache response for: {}, error: {}",merchant.getId(), e.getMessage(), e);
            }
            return bankListResponse;
        }
        return new MerchantListBankResponseDto(false, 200, FAILURE_MESSAGE, null, CommonConstants.FAILED, merchant.getMobile());
    }
}
