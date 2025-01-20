package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsDto;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroCreateApplicationResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class UgroCreateClientServiceTest {
    @Mock
    KycUtils kycUtils;

    @Mock
    CommonService commonService;

    @Mock
    ILenderAPIGateway lenderAPIGateway;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    UgroCreateClientService ugroCreateClientService;

    @Test
    public void testInvokeCreateClient() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setCccId("1234");
        lendingApplicationLenderDetails.setKycStatus("KYC");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 200128L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        UgroCreateApplicationResponse ugroCreateApplicationResponse = new UgroCreateApplicationResponse();
        ugroCreateApplicationResponse.setApplicationId("600123");
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroCreateApplicationResponse, "error");
        when(lenderAPIGateway.invokeStage(any(), any(LenderAssociationStages.class))).thenReturn(nbfcResponseDto);


        UgroCreateApplicationResponse createClientResponse = new UgroCreateApplicationResponse();
        createClientResponse.setApplicationId("600123");

        when(objectMapper.convertValue(any(), eq(UgroCreateApplicationResponse.class))).thenReturn(createClientResponse);

        ugroCreateClientService.invokeCreateClient(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeCreateClient_EmptyLA() throws Exception {
        LendingApplication lendingApplication = null;
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 200128L, null, lendingApplication, null, null, true, true);


        ugroCreateClientService.invokeCreateClient(lenderAssociationDetailsDto);
    }

    @Test
    public void testInvokeCreateClient_EmptyPayload() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setLeadStatus("APPROVED");
        lendingApplicationLenderDetails.setLeadId("LD123");

        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = new LenderAssociationDetailsRequestDto();
        try{
            when(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId()).thenThrow(new RuntimeException("Some exception has occurred!"));
        }
        catch (Exception ex){

        }

        ugroCreateClientService.invokeCreateClient(lenderAssociationDetailsRequestDto);
    }

    @Test
    public void testInvokeCreateClient_Exception() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        lendingApplicationLenderDetails.setCccId("1234");
        lendingApplicationLenderDetails.setKycStatus("KYC");
        LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto(600123L, 200128L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);

        UgroCreateApplicationResponse ugroCreateApplicationResponse = new UgroCreateApplicationResponse();
        ugroCreateApplicationResponse.setApplicationId("600123");
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroCreateApplicationResponse, "error");
        when(lenderAPIGateway.invokeStage(any(), any(LenderAssociationStages.class))).thenReturn(nbfcResponseDto);


        UgroCreateApplicationResponse createClientResponse = new UgroCreateApplicationResponse();
        createClientResponse.setApplicationId("600123");

        when(objectMapper.convertValue(any(), eq(UgroCreateApplicationResponse.class))).thenThrow(new RuntimeException("Some exception has occurred!"));

        ugroCreateClientService.invokeCreateClient(lenderAssociationDetailsDto);
    }
}






















