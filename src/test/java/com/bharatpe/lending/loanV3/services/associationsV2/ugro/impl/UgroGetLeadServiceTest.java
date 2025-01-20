package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroGetLeadResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ObjectUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class UgroGetLeadServiceTest {
    @Mock
    CommonService commonService;

    @Mock
    ILenderAPIGateway lenderAPIGateway;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    UgroConfig ugroConfig;

    @InjectMocks
    UgroGetLeadService ugroGetLeadService;

    @Test
    public void testInvokeGetLead() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        NBFCRequestDTO<?> getLeadRequest = new NBFCRequestDTO<>("UGRO", "LENDING", 600123L, null, null, false);

        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);

        ugroGetLeadService.invokeGetLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeGetLead_EmptyLA() throws Exception {
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, null, null, null, true, true);

        ugroGetLeadService.invokeGetLead(lenderAssociationDetailsDto);
    }

//    @Test
//    public void testInvokeGetLead_EmptyPayload() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);
//
//        NBFCRequestDTO<?> getLeadRequest = new NBFCRequestDTO<>("UGRO", "LENDING", 600123L, null, null, false);
//
//        when(ObjectUtils.isEmpty(any(NBFCRequestDTO.class))).thenReturn(true);
//        ugroGetLeadService.invokeGetLead(lenderAssociationDetailsDto);
//    }

    @Test
    public void testInvokeGetLead_UdyamSuccessResponse() throws Exception {
        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        NBFCRequestDTO<?> getLeadRequest = new NBFCRequestDTO<>("UGRO", "LENDING", 600123L, null, null, false);

        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        getLeadResponse.setStatus("PENDING");
        getLeadResponse.setBankAccountVerification("SUCCESS");
        getLeadResponse.setBusinessProofVerification("SUCCESS");
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);

        ugroGetLeadService.invokeGetLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeGetLead_UdyamSuccessResponse2() throws Exception {
        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        NBFCRequestDTO<?> getLeadRequest = new NBFCRequestDTO<>("UGRO", "LENDING", 600123L, null, null, false);

        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        UgroGetLeadResponse.KYBRemarks kybRemarks = new UgroGetLeadResponse.KYBRemarks("SUCCESS", "SUCCESS");
        getLeadResponse.setKybRemarks(kybRemarks);
        getLeadResponse.setStatus("PENDING");
        getLeadResponse.setBankAccountVerification("SUCCESS");
        getLeadResponse.setBusinessProofVerification("SUCCESS");
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);

        ugroGetLeadService.invokeGetLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeGetLead_UdyamPendingResponse() throws Exception {
        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        NBFCRequestDTO<?> getLeadRequest = new NBFCRequestDTO<>("UGRO", "LENDING", 600123L, null, null, false);

        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        getLeadResponse.setStatus("PENDING");
        getLeadResponse.setBankAccountVerification("SUCCESS");
        getLeadResponse.setBusinessProofVerification("PENDING");
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);

        ugroGetLeadService.invokeGetLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeGetLead_Exception() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");
        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenThrow(new RuntimeException("Some exception has occurred!"));

        ugroGetLeadService.invokeGetLead(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeUdyamStatusCheck() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");

        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);

        ugroGetLeadService.invokeUdyamStatusCheck(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeUdyamStatusCheck_EmptyLA() throws Exception {
        LendingApplication lendingApplication = null;
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        ugroGetLeadService.invokeUdyamStatusCheck(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeUdyamStatusCheck_UdyamFailed() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");

        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);

        ugroGetLeadService.invokeUdyamStatusCheck(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeUdyamStatusCheck_UdyamSuccess() throws Exception {
        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");

        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        getLeadResponse.setStatus("SUCCESS");
        getLeadResponse.setBankAccountVerification("SUCCESS");
        getLeadResponse.setBusinessProofVerification("SUCCESS");
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);

        ugroGetLeadService.invokeUdyamStatusCheck(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeUdyamStatusCheck_UdyamSuccess2() throws Exception {
        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");

        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        getLeadResponse.setStatus("SUCCESS");
        getLeadResponse.setBankAccountVerification("SUCCESS");
        getLeadResponse.setBusinessProofVerification("PENDING");
        getLeadResponse.setKybRemarks(new UgroGetLeadResponse.KYBRemarks("SUCCESS", "SUCCESS"));
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenReturn(getLeadResponse);

        ugroGetLeadService.invokeUdyamStatusCheck(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeUdyamStatusCheck_Exception() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 2002889L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        UgroGetLeadResponse ugroGetLeadResponse = new UgroGetLeadResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroGetLeadResponse, "");

        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);

        UgroGetLeadResponse getLeadResponse = new UgroGetLeadResponse();
        when(objectMapper.convertValue(any(), eq(UgroGetLeadResponse.class))).thenThrow(new RuntimeException("Some exception has occurred!"));

        ugroGetLeadService.invokeUdyamStatusCheck(lenderAssociationDetailsDto);
    }
}

















