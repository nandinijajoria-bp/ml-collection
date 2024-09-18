package com.bharatpe.lending.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.EligibleLoan;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.LendingPincodesDao;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.LendingPincodes;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.MileStoneDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.MileStoneEntity;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.RTEProgramType;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.BureauResponseDTO;
import com.bharatpe.lending.loanV2.dto.Eligibility;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@TestConfiguration
public class MileStoneProgramServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private MileStoneDao mileStoneDao;

    @Mock
    private DsHandler dsHandler;

    @Mock
    private ExperianDao experianDao;

    @Mock
    private MileStoneHelperService mileStoneHelperService;

    @Mock
    private LendingPincodesDao lendingPincodesDao;

    @Mock
    private LendingCache lendingCache;

    @Mock
    private FunnelService funnelService;

    @Mock
    private KycHandler kycHandler;

    @Mock
    private MerchantSummaryHandler merchantSummaryHandler;

    @Mock
    private LoanDashboardService loanDashboardService;

    @Mock
    private EligibleLoanDao eligibleLoanDao;

    @Mock
    private DateTimeUtil dateTimeUtil;

    @Mock
    private APIGatewayService apiGatewayService;

    @Mock
    private MileStoneHelperServicev3 mileStoneHelperServicev3;

    @Mock
    private EasyLoanUtil easyLoanUtil;

    @InjectMocks
    private MileStoneProgramService mileStoneProgramService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCheckEligibility_Eligible() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        String loanAmount = "10000";
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        MileStoneEligibilityResponseDto eligibilityResponseDto = new MileStoneEligibilityResponseDto();
        eligibilityResponseDto.setMilStoneEligibility(true);

        when(easyLoanUtil.percentScaleUp(any(Long.class), anyInt())).thenReturn(true);
        when(mileStoneHelperServicev3.calculateEligibility(any(BasicDetailsDto.class), eq(true)))
                .thenReturn(eligibilityResponseDto);

        ApiResponse<MileStoneEligibilityResponseDto> response = mileStoneProgramService.checkEligibility(merchant, loanAmount);

        verify(easyLoanUtil, times(1)).percentScaleUp(eq(merchant.getId()), anyInt());
        verify(mileStoneHelperServicev3, times(1)).calculateEligibility(eq(merchant), eq(true));
        verify(lendingCache, times(1)).add(any(AddCacheDto.class), eq(TimeUnit.DAYS));
        assertEquals("200", response.getErrorCode());
        assertEquals("ELIGIBLE", response.getMessage());
    }

    @Test
    public void testCheckEligibility_Ineligible() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000754L);
        String loanAmount = "10000";
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        MileStoneEligibilityResponseDto eligibilityResponseDto = new MileStoneEligibilityResponseDto();

        when(easyLoanUtil.percentScaleUp(any(Long.class), anyInt())).thenReturn(true);
        when(mileStoneHelperServicev3.calculateEligibility(any(BasicDetailsDto.class), eq(true)))
                .thenReturn(eligibilityResponseDto);
        eligibilityResponseDto.setMilStoneEligibility(false);

        ApiResponse<MileStoneEligibilityResponseDto> response = mileStoneProgramService.checkEligibility(merchant, loanAmount);

        assertEquals("INELIGIBLE", response.getMessage());
        assertEquals(false, response.getData().getMilStoneEligibility());
    }

    @Test
    public void testProgramSummary_FoundFromTable() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        MileStoneEntity mileStoneEntity = new MileStoneEntity();
        mileStoneEntity.setMerchantId(merchant.getId());
        mileStoneEntity.setSessionStatus("IN_PROGRESS");

        DSMileStoneResponse mileStoneResponse = new DSMileStoneResponse();
        Target t1 = new Target();
        t1.setMilestone_no(1);
        Target t2 = new Target();
        t2.setMilestone_no(2);

        ArrayList<Target> targets = new ArrayList<>();
        targets.add(t1);
        targets.add(t2);
        mileStoneResponse.setTarget(targets);

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), eq("IN_PROGRESS")))
                .thenReturn(mileStoneEntity);
        when(mileStoneHelperService.fetchTarget(any(MileStoneEntity.class)))
                .thenReturn(mileStoneResponse);

        ApiResponse<DSMileStoneResponse> response = mileStoneProgramService.programSummary(merchant);

        assertEquals(mileStoneResponse, response.getData());
    }

    @Test
    public void testProgramSummary_PincodeNotFound() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), eq("IN_PROGRESS")))
                .thenReturn(null);
        when(experianDao.getByMerchantId(merchant.getId())).thenReturn(null);

        ApiResponse<DSMileStoneResponse> response = mileStoneProgramService.programSummary(merchant);

        assertEquals("400", response.getErrorCode());
        assertEquals("PINCODE_NOT_FOUND", response.getMessage());
    }

    @Test
    public void testProgramSummary_PancardNotFound() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        Experian experian = new Experian();
        experian.setPincode(123456);
        LendingPincodes lendingPincodes = new LendingPincodes();
        lendingPincodes.setColor(PincodeColor.GREEN);

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), eq("IN_PROGRESS")))
                .thenReturn(null);
        when(experianDao.getByMerchantId(merchant.getId())).thenReturn(experian);
        when(lendingPincodesDao.findByPincode(anyInt())).thenReturn(lendingPincodes);
        when(kycHandler.getPanNumber(merchant.getId())).thenReturn(null);

        ApiResponse<DSMileStoneResponse> response = mileStoneProgramService.programSummary(merchant);

        assertEquals("400", response.getErrorCode());
        assertEquals("PANCARD_NOT_FOUND", response.getMessage());
    }

    @Test
    public void testProgramSummary_FoundByDSApi() {
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), eq("IN_PROGRESS")))
                .thenReturn(null);

        Experian experian = new Experian();
        experian.setPincode(123456);

        LendingPincodes lendingPincodes = new LendingPincodes();
        lendingPincodes.setColor(PincodeColor.GREEN);

        BureauResponseDTO bureauResponseDTO = new BureauResponseDTO();
        bureauResponseDTO.setIsNTC(false);
        BureauResponseDTO.BureauVariables variables = new BureauResponseDTO.BureauVariables();
        variables.setBureauScore(700.0);
        variables.setBbs(50.0);
        bureauResponseDTO.setVariables(variables);

        DSMileStoneResponse mileStoneResponse = new DSMileStoneResponse();
        Target t1 = new Target();
        t1.setMilestone_no(1);
        Target t2 = new Target();
        t2.setMilestone_no(2);

        ArrayList<Target> targets = new ArrayList<>();
        targets.add(t1);
        targets.add(t2);
        mileStoneResponse.setTarget(targets);

        when(experianDao.getByMerchantId(merchant.getId())).thenReturn(experian);
        when(lendingPincodesDao.findByPincode(123456)).thenReturn(lendingPincodes);
        when(kycHandler.getPanNumber(merchant.getId())).thenReturn("ABCDE1234F");
        when(mileStoneHelperService.calculateBureauScore("ABCDE1234F", merchant)).thenReturn(bureauResponseDTO);
        when(easyLoanUtil.percentScaleUp(any(), anyInt())).thenReturn(true);
        when(dsHandler.fetchMileStoneDatav3(merchant.getId(), 700.0, 50.0, "GREEN", "25k")).thenReturn(mileStoneResponse);

        ApiResponse<DSMileStoneResponse> response = mileStoneProgramService.programSummary(merchant);
        assertNotNull(response);
        assertEquals(mileStoneResponse.getTarget(), response.getData().getTarget());
    }

    @Test
    public void testProgramSummary_BureauResponseOrDsResponseNotFound() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), eq("IN_PROGRESS")))
                .thenReturn(null);

        Experian experian = new Experian();
        experian.setPincode(123456);

        LendingPincodes lendingPincodes = new LendingPincodes();
        lendingPincodes.setColor(PincodeColor.GREEN);

        BureauResponseDTO bureauResponseDTO = new BureauResponseDTO();
        bureauResponseDTO.setIsNTC(false);
        BureauResponseDTO.BureauVariables variables = new BureauResponseDTO.BureauVariables();
        variables.setBureauScore(700.0);
        variables.setBbs(50.0);
        bureauResponseDTO.setVariables(variables);

        when(experianDao.getByMerchantId(merchant.getId())).thenReturn(experian);
        when(lendingPincodesDao.findByPincode(123456)).thenReturn(lendingPincodes);
        when(kycHandler.getPanNumber(merchant.getId())).thenReturn("ABCDE1234F");
        when(mileStoneHelperService.calculateBureauScore("ABCDE1234F", merchant)).thenReturn(bureauResponseDTO);
        when(easyLoanUtil.percentScaleUp(merchant.getId(), 10)).thenReturn(true);
        when(dsHandler.fetchMileStoneDatav3(merchant.getId(), 700.0, 50.0, "GREEN", "25k")).thenReturn(null);

        ApiResponse<DSMileStoneResponse> response = mileStoneProgramService.programSummary(merchant);

        assertEquals("400", response.getErrorCode());
        assertEquals("Bureau Response Or DS Response Not Found", response.getMessage());
    }


    @Test
    public void testCreateSession_InProgress() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);


        DSMileStoneResponse dsMileStoneResponse = new DSMileStoneResponse();

        MileStoneEntity entity = new MileStoneEntity();
        entity.setMerchantId(merchant.getId());
        entity.setSessionStatus("IN_PROGRESS");

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), eq("IN_PROGRESS")))
                .thenReturn(entity);

        ApiResponse<?> response = mileStoneProgramService.createSession(merchant, dsMileStoneResponse);

        assertEquals("400", response.getErrorCode());
        assertEquals("milestone journey is already in-progress for merchant id", response.getMessage());
    }

    @Test
    public void testCreateSession_EligibilityTrue() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);


        DSMileStoneResponse dsMileStoneResponse = new DSMileStoneResponse();
        dsMileStoneResponse.setTarget_duration_days(30);

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), eq("IN_PROGRESS")))
                .thenReturn(null);

        MileStoneEligibilityResponseDto eligibilityResponse = new MileStoneEligibilityResponseDto();
        eligibilityResponse.setMilStoneEligibility(true);
        eligibilityResponse.setProgramType(RTEProgramType.SLIDER.name());

        when(easyLoanUtil.percentScaleUp(eq(merchant.getId()), anyInt())).thenReturn(true);
        when(mileStoneHelperServicev3.calculateEligibility(eq(merchant), anyBoolean()))
                .thenReturn(eligibilityResponse);
        when(mileStoneHelperService.createMileStoneSession(eq(merchant.getId()), eq(dsMileStoneResponse), eq(30)))
                .thenReturn(new MileStoneEntity());
        when(lendingCache.get(RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId())).thenReturn(null);
        when(lendingCache.get(RTEConstants.RTE_MILESTONE_DASHBOARD + merchant.getId())).thenReturn(null);

        ApiResponse<?> response = mileStoneProgramService.createSession(merchant, dsMileStoneResponse);

        assertEquals("200", response.getErrorCode());
        assertEquals("OK", response.getMessage());
    }

    @Test
    public void testCreateSession_EligibilityFalse() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);


        DSMileStoneResponse dsMileStoneResponse = new DSMileStoneResponse();

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), eq("IN_PROGRESS")))
                .thenReturn(null);

        MileStoneEligibilityResponseDto eligibilityResponse = new MileStoneEligibilityResponseDto();
        eligibilityResponse.setMilStoneEligibility(false);

        when(easyLoanUtil.percentScaleUp(eq(merchant.getId()), anyInt())).thenReturn(true);
        when(mileStoneHelperServicev3.calculateEligibility(eq(merchant), anyBoolean()))
                .thenReturn(eligibilityResponse);

        ApiResponse<?> response = mileStoneProgramService.createSession(merchant, dsMileStoneResponse);

        assertEquals("400", response.getErrorCode());
        assertEquals("eligibility is false", response.getMessage());
    }

    @Test
    public void testCreateSession_Exception() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);


        DSMileStoneResponse dsMileStoneResponse = new DSMileStoneResponse();

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), eq("IN_PROGRESS")))
                .thenReturn(null);

        when(easyLoanUtil.percentScaleUp(eq(merchant.getId()), anyInt())).thenThrow(new RuntimeException("Test Exception"));

        ApiResponse<?> response = mileStoneProgramService.createSession(merchant, dsMileStoneResponse);

        assertEquals("400", response.getErrorCode());
        assertEquals("eligibility is false", response.getMessage());
    }

    @Test
    public void testDashboardDetails_EntityNotFound() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);


        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(eq(merchant.getId()))).thenReturn(null);
        ApiResponse<MileStoneDashboardDetails> response = mileStoneProgramService.dashboardDetails(merchant);

        assertEquals("400", response.getErrorCode());
        assertEquals("Entity Not Found", response.getMessage());
    }

    @Test
    public void testDashboardDetails_AchievementResponseNull() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);


        MileStoneEntity entity = new MileStoneEntity();
        entity.setMerchantId(merchant.getId());

        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(eq(merchant.getId()))).thenReturn(entity);
        when(lendingCache.get(eq(RTEConstants.RTE_MILESTONE_DASHBOARD + merchant.getId()))).thenReturn(null);
        when(mileStoneHelperService.getAchievementData(eq(dsHandler), eq(entity))).thenReturn(null);

        ApiResponse<MileStoneDashboardDetails> response = mileStoneProgramService.dashboardDetails(merchant);

        assertEquals("400", response.getErrorCode());
        assertEquals("Achievement response is null", response.getMessage());
    }

    @Test
    public void testDashboardDetails_AchievementEmpty() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        MileStoneEntity entity = new MileStoneEntity();
        entity.setMerchantId(merchant.getId());
        entity.setProgramDuration(30);
        entity.setSessionStatus("IN_PROGRESS");
        entity.setProgramStartDate(new Date());
        entity.setCreatedAt(new Date());

        DSMileStoneAchievementResponse achievementResponse = new DSMileStoneAchievementResponse();
        achievementResponse.setAchievement(new ArrayList<>());

        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(eq(merchant.getId()))).thenReturn(entity);
        when(lendingCache.get(eq(RTEConstants.RTE_MILESTONE_DASHBOARD + merchant.getId()))).thenReturn(null);
        when(mileStoneHelperService.getAchievementData(eq(dsHandler), eq(entity))).thenReturn(achievementResponse);
        doNothing().when(dsHandler).pushMilestoneAchievementData(eq(merchant.getId()), eq(achievementResponse));

        DSMileStoneResponse mileStoneResponse = new DSMileStoneResponse();
        Target t1 = new Target();
        t1.setMilestone_no(1);
        t1.setActive_days(4);
        t1.setUnq_payer(2);

        ArrayList<Target> targets = new ArrayList<>();
        targets.add(t1);
        mileStoneResponse.setTarget(targets);
        when(mileStoneHelperService.fetchTarget(eq(entity))).thenReturn(mileStoneResponse);

        ApiResponse<MileStoneDashboardDetails> response = mileStoneProgramService.dashboardDetails(merchant);

        verify(lendingCache, times(1)).add(any(AddCacheDto.class), eq(TimeUnit.MINUTES));
        Assertions.assertNotNull(response);
        assertTrue(response.isSuccess());
    }

    @Test
    public void testDashboardDetails_AchievementPresent() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        MileStoneEntity entity = new MileStoneEntity();
        entity.setMerchantId(merchant.getId());
        entity.setProgramDuration(60);
        entity.setSessionStatus("IN_PROGRESS");
        entity.setProgramStartDate(new Date());
        entity.setCreatedAt(new Date());
        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(eq(merchant.getId()))).thenReturn(entity);
        when(lendingCache.get(eq(RTEConstants.RTE_MILESTONE_DASHBOARD + merchant.getId()))).thenReturn(null);

        DSMileStoneAchievementResponse achievementResponse = new DSMileStoneAchievementResponse();
        DSMileStoneAchievementResponse.Achievement achievement = new DSMileStoneAchievementResponse.Achievement();
        achievement.setMilestone_no(1);
        achievement.setActive_days(4);
        achievement.setUnq_payer(2);
        achievement.setMilestone_start_time(new Date());
        achievement.setMilestone_end_time(new Date());
        achievement.setActive_days_daily(new ArrayList<>());
        achievement.setUnq_payer_daily(new ArrayList<>());
        ArrayList<DSMileStoneAchievementResponse.Achievement> achievementList = new ArrayList<>();
        achievementList.add(achievement);
        achievementResponse.setAchievement(achievementList);

        DSMileStoneAchievementResponse.Total total = new DSMileStoneAchievementResponse.Total();
        total.setActive_days(4);
        total.setUnq_payer(2);
        achievementResponse.setTotal(total);
        when(mileStoneHelperService.getAchievementData(eq(dsHandler), eq(entity))).thenReturn(achievementResponse);

        DSMileStoneResponse mileStoneResponse = new DSMileStoneResponse();
        mileStoneResponse.setProgram_type(RTEProgramType.NEW_MERCHANT.name());
        Target t1 = new Target();
        t1.setMilestone_no(1);
        t1.setActive_days(4);
        t1.setUnq_payer(2);

        Target t2 = new Target();
        t2.setMilestone_no(2);
        t2.setActive_days(4);
        t2.setUnq_payer(3);

        ArrayList<Target> targets = new ArrayList<>();
        targets.add(t1);
        targets.add(t2);
        mileStoneResponse.setTarget(targets);

        DSMileStoneResponse.total_target total_target = new DSMileStoneResponse.total_target();
        total_target.setActive_days(4);
        total_target.setUnq_payer(2);
        mileStoneResponse.setTotal_target(total_target);
        when(mileStoneHelperService.fetchTarget(eq(entity))).thenReturn(mileStoneResponse);

        ApiResponse<MileStoneDashboardDetails> response = mileStoneProgramService.dashboardDetails(merchant);
        verify(lendingCache, times(1)).add(any(AddCacheDto.class), eq(TimeUnit.MINUTES));
        assertEquals(achievementResponse.getTotal().getActive_days(), response.getData().getAchievementActiveDays());
        Assertions.assertNotNull(response);
    }

    @Test
    public void testMilestoneOffer_EntityNotFound() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        MileStoneOfferRequest request = new MileStoneOfferRequest();

        when(lendingCache.get(RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId())).thenReturn(null);
        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(eq(merchant.getId()))).thenReturn(null);

        ApiResponse<?> response = mileStoneProgramService.milestoneOffer(merchant, request);

        assertEquals("400", response.getErrorCode());
        assertEquals("entity not found", response.getMessage());
    }

    @Test
    public void testMilestoneOffer_EntityNotUpdatedInDb() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        MileStoneOfferRequest request = new MileStoneOfferRequest();

        MileStoneEntity entity = new MileStoneEntity();
        entity.setMilestoneOffer(true);

        when(lendingCache.get(RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId())).thenReturn(null);
        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(eq(merchant.getId()))).thenReturn(entity);

        ApiResponse<?> response = mileStoneProgramService.milestoneOffer(merchant, request);

        assertEquals("400", response.getErrorCode());
        assertEquals("entity not updated in db", response.getMessage());
    }

    @Test
    public void testMilestoneOffer_EntityUpdatedInDb() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        MileStoneOfferRequest request = new MileStoneOfferRequest();

        MileStoneEntity entity = new MileStoneEntity();
        entity.setMilestoneOffer(false);

        when(lendingCache.get(RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId())).thenReturn(null);
        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(eq(merchant.getId()))).thenReturn(entity);
        when(mileStoneHelperService.updateEntity(eq(request), eq(entity))).thenReturn(true);

        ApiResponse<?> response = mileStoneProgramService.milestoneOffer(merchant, request);

        assertEquals("200", response.getErrorCode());
        assertEquals("entity  updated in db", response.getMessage());
    }


    @Test
    public void testMilestoneOffer_CacheDeletion() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        MileStoneOfferRequest request = new MileStoneOfferRequest();

        MileStoneEntity entity = new MileStoneEntity();
        entity.setMilestoneOffer(false);

        String mileStoneProgramCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();

        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(eq(merchant.getId()))).thenReturn(entity);
        when(mileStoneHelperService.updateEntity(eq(request), eq(entity))).thenReturn(true);
        when(lendingCache.get(eq(mileStoneProgramCacheKey))).thenReturn(new Object());

        ApiResponse<?> response = mileStoneProgramService.milestoneOffer(merchant, request);

        verify(lendingCache, times(1)).delete(eq(mileStoneProgramCacheKey));

        assertEquals("200", response.getErrorCode());
        assertEquals("entity  updated in db", response.getMessage());
    }

    @Test
    public void testProgramDetails_CacheHitWithEligibilityData() throws Exception {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        rteProgramDetailsDto.setRouteToEligibilityData(new MileStoneEligibilityResponseDto());

        String mileStoneCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
        when(lendingCache.get(eq(mileStoneCacheKey))).thenReturn("{\"routeToEligibilityData\": {}}");
        when(objectMapper.readValue(anyString(), eq(RTEProgramDetailsDto.class))).thenReturn(rteProgramDetailsDto);

        ApiResponse<Object> response = mileStoneProgramService.programDetails(merchant);

        verify(lendingCache, times(1)).get(eq(mileStoneCacheKey));
        verify(objectMapper, times(1)).readValue(anyString(), eq(RTEProgramDetailsDto.class));
        assertTrue(response.isSuccess());
        assertEquals(rteProgramDetailsDto, response.getData());
    }

    @Test
    public void testProgramDetails_CacheHitWithoutEligibilityData() throws Exception {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        MileStoneEligibilityResponseDto responseDto = new MileStoneEligibilityResponseDto();
        responseDto.setMilStoneEligibility(true);

        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        rteProgramDetailsDto.setRouteToEligibilityData(null);

        String mileStoneCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
        when(merchantSummaryHandler.getMerchantSummary(merchant.getId())).thenReturn(new MerchantResponseDTO());
        when(lendingCache.get(eq(mileStoneCacheKey))).thenReturn("{\"routeToEligibilityData\": null}");
        when(objectMapper.readValue(anyString(), eq(RTEProgramDetailsDto.class))).thenReturn(rteProgramDetailsDto);
        when(easyLoanUtil.percentScaleUp(eq(merchant.getId()), anyInt())).thenReturn(true);
        when(mileStoneHelperServicev3.calculateEligibility(eq(merchant), anyBoolean())).thenReturn(responseDto);

        ApiResponse<Object> response = mileStoneProgramService.programDetails(merchant);

        verify(lendingCache, times(1)).get(eq(mileStoneCacheKey));
        verify(objectMapper, times(1)).readValue(anyString(), eq(RTEProgramDetailsDto.class));
        verify(mileStoneHelperServicev3, times(1)).calculateEligibility(eq(merchant), anyBoolean());
        Assertions.assertNotNull(response);
    }

    @Test
    public void testProgramDetails_NoCacheHitAndNotEligible() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        MileStoneEligibilityResponseDto responseDto = new MileStoneEligibilityResponseDto();
        responseDto.setMilStoneEligibility(false);

        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        rteProgramDetailsDto.setRouteToEligibilityData(responseDto);

        String mileStoneCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
        when(lendingCache.get(eq(mileStoneCacheKey))).thenReturn(null);
        when(easyLoanUtil.percentScaleUp(eq(merchant.getId()), anyInt())).thenReturn(true);
        when(mileStoneHelperServicev3.calculateEligibility(eq(merchant), anyBoolean())).thenReturn(responseDto);

        ApiResponse<Object> response = mileStoneProgramService.programDetails(merchant);

        verify(lendingCache, times(1)).get(eq(mileStoneCacheKey));
        verify(mileStoneHelperServicev3, times(1)).calculateEligibility(eq(merchant), anyBoolean());
        assertTrue(response.isSuccess());
        assertFalse(rteProgramDetailsDto.getRouteToEligibilityData().getMilStoneEligibility());
    }

    @Test
    public void testProgramDetails_NoCacheHitAndEligible() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        boolean isRtev3Enabled = true;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);

        MileStoneEligibilityResponseDto responseDto = new MileStoneEligibilityResponseDto();
        responseDto.setMilStoneEligibility(true);

        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        rteProgramDetailsDto.setRouteToEligibilityData(responseDto);

        MileStoneEntity entity = new MileStoneEntity();
        entity.setSessionStatus("IN_PROGRESS");

        String mileStoneCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
        when(merchantSummaryHandler.getMerchantSummary(merchant.getId())).thenReturn(new MerchantResponseDTO());
        when(lendingCache.get(eq(mileStoneCacheKey))).thenReturn(null);
        when(easyLoanUtil.percentScaleUp(eq(merchant.getId()), anyInt())).thenReturn(true);
        when(mileStoneHelperServicev3.calculateEligibility(eq(merchant), anyBoolean())).thenReturn(responseDto);
        when(kycHandler.getPanStatus(eq(merchant.getId()))).thenReturn(KycStatus.APPROVED);
        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), eq("IN_PROGRESS"))).thenReturn(entity);
        ApiResponse<Object> response = mileStoneProgramService.programDetails(merchant);

        verify(lendingCache, times(1)).get(eq(mileStoneCacheKey));
        verify(mileStoneHelperServicev3, times(1)).calculateEligibility(eq(merchant), anyBoolean());
        verify(kycHandler, times(1)).getPanStatus(eq(merchant.getId()));
        verify(mileStoneDao, times(1)).findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), eq("IN_PROGRESS"));
        assertTrue(response.isSuccess());
        Assertions.assertNotNull(response);
    }

    @Test
    public void testCheckEligibility_MerchantSummaryNotFound() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        when(merchantSummaryHandler.getMerchantSummary(eq(merchant.getId()))).thenReturn(null);

        assertThrows(MerchantSummaryExceptionHandler.class, () -> {
            mileStoneProgramService.checkEligibility(rteProgramDetailsDto, merchant);
        });

        verify(merchantSummaryHandler, times(1)).getMerchantSummary(eq(merchant.getId()));
    }

    @Test
    public void testCheckEligibility_NoExperianRecord() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        MerchantResponseDTO merchantResponseDTO = new MerchantResponseDTO();

        when(merchantSummaryHandler.getMerchantSummary(eq(merchant.getId()))).thenReturn(merchantResponseDTO);
        when(experianDao.getByMerchantId(eq(merchant.getId()))).thenReturn(null);

        mileStoneProgramService.checkEligibility(rteProgramDetailsDto, merchant);

        verify(merchantSummaryHandler, times(1)).getMerchantSummary(eq(merchant.getId()));
        verify(experianDao, times(1)).getByMerchantId(eq(merchant.getId()));
    }

    @Test
    public void testCheckEligibility_WithPreApprovedTag() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        MerchantResponseDTO merchantResponseDTO = new MerchantResponseDTO();
        Experian experian = new Experian();

        when(merchantSummaryHandler.getMerchantSummary(eq(merchant.getId()))).thenReturn(merchantResponseDTO);
        when(experianDao.getByMerchantId(eq(merchant.getId()))).thenReturn(experian);
        when(loanDashboardService.getPreApprovedTag(eq(merchant.getId()))).thenReturn("PREAPPROVED_TAG");

        mileStoneProgramService.checkEligibility(rteProgramDetailsDto, merchant);

        verify(funnelService, times(1)).submitEvent(eq(merchant.getId()), isNull(), isNull(), eq(FunnelEnums.StageId.LOAN_DASHBOARD), eq(FunnelEnums.StageEvent.PREAPPROVED), eq("PREAPPROVED_TAG"));
    }

    @Test
    public void testCheckEligibility_GlobalLimitFound() throws BureauCallMaskedApiException {
        boolean isRtev3Enabled = true;
        int eligibilityRefreshWindow = 1;
        ReflectionTestUtils.setField(mileStoneProgramService, "isRtev3Enabled", isRtev3Enabled);
        ReflectionTestUtils.setField(mileStoneProgramService, "eligibilityRefreshWindow", eligibilityRefreshWindow);

        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        rteProgramDetailsDto.setLoanAmount(20000D);
        MerchantResponseDTO merchantResponseDTO = new MerchantResponseDTO();
        Experian experian = new Experian();
        experian.setPincode(2001017);

        EligibleLoan eligibleLoan = new EligibleLoan();
        eligibleLoan.setCreatedAt(new Date());

        Eligibility eligibility = new Eligibility();
        eligibility.setLoanAmount(20000D);

        GlobalLimitResponse globalLimitResponse = new GlobalLimitResponse();
        globalLimitResponse.setData(new GlobalLimitResponse.Data());
        globalLimitResponse.getData().setGlobalLimit(100000.0);
        globalLimitResponse.getData().setDerog(true);

        when(merchantSummaryHandler.getMerchantSummary(eq(merchant.getId()))).thenReturn(merchantResponseDTO);
        when(experianDao.getByMerchantId(eq(merchant.getId()))).thenReturn(experian);
        when(eligibleLoanDao.findTop1ByMerchantIdAndLoanTypeNotTopup(eq(merchant.getId()))).thenReturn(null);
        when(dateTimeUtil.getCurrentDate()).thenReturn(new Date());
        when(loanDashboardService.isClubV2Member(merchant.getId())).thenReturn(true);
        when(easyLoanUtil.percentScaleUp(any(), anyInt())).thenReturn(true);
        when(easyLoanUtil.isDummyMerchant(merchant.getId())).thenReturn(false);
        when(apiGatewayService.getGlobalLimit(eq(merchant.getId()), anyBoolean(), eq(EligibilityRequestSource.RTE))).thenReturn(globalLimitResponse);
        when(loanDashboardService.recomputeEligibleLoan(globalLimitResponse, null, merchant.getId())).thenReturn(eligibleLoan);
        when(loanDashboardService.createEligibility(any(), any())).thenReturn(eligibility);

        mileStoneProgramService.checkEligibility(rteProgramDetailsDto, merchant);

        verify(apiGatewayService, times(1)).getGlobalLimit(eq(merchant.getId()), anyBoolean(), eq(EligibilityRequestSource.RTE));
        assertEquals(20000, rteProgramDetailsDto.getLoanAmount());
    }

}
