package com.bharatpe.lending.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.dao.LendingPincodesDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.LendingPincodes;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.MileStoneDao;
import com.bharatpe.lending.dto.DSMileStoneResponse;
import com.bharatpe.lending.dto.MileStoneEligibilityResponseDto;
import com.bharatpe.lending.dto.RTEProgramDetailsDto;
import com.bharatpe.lending.dto.Target;
import com.bharatpe.lending.entity.MileStoneEntity;
import com.bharatpe.lending.enums.RTEProgramType;
import com.bharatpe.lending.enums.RTESessionStatus;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.BureauResponseDTO;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@TestConfiguration
public class MileStoneHelperServicev3Test {
    @InjectMocks
    private MileStoneHelperServicev3 mileStoneHelperServicev3;

    @Mock
    private MileStoneDao mileStoneDao;
    @Mock
    private DsHandler dsHandler;
    @Mock
    private LoanUtil loanUtil;
    @Mock
    private LendingCache lendingCache;
    @Mock
    private LendingRiskVariablesDao lendingRiskVariablesDao;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ExperianDao experianDao;
    @Mock
    private LendingPincodesDao lendingPincodesDao;
    @Mock
    private KycHandler kycHandler;
    @Mock
    private MileStoneHelperService mileStoneHelperService;
    @Mock
    private FunnelService funnelService;
    @Mock
    private EasyLoanUtil easyLoanUtil;
    @Mock
    private CleverTapEventService cleverTapEventService;

    @Value("${milestone.splashScreen.visible:true}")
    private Boolean milestoneSplashVisible;
    @Value("${milestone.widget.visible:true}")
    private Boolean milestoneWidgetVisible;
    @Value("${milestone.easyLoan.visible:true}")
    private Boolean milestoneEasyLoanVisible;
    @Value("${rte.program.visible:true}")
    private Boolean showRTEProgram = true;

    @BeforeEach
    public void setUp() {
        milestoneSplashVisible = true;
        milestoneWidgetVisible = true;
        milestoneEasyLoanVisible = true;
        showRTEProgram = true;
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCalculateEligibility_RTEProgramDisabled() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);
        MileStoneEntity entity = new MileStoneEntity();
        entity.setSessionStatus("COMPLETED");
        showRTEProgram = false;

        doNothing().when(loanUtil).rteEligibleMerchant();
        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), anyString())).thenReturn(entity);
        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId())).thenReturn(entity);
        MileStoneEligibilityResponseDto responseDto = mileStoneHelperServicev3.calculateEligibility(merchant, false);

        assertFalse(responseDto.getMilStoneEligibility());
        assertFalse(responseDto.getEnrollState());
    }

    @Test
    public void testCalculateEligibility_FreshMerchantCacheHit() throws IOException {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        MileStoneEligibilityResponseDto mileStoneEligibilityResponseDto = new MileStoneEligibilityResponseDto();
        mileStoneEligibilityResponseDto.setMilStoneEligibility(true);

        when(loanUtil.rteEligibleMerchant()).thenReturn(new ArrayList<>());
        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), anyString())).thenReturn(null);
        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId())).thenReturn(null);
        when(lendingCache.get(anyString())).thenReturn(mileStoneEligibilityResponseDto);
        when(objectMapper.readValue(anyString(), eq(MileStoneEligibilityResponseDto.class))).thenReturn(mileStoneEligibilityResponseDto);

        MileStoneEligibilityResponseDto responseDto = mileStoneHelperServicev3.calculateEligibility(merchant, false);

        assertNotNull(responseDto);
        assertEquals(true, responseDto.getMilStoneEligibility());
        verify(lendingCache).get(anyString());
    }

    @Test
    public void testCalculateEligibility_FreshSliderMerchant() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        Experian experian = new Experian();
        experian.setPincode(201017);

        LendingPincodes lendingPincodes = new LendingPincodes();
        lendingPincodes.setColor(PincodeColor.GREEN);

        BureauResponseDTO bureauResponseDTO = new BureauResponseDTO();
        bureauResponseDTO.setIsNTC(true);
        bureauResponseDTO.setVariables(new BureauResponseDTO.BureauVariables());

        DSMileStoneResponse dsMileStoneResponse = new DSMileStoneResponse();
        Target t1 = new Target();
        t1.setMilestone_no(1);
        t1.setActive_days(4);
        t1.setUnq_payer(2);

        ArrayList<Target> targets = new ArrayList<>();
        targets.add(t1);
        dsMileStoneResponse.setProgram_type(RTEProgramType.SLIDER.name());
        dsMileStoneResponse.setTarget(targets);
        dsMileStoneResponse.setMax_limit(100000);

        MileStoneEligibilityResponseDto mileStoneEligibilityResponseDto = new MileStoneEligibilityResponseDto();
        mileStoneEligibilityResponseDto.setMilStoneEligibility(true);
        mileStoneEligibilityResponseDto.setEnrollState(true);

        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        rteProgramDetailsDto.setRouteToEligibilityData(mileStoneEligibilityResponseDto);

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), anyString())).thenReturn(null);
        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId())).thenReturn(null);
        when(loanUtil.rteEligibleMerchant()).thenReturn(new ArrayList<>());
        when(lendingCache.get(anyString())).thenReturn(null);
        when(experianDao.getByMerchantId(eq(merchant.getId()))).thenReturn(experian);
        when(kycHandler.getPanNumber(eq(merchant.getId()))).thenReturn("GNFPP1325D");
        when(lendingPincodesDao.findByPincode(experian.getPincode())).thenReturn(lendingPincodes);
        when(mileStoneHelperService.calculateBureauScore(anyString(), eq(merchant))).thenReturn(bureauResponseDTO);
        when(lendingCache.get(RTEConstants.RTE_V3_AMOUNT + merchant.getId())).thenReturn("25k");
        when(dsHandler.fetchMileStoneDatav3(any(), any(), any(), anyString(), anyString())).thenReturn(dsMileStoneResponse);
        when(mileStoneHelperService.setNTCProgramEligibleData()).thenReturn(null);
        when(mileStoneHelperService.setNTCProgramActiveData(any(), anyString())).thenReturn(null);

        MileStoneEligibilityResponseDto responseDto = mileStoneHelperServicev3.calculateEligibility(merchant, false);

        assertTrue(responseDto.getMilStoneEligibility());
        verify(funnelService, times(1)).submitEvent(eq(merchant.getId()), any(), any(), any(), any(), any());
        verify(cleverTapEventService, times(1)).sendClevertapEvent(any(), any(), any());
    }

    @Test
    public void testCalculateEligibility_ActiveSliderMerchantNoAchievements() throws IOException {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        DSMileStoneResponse dsMileStoneResponse = new DSMileStoneResponse();
        dsMileStoneResponse.setProgram_type(RTEProgramType.SLIDER.name());

        MileStoneEntity entity = new MileStoneEntity();
        entity.setMilestoneOffer(false);
        entity.setSessionStatus(RTESessionStatus.IN_PROGRESS.name());
        entity.setResponse(dsMileStoneResponse.toString());

        Experian experian = new Experian();
        experian.setPincode(201017);

        MileStoneEligibilityResponseDto mileStoneEligibilityResponseDto = new MileStoneEligibilityResponseDto();
        mileStoneEligibilityResponseDto.setMilStoneEligibility(true);
        mileStoneEligibilityResponseDto.setEnrollState(true);

        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        rteProgramDetailsDto.setRouteToEligibilityData(mileStoneEligibilityResponseDto);

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), anyString())).thenReturn(null);
        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId())).thenReturn(entity);
        when(loanUtil.rteEligibleMerchant()).thenReturn(new ArrayList<>());
        when(lendingCache.get(RTEConstants.RTE_MILESTONE_OFFER_KEY + merchant.getId())).thenReturn(null);
        when(experianDao.getByMerchantId(eq(merchant.getId()))).thenReturn(experian);
        when(kycHandler.getPanNumber(eq(merchant.getId()))).thenReturn("GNFPP1325D");
        when(mileStoneHelperService.fetchTarget(entity)).thenReturn(dsMileStoneResponse);
        when(dsHandler.fetchMilestoneAchievements(eq(merchant.getId()), eq(anyString()))).thenReturn(null);
        when(lendingCache.get(RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId())).thenReturn(new Object());
        when(objectMapper.readValue(anyString(), eq(RTEProgramDetailsDto.class))).thenReturn(rteProgramDetailsDto);

        MileStoneEligibilityResponseDto responseDto = mileStoneHelperServicev3.calculateEligibility(merchant, false);

        assertTrue(responseDto.getMilStoneEligibility());
        assertTrue(responseDto.getEnrollState());
        verify(funnelService, times(1)).submitEvent(eq(merchant.getId()), any(), any(), any(), any(), any());
        verify(cleverTapEventService, times(1)).sendClevertapEvent(any(), any(), any());
    }


    @Test
    public void testCalculateEligibility_FreshMerchantDSErrorResponse() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        Experian experian = new Experian();
        experian.setPincode(201017);

        LendingPincodes lendingPincodes = new LendingPincodes();
        lendingPincodes.setColor(PincodeColor.GREEN);

        BureauResponseDTO bureauResponseDTO = new BureauResponseDTO();
        bureauResponseDTO.setIsNTC(true);
        bureauResponseDTO.setVariables(new BureauResponseDTO.BureauVariables());

        MileStoneEligibilityResponseDto mileStoneEligibilityResponseDto = new MileStoneEligibilityResponseDto();
        mileStoneEligibilityResponseDto.setMilStoneEligibility(true);
        mileStoneEligibilityResponseDto.setEnrollState(true);

        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        rteProgramDetailsDto.setRouteToEligibilityData(mileStoneEligibilityResponseDto);

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), anyString())).thenReturn(null);
        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId())).thenReturn(null);
        when(loanUtil.rteEligibleMerchant()).thenReturn(new ArrayList<>());
        when(lendingCache.get(anyString())).thenReturn(null);
        when(experianDao.getByMerchantId(eq(merchant.getId()))).thenReturn(new Experian());
        when(kycHandler.getPanNumber(eq(merchant.getId()))).thenReturn("GNFPP1325D");
        when(lendingPincodesDao.findByPincode(experian.getPincode())).thenReturn(lendingPincodes);
        when(mileStoneHelperService.calculateBureauScore(anyString(), eq(merchant))).thenReturn(bureauResponseDTO);
        when(lendingCache.get(RTEConstants.RTE_V3_AMOUNT + merchant.getId())).thenReturn("25k");
        when(dsHandler.fetchMileStoneDatav3(any(), any(), any(), anyString(), anyString())).thenReturn(null);
        when(mileStoneHelperService.setNTCProgramEligibleData()).thenReturn(null);
        when(mileStoneHelperService.setNTCProgramActiveData(any(), anyString())).thenReturn(null);

        MileStoneEligibilityResponseDto responseDto = mileStoneHelperServicev3.calculateEligibility(merchant, false);

        assertFalse(responseDto.getMilStoneEligibility());
        assertEquals("error in target ds api", responseDto.getDsErrorMessage());
        verify(funnelService, times(1)).submitEvent(eq(merchant.getId()), any(), any(), any(), any(), any());
        verify(cleverTapEventService, times(1)).sendClevertapEvent(any(), any(), any());
    }

    @Test
    public void testCalculateEligibility_FreshMerchantProgramTypeNEW_MERCHANT() {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        Experian experian = new Experian();
        experian.setPincode(201017);

        LendingPincodes lendingPincodes = new LendingPincodes();
        lendingPincodes.setColor(PincodeColor.GREEN);

        BureauResponseDTO bureauResponseDTO = new BureauResponseDTO();
        bureauResponseDTO.setIsNTC(true);
        bureauResponseDTO.setVariables(new BureauResponseDTO.BureauVariables());

        DSMileStoneResponse dsMileStoneResponse = new DSMileStoneResponse();
        Target t1 = new Target();
        t1.setMilestone_no(1);
        t1.setActive_days(4);
        t1.setUnq_payer(2);

        ArrayList<Target> targets = new ArrayList<>();
        targets.add(t1);
        dsMileStoneResponse.setProgram_type(RTEProgramType.NEW_MERCHANT.name());
        dsMileStoneResponse.setTarget(targets);
        dsMileStoneResponse.setMax_limit(100000);

        LendingRiskVariables lendingRiskVariables = new LendingRiskVariables();
        lendingRiskVariables.setExperianRejection("LIMIT BLOCKED: Offer set 0");

        MileStoneEligibilityResponseDto mileStoneEligibilityResponseDto = new MileStoneEligibilityResponseDto();
        mileStoneEligibilityResponseDto.setMilStoneEligibility(true);
        mileStoneEligibilityResponseDto.setEnrollState(true);

        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        rteProgramDetailsDto.setRouteToEligibilityData(mileStoneEligibilityResponseDto);

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), anyString())).thenReturn(null);
        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId())).thenReturn(null);
        when(loanUtil.rteEligibleMerchant()).thenReturn(new ArrayList<>());
        when(lendingCache.get(anyString())).thenReturn(null);
        when(experianDao.getByMerchantId(eq(merchant.getId()))).thenReturn(experian);
        when(kycHandler.getPanNumber(eq(merchant.getId()))).thenReturn("GNFPP1325D");
        when(lendingPincodesDao.findByPincode(experian.getPincode())).thenReturn(lendingPincodes);
        when(mileStoneHelperService.calculateBureauScore(anyString(), eq(merchant))).thenReturn(bureauResponseDTO);
        when(lendingCache.get(RTEConstants.RTE_V3_AMOUNT + merchant.getId())).thenReturn("25k");
        when(dsHandler.fetchMileStoneDatav3(any(), any(), any(), anyString(), anyString())).thenReturn(dsMileStoneResponse);
        when(lendingRiskVariablesDao.findByMerchantId(eq(merchant.getId()))).thenReturn(lendingRiskVariables);
        when(mileStoneHelperService.setNTCProgramEligibleData()).thenReturn(null);
        when(mileStoneHelperService.setNTCProgramActiveData(any(), anyString())).thenReturn(null);

        MileStoneEligibilityResponseDto responseDto = mileStoneHelperServicev3.calculateEligibility(merchant, false);

        assertTrue(responseDto.getMilStoneEligibility());
        verify(funnelService, times(1)).submitEvent(eq(merchant.getId()), any(), any(), any(), any(), any());
        verify(cleverTapEventService, times(1)).sendClevertapEvent(any(), any(), any());
    }


    @Test
    public void testCalculateEligibility_ActiveSessionNEW_MERCHANT() throws IOException {
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(20000760L);

        DSMileStoneResponse dsMileStoneResponse = new DSMileStoneResponse();
        Target t1 = new Target();
        t1.setMilestone_no(1);
        t1.setActive_days(4);
        t1.setUnq_payer(2);

        ArrayList<Target> targets = new ArrayList<>();
        targets.add(t1);
        dsMileStoneResponse.setProgram_type(RTEProgramType.NEW_MERCHANT.name());
        dsMileStoneResponse.setTarget(targets);
        dsMileStoneResponse.setMax_limit(100000);

        MileStoneEntity entity = new MileStoneEntity();
        entity.setMilestoneOffer(false);
        entity.setSessionStatus(RTESessionStatus.IN_PROGRESS.name());
        entity.setResponse(dsMileStoneResponse.toString());

        Experian experian = new Experian();
        experian.setPincode(201017);

        LendingPincodes lendingPincodes = new LendingPincodes();
        lendingPincodes.setColor(PincodeColor.GREEN);

        BureauResponseDTO bureauResponseDTO = new BureauResponseDTO();
        bureauResponseDTO.setIsNTC(true);
        bureauResponseDTO.setVariables(new BureauResponseDTO.BureauVariables());

        LendingRiskVariables lendingRiskVariables = new LendingRiskVariables();
        lendingRiskVariables.setExperianRejection("LIMIT BLOCKED: Offer set 0");

        MileStoneEligibilityResponseDto mileStoneEligibilityResponseDto = new MileStoneEligibilityResponseDto();
        mileStoneEligibilityResponseDto.setMilStoneEligibility(true);
        mileStoneEligibilityResponseDto.setEnrollState(true);

        RTEProgramDetailsDto rteProgramDetailsDto = new RTEProgramDetailsDto();
        rteProgramDetailsDto.setRouteToEligibilityData(mileStoneEligibilityResponseDto);

        when(mileStoneDao.findTop1ByMerchantIdAndSessionStatus(eq(merchant.getId()), anyString())).thenReturn(null);
        when(mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId())).thenReturn(null);
        when(loanUtil.rteEligibleMerchant()).thenReturn(new ArrayList<>());
        when(lendingCache.get(RTEConstants.RTE_MILESTONE_OFFER_KEY + merchant.getId())).thenReturn(null);
        when(experianDao.getByMerchantId(eq(merchant.getId()))).thenReturn(experian);
        when(kycHandler.getPanNumber(eq(merchant.getId()))).thenReturn("GNFPP1325D");
        when(lendingPincodesDao.findByPincode(experian.getPincode())).thenReturn(lendingPincodes);
        when(mileStoneHelperService.calculateBureauScore(anyString(), eq(merchant))).thenReturn(bureauResponseDTO);
        when(lendingCache.get(RTEConstants.RTE_V3_AMOUNT + merchant.getId())).thenReturn("25k");
        when(dsHandler.fetchMileStoneDatav3(any(), any(), any(), anyString(), anyString())).thenReturn(dsMileStoneResponse);
        when(lendingRiskVariablesDao.findByMerchantId(eq(merchant.getId()))).thenReturn(lendingRiskVariables);
        when(mileStoneHelperService.fetchTarget(entity)).thenReturn(dsMileStoneResponse);
        when(dsHandler.fetchMilestoneAchievements(eq(merchant.getId()), eq(anyString()))).thenReturn(null);
        when(lendingCache.get(RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId())).thenReturn(new Object());
        when(objectMapper.readValue(anyString(), eq(RTEProgramDetailsDto.class))).thenReturn(rteProgramDetailsDto);

        MileStoneEligibilityResponseDto responseDto = mileStoneHelperServicev3.calculateEligibility(merchant, false);

        assertTrue(responseDto.getMilStoneEligibility());
        verify(funnelService, times(1)).submitEvent(eq(merchant.getId()), any(), any(), any(), any(), any());
        verify(cleverTapEventService, times(1)).sendClevertapEvent(any(), any(), any());
    }

}
