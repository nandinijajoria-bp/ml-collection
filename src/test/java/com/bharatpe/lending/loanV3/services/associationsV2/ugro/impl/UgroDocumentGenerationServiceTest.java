package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroUdyamRegistrationResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
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
public class UgroDocumentGenerationServiceTest {

    @Mock
    CommonService commonService;

    @Mock
    ILenderAPIGateway lenderAPIGateway;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    UgroConfig ugroConfig;

    @InjectMocks
    UgroDocumentGenerationService ugroDocumentGenerationService;

    @Test
    public void testGetUdyamRegistrationResponse() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);

        UgroUdyamRegistrationResponse ugroUdyamRegistrationResponse = new UgroUdyamRegistrationResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroUdyamRegistrationResponse, "");
        when(lenderAPIGateway.invokeStage(any(), any(LenderAssociationStages.class))).thenReturn(nbfcResponseDto);

        UgroUdyamRegistrationResponse udyamRegistrationResponse = new UgroUdyamRegistrationResponse();
        udyamRegistrationResponse.setLink("setLink");
        when(objectMapper.convertValue(any(), eq(UgroUdyamRegistrationResponse.class))).thenReturn(udyamRegistrationResponse);

        ugroDocumentGenerationService.getUdyamRegistrationResponse(lenderAssociationDetailsRequestDto);
    }

    @Test
    public void testGetUdyamRegistrationResponse_EmptyLA() throws Exception {
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, null, null, null, true, false);
        ugroDocumentGenerationService.getUdyamRegistrationResponse(lenderAssociationDetailsRequestDto);
    }

    @Test
    public void testGetUdyamRegistrationResponse_Exception() throws Exception {
        LendingApplication lendingApplication = new LendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, false);

        UgroUdyamRegistrationResponse ugroUdyamRegistrationResponse = new UgroUdyamRegistrationResponse();
        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroUdyamRegistrationResponse, "");
        when(lenderAPIGateway.invokeStage(any(), any(LenderAssociationStages.class))).thenReturn(nbfcResponseDto);

        UgroUdyamRegistrationResponse udyamRegistrationResponse = new UgroUdyamRegistrationResponse();
        udyamRegistrationResponse.setLink("setLink");
        when(objectMapper.convertValue(any(), eq(UgroUdyamRegistrationResponse.class))).thenThrow(new RuntimeException("Some exception has occurred!"));

        ugroDocumentGenerationService.getUdyamRegistrationResponse(lenderAssociationDetailsRequestDto);
    }

}
