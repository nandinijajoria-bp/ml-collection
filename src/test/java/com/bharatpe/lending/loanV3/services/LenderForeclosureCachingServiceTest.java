package com.bharatpe.lending.loanV3.services;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dto.LenderForeclosureDetailsDTO;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.stages.ForeClosureAmtStageSvcFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.class)
@TestConfiguration
public class LenderForeclosureCachingServiceTest {

    @InjectMocks
    private LenderForeclosureCachingService lenderForeclosureCachingService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LendingCache lendingCache;

    @Mock
    private EasyLoanUtil easyLoanUtil;

    @Mock
    private LenderAssociationStageFactory lenderAssociationStageFactory;

    @Mock
    private ForeClosureAmtStageSvcFactory lenderAssociationServiceFactory;

    @Mock
    private ILenderAssociationService<LenderForeclosureDetailsDTO> iLenderAssociationService;

    private static final String lender = "ABFL,PIRAMAL";
    private static final Long applicationId = 123L;
    private static final Long merchantId = 456L;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(lenderForeclosureCachingService, "lenderForeclosureCachingEnabledLenders", lender);
        ReflectionTestUtils.setField(lenderForeclosureCachingService, "lenderForeclosureCachingStartHour", 8);
        ReflectionTestUtils.setField(lenderForeclosureCachingService, "lenderForeclosureCachingEndHour", 22);

        Map<String, Integer> rolloutMap = new HashMap<>();
        rolloutMap.put("ABFL", 100);
        rolloutMap.put("PIRAMAL", 50);
        ReflectionTestUtils.setField(lenderForeclosureCachingService, "lenderForeclosureDetailsLenderRolloutPercentage", rolloutMap);
    }

    @Test
    public void testCacheHitWhenCachingApplicable() {
        LenderForeclosureDetailsDTO cachedDto = new LenderForeclosureDetailsDTO();
        cachedDto.setForeclosureAmount(10000D);

        when(easyLoanUtil.percentScaleUp(anyLong(), anyInt())).thenCallRealMethod();
        when(lendingCache.get(anyString())).thenReturn(cachedDto);
        when(objectMapper.convertValue(cachedDto, LenderForeclosureDetailsDTO.class)).thenReturn(cachedDto);

        LenderForeclosureDetailsDTO result = lenderForeclosureCachingService.getLenderForeclosureAmount("ABFL", applicationId, merchantId);

        assertNotNull(result);
        assertEquals(Optional.of(10000D), Optional.ofNullable(result.getForeclosureAmount()));
        verify(lendingCache).get(anyString());
        verify(easyLoanUtil).percentScaleUp(anyLong(), anyInt());
    }

    @Test
    public void testCacheMissThenFetchFromService() {
        when(easyLoanUtil.percentScaleUp(anyLong(), anyInt())).thenCallRealMethod();
        when(lendingCache.get(anyString())).thenReturn(null);
        when(lendingCache.add(any(), any())).thenReturn(true);

        LenderForeclosureDetailsDTO serviceResponse = new LenderForeclosureDetailsDTO();
        serviceResponse.setForeclosureAmount(20000D);
        when(lenderAssociationStageFactory.getStageAssociatedLenderService(LenderAssociationStages.FORECLOSURE_FETCH.name())).thenReturn(lenderAssociationServiceFactory);
        when(lenderAssociationServiceFactory.getLenderAssociationService(anyString())).thenReturn(iLenderAssociationService);
        when(iLenderAssociationService.invoke(anyLong(), isNull())).thenReturn(serviceResponse);
        LenderForeclosureDetailsDTO result = lenderForeclosureCachingService.getLenderForeclosureAmount("ABFL", applicationId, merchantId);

        assertNotNull(result);
        assertEquals(Optional.of(20000D), Optional.ofNullable(result.getForeclosureAmount()));
        verify(lendingCache).get(anyString());
        verify(lendingCache).add(any(), any());
        verify(easyLoanUtil).percentScaleUp(anyLong(), anyInt());
    }

    @Test
    public void testCacheMissThenFetchFromServiceWithNull() {
        when(easyLoanUtil.percentScaleUp(anyLong(), anyInt())).thenCallRealMethod();
        when(lendingCache.get(anyString())).thenReturn(null);

        when(lenderAssociationStageFactory.getStageAssociatedLenderService(LenderAssociationStages.FORECLOSURE_FETCH.name())).thenReturn(lenderAssociationServiceFactory);
        when(lenderAssociationServiceFactory.getLenderAssociationService(anyString())).thenReturn(iLenderAssociationService);
        when(iLenderAssociationService.invoke(anyLong(), isNull())).thenReturn(null);
        LenderForeclosureDetailsDTO result = lenderForeclosureCachingService.getLenderForeclosureAmount("ABFL", applicationId, merchantId);

        assertNull(result);
        verify(lendingCache).get(anyString());
        verify(easyLoanUtil).percentScaleUp(anyLong(), anyInt());
    }


    @Test
    public void testMerchantNotInRolloutPercentCachingNotApplicable() {
        when(easyLoanUtil.percentScaleUp(anyLong(), anyInt())).thenCallRealMethod();

        LenderForeclosureDetailsDTO serviceResponse = new LenderForeclosureDetailsDTO();
        serviceResponse.setForeclosureAmount(30000D);
        when(lenderAssociationStageFactory.getStageAssociatedLenderService(LenderAssociationStages.FORECLOSURE_FETCH.name())).thenReturn(lenderAssociationServiceFactory);
        when(lenderAssociationServiceFactory.getLenderAssociationService(anyString())).thenReturn(iLenderAssociationService);
        when(iLenderAssociationService.invoke(anyLong(), isNull())).thenReturn(serviceResponse);

        LenderForeclosureDetailsDTO result = lenderForeclosureCachingService.getLenderForeclosureAmount("PIRAMAL", applicationId, merchantId);

        assertNotNull(result);
        assertEquals(Optional.of(30000D), Optional.ofNullable(result.getForeclosureAmount()));
        verify(lendingCache, never()).get(anyString());
        verify(easyLoanUtil).percentScaleUp(anyLong(), anyInt());
    }

    @Test
    public void testLenderNotInEnabledListCachingNotApplicable() {
        LenderForeclosureDetailsDTO serviceResponse = new LenderForeclosureDetailsDTO();
        serviceResponse.setForeclosureAmount(30000D);
        when(lenderAssociationStageFactory.getStageAssociatedLenderService(LenderAssociationStages.FORECLOSURE_FETCH.name())).thenReturn(lenderAssociationServiceFactory);
        when(lenderAssociationServiceFactory.getLenderAssociationService(anyString())).thenReturn(iLenderAssociationService);
        when(iLenderAssociationService.invoke(anyLong(), isNull())).thenReturn(serviceResponse);

        LenderForeclosureDetailsDTO result = lenderForeclosureCachingService.getLenderForeclosureAmount("SMFG", applicationId, merchantId);

        assertNotNull(result);
        assertEquals(Optional.of(30000D), Optional.ofNullable(result.getForeclosureAmount()));
        verify(lendingCache, never()).get(anyString());
        verify(easyLoanUtil, never()).percentScaleUp(anyLong(), anyInt());
    }

    @Test
    public void testOutsideCacheTimeWindowCachingNotApplicable() {
        ReflectionTestUtils.setField(lenderForeclosureCachingService, "lenderForeclosureCachingStartHour", 22);
        ReflectionTestUtils.setField(lenderForeclosureCachingService, "lenderForeclosureCachingEndHour", 23);
        when(easyLoanUtil.percentScaleUp(anyLong(), anyInt())).thenCallRealMethod();
        LenderForeclosureDetailsDTO serviceResponse = new LenderForeclosureDetailsDTO();
        serviceResponse.setForeclosureAmount(30000D);
        when(lenderAssociationStageFactory.getStageAssociatedLenderService(LenderAssociationStages.FORECLOSURE_FETCH.name())).thenReturn(lenderAssociationServiceFactory);
        when(lenderAssociationServiceFactory.getLenderAssociationService(anyString())).thenReturn(iLenderAssociationService);
        when(iLenderAssociationService.invoke(anyLong(), isNull())).thenReturn(serviceResponse);

        LenderForeclosureDetailsDTO result = lenderForeclosureCachingService.getLenderForeclosureAmount("ABFL", applicationId, merchantId);

        assertNotNull(result);
        assertEquals(Optional.of(30000D), Optional.ofNullable(result.getForeclosureAmount()));
        verify(lendingCache, never()).get(anyString());
        verify(easyLoanUtil).percentScaleUp(anyLong(), anyInt());
    }

    @Test
    public void testEvictLenderForeclosureDetailsCache() {
        when(lendingCache.delete(anyString())).thenReturn(true);
        lenderForeclosureCachingService.evictLenderForeclosureDetailsCache(lender, applicationId);
        verify(lendingCache, times(1)).delete(anyString());
    }
}
