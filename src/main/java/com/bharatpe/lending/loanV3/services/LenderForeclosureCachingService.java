package com.bharatpe.lending.loanV3.services;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dto.LenderForeclosureDetailsDTO;
import com.bharatpe.lending.lendingplatform.lms.service.ForeclosureService;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ONE_LMS;


@Slf4j
@Component
public class LenderForeclosureCachingService {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LendingCache lendingCache;

    @Autowired
    private EasyLoanUtil easyLoanUtil;

    @Autowired
    private LenderAssociationStageFactory lenderAssociationStageFactory;

    @Autowired
    private ForeclosureService foreclosureService;

    @Value("${lenderForeclosureDetails.caching.enabled.lenders:}")
    private String lenderForeclosureCachingEnabledLenders;

    @Value("${lenderForeclosureDetails.caching.start.time:08:00}")
    private String lenderForeclosureCachingStartTime;

    @Value("${lenderForeclosureDetails.caching.end.time:22:00}")
    private String lenderForeclosureCachingEndTime;

    @Value("#{${lenderForeclosureDetails.lender.rollout.percentage:{}}}")
    private Map<String,Integer> lenderForeclosureDetailsLenderRolloutPercentage = new HashMap<>();;

    private static final String LENDER_FORECLOSURE_DETAILS_CACHING_KEY = "%s_foreclosure_details_%s";

    public LenderForeclosureDetailsDTO getLenderForeclosureAmount(String lender, Long applicationId, Long merchantId) {
        try {
            LenderForeclosureDetailsDTO lenderForeclosureDetails = null;
            Boolean isCachingApplicable = isCachingApplicableForLenderForeclosureDetails(lender, merchantId);
            if(isCachingApplicable) {
                lenderForeclosureDetails = fetchCachedLenderForeclosureDetails(lender, applicationId);
                if(!ObjectUtils.isEmpty(lenderForeclosureDetails)) {
                    log.info("returning cached lenderForeclosureDetails {} of {} for applicationId {}", lenderForeclosureDetails, lender, applicationId);
                    return lenderForeclosureDetails;
                }
            }
            ILenderAssociationService iLenderAssociationService = lenderAssociationStageFactory.getStageAssociatedLenderService(LenderAssociationStages.FORECLOSURE_FETCH.name()).getLenderAssociationService(lender);
            if(!ObjectUtils.isEmpty(iLenderAssociationService)) {
                lenderForeclosureDetails = (LenderForeclosureDetailsDTO) iLenderAssociationService.invoke(applicationId, null);
                if (!ObjectUtils.isEmpty(lenderForeclosureDetails)
                        && !ObjectUtils.isEmpty(lenderForeclosureDetails.getForeclosureAmount())
                        && lenderForeclosureDetails.getForeclosureAmount() != 0D) {
                    cacheLenderForeclosureDetails(lenderForeclosureDetails, lender, applicationId, isCachingApplicable);
                    return lenderForeclosureDetails;
                }
            }
        } catch (Exception e) {
            log.error("Exception in fetching {} foreclosure amount for application {} {}", lender, applicationId, Arrays.asList(e.getStackTrace()));
        }
        return null;
    }


    public LenderForeclosureDetailsDTO getLenderForeclosureAmount(String lender, Long applicationId, Long merchantId, LendingPaymentSchedule lendingPaymentSchedule) {
        try {
            LenderForeclosureDetailsDTO lenderForeclosureDetails = null;
            Boolean isCachingApplicable = isCachingApplicableForLenderForeclosureDetails(lender, merchantId);
            if(isCachingApplicable) {
                lenderForeclosureDetails = fetchCachedLenderForeclosureDetails(lender, applicationId);
                if(!ObjectUtils.isEmpty(lenderForeclosureDetails)) {
                    log.info("returning cached lenderForeclosureDetails {} of {} for applicationId {}", lenderForeclosureDetails, lender, applicationId);
                    return lenderForeclosureDetails;
                }
            }
            if(ONE_LMS.equalsIgnoreCase(lendingPaymentSchedule.getLmsSource())){
                Double foreclosureAmount = foreclosureService.getLenderForeclosureAmount(lendingPaymentSchedule);
                lenderForeclosureDetails= new LenderForeclosureDetailsDTO();
                lenderForeclosureDetails.setForeclosureAmount(foreclosureAmount);
                return lenderForeclosureDetails;
            }
            ILenderAssociationService iLenderAssociationService = lenderAssociationStageFactory.getStageAssociatedLenderService(LenderAssociationStages.FORECLOSURE_FETCH.name()).getLenderAssociationService(lender);
            if(!ObjectUtils.isEmpty(iLenderAssociationService)) {
                lenderForeclosureDetails = (LenderForeclosureDetailsDTO) iLenderAssociationService.invoke(applicationId, null);
                if (!ObjectUtils.isEmpty(lenderForeclosureDetails)
                        && !ObjectUtils.isEmpty(lenderForeclosureDetails.getForeclosureAmount())
                        && lenderForeclosureDetails.getForeclosureAmount() != 0D) {
                    cacheLenderForeclosureDetails(lenderForeclosureDetails, lender, applicationId, isCachingApplicable);
                    return lenderForeclosureDetails;
                }
            }
        } catch (Exception e) {
            log.error("Exception in fetching {} foreclosure amount for application {} {}", lender, applicationId, Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private Boolean isCachingApplicableForLenderForeclosureDetails(String lender, Long merchantId) {
        try {
            if (!lenderForeclosureCachingEnabledLenders.contains(lender)) {
                log.info("caching not enabled for {} lenderForeclosureDetails", lender);
                return false;
            }
            if (!easyLoanUtil.percentScaleUp(merchantId, lenderForeclosureDetailsLenderRolloutPercentage.getOrDefault(lender, 0))) {
                log.info("lenderForeclosureDetails caching of {} is not enabled for merchantId {}", lender, merchantId);
                return false;
            }
            LocalTime cacheStartTime = LocalTime.parse(lenderForeclosureCachingStartTime);
            LocalTime cacheEndTime = LocalTime.parse(lenderForeclosureCachingEndTime);
            if (LocalTime.now().isBefore(cacheStartTime) || LocalTime.now().isAfter(cacheEndTime)) {
                log.info("lenderForeclosureDetails caching is not applicable before {} and after {}", cacheStartTime, cacheEndTime);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Exception in checking lenderForeclosureDetails caching applicability for merchantId {} {}", merchantId, Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private LenderForeclosureDetailsDTO fetchCachedLenderForeclosureDetails(String lender, Long applicationId) {
        try {
            String key = String.format(LENDER_FORECLOSURE_DETAILS_CACHING_KEY, lender, applicationId);
            log.info("fetching cached lenderForeClosureDetails against key {} for applicationId {}", key, applicationId);
            Object cachedResponse = lendingCache.get(String.format(LENDER_FORECLOSURE_DETAILS_CACHING_KEY, lender, applicationId));
            return objectMapper.convertValue(cachedResponse, LenderForeclosureDetailsDTO.class);
        } catch (Exception e) {
            log.error("Exception in fetching cachedLenderForeclosureDetails of {} for applicationId {} {}", lender, applicationId, Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private void cacheLenderForeclosureDetails(LenderForeclosureDetailsDTO lenderForeclosureDetails, String lender, Long applicationId, Boolean isCachingApplicable) {
        try {
            if(isCachingApplicable) {
                log.info("caching lenderForeclosureDetails {} of {} for applicationId {}", lenderForeclosureDetails, lender, applicationId);
                LocalTime cacheEndTime = LocalTime.parse(lenderForeclosureCachingEndTime);
                Integer ttlInMinutes = Math.toIntExact(Duration.between(LocalTime.now(), cacheEndTime).getSeconds() / 60);
                AddCacheDto addCacheDto = new AddCacheDto();
                addCacheDto.setKey(String.format(LENDER_FORECLOSURE_DETAILS_CACHING_KEY, lender, applicationId));
                addCacheDto.setTtl(ttlInMinutes);
                addCacheDto.setValue(lenderForeclosureDetails);
                lendingCache.add(addCacheDto, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.error("Exception in caching lenderForeclosureDetails of {} for applicationId {} {}", lender, applicationId, Arrays.asList(e.getStackTrace()));
        }
    }

    public void evictLenderForeclosureDetailsCache(String lender, Long applicationId) {
        try {
            log.info("evicting caching of lenderForeclosureDetails for applicationId {}", applicationId);
            lendingCache.delete(String.format(LENDER_FORECLOSURE_DETAILS_CACHING_KEY, lender, applicationId));
        } catch (Exception e) {
            log.error("Exception in evict caching of lenderForeclosureDetails for applicationId {} {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
    }

}
