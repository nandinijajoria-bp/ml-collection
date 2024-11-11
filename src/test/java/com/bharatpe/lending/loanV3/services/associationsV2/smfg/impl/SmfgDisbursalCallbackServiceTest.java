package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.smfg.SmfgCallbackRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

@RunWith(SpringJUnit4ClassRunner.class)
@TestConfiguration
public class SmfgDisbursalCallbackServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @InjectMocks
    private SmfgDisbursalCallbackService smfgDisbursalCallbackService;

    @Test
    public void handleCallbackResponse_Success() throws Exception {
        NBFCResponseDTO callbackRequest = new NBFCResponseDTO<>();
        callbackRequest.setLender("SMFG");
        callbackRequest.setSuccess(true);
        callbackRequest.setApplicationId("5756732");
        SmfgCallbackRequest smfgCallbackRequest = new SmfgCallbackRequest();
        SmfgCallbackRequest.Data data = new SmfgCallbackRequest.Data();
        SmfgCallbackRequest.Output output = new SmfgCallbackRequest.Output();
        output.setDisbursalstatus("Processed");
        data.setOutput(output);


        smfgCallbackRequest.setData(data);
        smfgCallbackRequest.setStatus("SUCCESS");
        callbackRequest.setData(smfgCallbackRequest);
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgCallbackRequest.class));
        doReturn(smfgCallbackRequest).when(objectMapper).readValue(any(String.class), eq(SmfgCallbackRequest.class));

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        doReturn(lendingApplicationLenderDetails).when(lendingApplicationLenderDetailsDao).findByApplicationIdAndLender(anyLong(), anyString());

        DisbursalCallbackCommonDTO disbursalCallbackCommon = smfgDisbursalCallbackService.handleCallbackResponse(callbackRequest);
        assertTrue(disbursalCallbackCommon.getStatus());
    }

    @Test
    public void handleCallbackResponse_Failure() throws Exception {
        NBFCResponseDTO callbackRequest = new NBFCResponseDTO<>();
        callbackRequest.setLender("SMFG");
        callbackRequest.setSuccess(true);
        callbackRequest.setApplicationId("5756732");
        SmfgCallbackRequest smfgCallbackRequest = new SmfgCallbackRequest();
        SmfgCallbackRequest.Data data = new SmfgCallbackRequest.Data();

        smfgCallbackRequest.setData(data);
        smfgCallbackRequest.setStatus("FAILURE");
        callbackRequest.setData(smfgCallbackRequest);
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgCallbackRequest.class));
        doReturn(smfgCallbackRequest).when(objectMapper).readValue(any(String.class), eq(SmfgCallbackRequest.class));

        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setApplicationId(5756732L);
        lendingApplicationLenderDetails.setStatus("ACTIVE");
        lendingApplicationLenderDetails.setLender("SMFG");
        lendingApplicationLenderDetails.setStage("BRE");
        lendingApplicationLenderDetails.setCccId("12345");
        lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
        lendingApplicationLenderDetails.setAnnualRoi(35.45D);
        doReturn(lendingApplicationLenderDetails).when(lendingApplicationLenderDetailsDao).findByApplicationIdAndLender(anyLong(), anyString());

        DisbursalCallbackCommonDTO disbursalCallbackCommon = smfgDisbursalCallbackService.handleCallbackResponse(callbackRequest);
        assertFalse(disbursalCallbackCommon.getStatus());
    }

    @Test
    public void handleCallbackResponse_Exception() throws Exception {
        NBFCResponseDTO callbackRequest = new NBFCResponseDTO<>();
        callbackRequest.setLender("SMFG");
        callbackRequest.setSuccess(true);
        callbackRequest.setApplicationId("5756732");
        SmfgCallbackRequest smfgCallbackRequest = new SmfgCallbackRequest();
        SmfgCallbackRequest.Data data = new SmfgCallbackRequest.Data();
        SmfgCallbackRequest.Output output = new SmfgCallbackRequest.Output();
        output.setStatus("Processed");
        data.setOutput(output);


        smfgCallbackRequest.setData(data);
        smfgCallbackRequest.setStatus("FAILURE");
        callbackRequest.setData(smfgCallbackRequest);
        doReturn("SuccessResponse").when(objectMapper).writeValueAsString(any(SmfgCallbackRequest.class));
        doReturn(smfgCallbackRequest).when(objectMapper).readValue(any(String.class), eq(SmfgCallbackRequest.class));

        DisbursalCallbackCommonDTO disbursalCallbackCommon = smfgDisbursalCallbackService.handleCallbackResponse(callbackRequest);
        assertFalse(disbursalCallbackCommon.getStatus());
    }
}
