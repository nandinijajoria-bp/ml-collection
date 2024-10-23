package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.smfg.SmfgRpsResponse;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@TestConfiguration
public class SmfgRpsServiceTest {
    @Mock
    private ILenderAPIGateway lenderAPIGateway;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Mock
    private LendingApplicationDao lendingApplicationDao;

    @Mock
    private SmfgConfig smfgConfig;

    @InjectMocks
    private SmfgRpsService smfgRepaymentService;

    @Test
    public void invokeRpsGenerate_Success() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(true);
        SmfgRpsResponse smfgRpsResponse = new SmfgRpsResponse();
        SmfgRpsResponse.DataResponse dataResponse = new SmfgRpsResponse.DataResponse();
        SmfgRpsResponse.RepaymentSummary repaymentSummary = new SmfgRpsResponse.RepaymentSummary();
        repaymentSummary.setInterest(1D);
        ArrayList arrayList = new ArrayList<>(Collections.singleton(repaymentSummary));
        repaymentSummary.setInstlAmt(123);
        dataResponse.setRepaymentSummary(arrayList);
        smfgRpsResponse.setData(dataResponse);
        smfgRpsResponse.setStatus("SUCCESS");
        nbfcResponseDTO.setData(smfgRpsResponse);
        nbfcResponseDTO.setApplicationId("5756732");
        nbfcResponseDTO.setLender("SMFG");

        doReturn("MIFIN").when(smfgConfig).getLmsAppName();
        doReturn("PASS@1234").when(smfgConfig).getLmsAppPassword();
        doReturn("REPAYMENT_SCHEDULE").when(smfgConfig).getLmsDeviceRpsId();
        doReturn("10.1.1.3").when(smfgConfig).getLmsStaticIpAddress();
        doReturn("28.6127356").when(smfgConfig).getLmsLatitude();
        doReturn("77.3877269").when(smfgConfig).getLmsLongitude();
        doReturn(Optional.of(lendingApplication)).when(lendingApplicationDao).findById(any(Long.class));
        doReturn(lendingApplicationLenderDetails).when(lendingApplicationLenderDetailsDao).findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), eq(Status.ACTIVE.name()), eq("SMFG"));
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.RPS));
        doReturn("mockString").when(objectMapper).writeValueAsString(any(SmfgRpsResponse.class));
        doReturn(smfgRpsResponse).when(objectMapper).readValue(any(String.class), eq(SmfgRpsResponse.class));

        LenderEdIScheduleResponseDTO edISchedule = smfgRepaymentService.invokeRpsGenerate(5756732L);
        verify(lenderAPIGateway, times(1)).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.RPS));
        assertNotNull(edISchedule);
    }

    @Test
    public void invokeRpsGenerate_LendingApplicationNotFoundFailure() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(false);
        nbfcResponseDTO.setData(new SmfgRpsService());
        nbfcResponseDTO.setApplicationId("5756732");
        nbfcResponseDTO.setLender("SMFG");
        doReturn(lendingApplicationLenderDetails).when(lendingApplicationLenderDetailsDao).findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), eq(Status.ACTIVE.name()), eq("SMFG"));
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.RPS));

        LenderEdIScheduleResponseDTO edISchedule = smfgRepaymentService.invokeRpsGenerate(5756732L);
        assertNull(edISchedule);
    }

    @Test
    public void invokeRpsGenerate_LenderDetailsNotFoundFailure() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(false);
        nbfcResponseDTO.setData(new SmfgRpsService());
        nbfcResponseDTO.setApplicationId("5756732");
        nbfcResponseDTO.setLender("SMFG");
        doReturn(Optional.of(lendingApplication)).when(lendingApplicationDao).findById(any(Long.class));
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.RPS));

        LenderEdIScheduleResponseDTO edISchedule = smfgRepaymentService.invokeRpsGenerate(5756732L);
        assertNull(edISchedule);
    }

    @Test
    public void invokeRpsGenerate_APIFailure() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setId(5756732L);
        lendingApplication.setMerchantId(20000404L);
        lendingApplication.setLender("SMFG");
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadId("lead123");
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        NBFCResponseDTO nbfcResponseDTO = new NBFCResponseDTO<>();
        nbfcResponseDTO.setSuccess(false);
        nbfcResponseDTO.setData(new SmfgRpsService());
        nbfcResponseDTO.setApplicationId("5756732");
        nbfcResponseDTO.setLender("SMFG");
        doReturn(Optional.of(lendingApplication)).when(lendingApplicationDao).findById(any(Long.class));
        doReturn(lendingApplicationLenderDetails).when(lendingApplicationLenderDetailsDao).findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(anyLong(), eq(Status.ACTIVE.name()), eq("SMFG"));
        doReturn(nbfcResponseDTO).when(lenderAPIGateway).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.RPS));

        LenderEdIScheduleResponseDTO edISchedule = smfgRepaymentService.invokeRpsGenerate(5756732L);
        verify(lenderAPIGateway, times(1)).invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.RPS));
        assertNull(edISchedule);
    }
}
