//package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;
//
//import com.bharatpe.common.entities.LendingApplication;
//import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
//import com.bharatpe.lending.common.enums.LenderAssociationStages;
//import com.bharatpe.lending.loanV3.config.UgroConfig;
//import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
//import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
//import com.bharatpe.lending.loanV3.dto.response.ugro.UgroConsentResponse;
//import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
//import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.test.context.junit4.SpringRunner;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.when;
//
//@RunWith(SpringRunner.class)
//public class UgroBreServiceTest {
//
//    @Mock
//    CommonService commonService;
//
//    @Mock
//    ObjectMapper objectMapper;
//
//    @Mock
//    ILenderAPIGateway lenderAPIGateway;
//
//    @Mock
//    UgroConfig ugroConfig;
//
//    @InjectMocks
//    UgroBreService ugroBreService;
//
//    @Test
//    public void testInvokeBre() throws Exception {
//        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        lendingApplication.setLender("UGRO");
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus("APPROVED");
//        lendingApplicationLenderDetails.setLeadId("LD123");
//
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);
//
//        UgroConsentResponse ugroConsentResponse = new UgroConsentResponse();
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroConsentResponse, "error");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//
//        UgroConsentResponse breResponseDTO = new UgroConsentResponse();
//        breResponseDTO.setStatus("SUCCESS");
//        when(objectMapper.convertValue(any(), eq(UgroConsentResponse.class))).thenReturn(breResponseDTO);
//
//
//        ugroBreService.invokeBre(lenderAssociationDetailsRequestDto);
//    }
//
//    @Test
//    public void testInvokeBre_EmptyLALD() throws Exception {
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        lendingApplication.setLender("UGRO");
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus("APPROVED");
//
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);
//
//        ugroBreService.invokeBre(lenderAssociationDetailsRequestDto);
//    }
//
//    @Test
//    public void testInvokeBre_EmptyBRE() throws Exception {
//        LendingApplication lendingApplication = null;
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus("APPROVED");
//        lendingApplicationLenderDetails.setLeadId("LD123");
//
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);
//
////        when(any(LendingApplication.class)).thenReturn(null);
//        ugroBreService.invokeBre(lenderAssociationDetailsRequestDto);
//    }
//
//    @Test
//    public void testInvokeBre_Exception() throws Exception {
//        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        lendingApplication.setLender("UGRO");
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus("APPROVED");
//        lendingApplicationLenderDetails.setLeadId("LD123");
//
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);
//
//        UgroConsentResponse ugroConsentResponse = new UgroConsentResponse();
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroConsentResponse, "error");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenThrow(new RuntimeException("Some exception has occurred!"));
//
//        UgroConsentResponse breResponseDTO = new UgroConsentResponse();
//        breResponseDTO.setStatus("SUCCESS");
//        when(objectMapper.convertValue(any(), eq(UgroConsentResponse.class))).thenReturn(breResponseDTO);
//
//
//        ugroBreService.invokeBre(lenderAssociationDetailsRequestDto);
//    }
//
//    @Test
//    public void testInvokeCounterOffer() throws Exception {
//        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        lendingApplication.setLender("UGRO");
//        lendingApplication.setProcessingFee(200D);
//        lendingApplication.setLoanAmount(2200D);
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus("APPROVED");
//        lendingApplicationLenderDetails.setLeadId("LD123");
//
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);
//
//        UgroConsentResponse ugroConsentResponse = new UgroConsentResponse();
//        ugroConsentResponse.setStatus("SUCCESS");
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroConsentResponse, "error");
//
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//        UgroConsentResponse counterOfferResponse = new UgroConsentResponse();
//        counterOfferResponse.setStatus("SUCCESS");
//        when(objectMapper.convertValue(any(), eq(UgroConsentResponse.class))).thenReturn(counterOfferResponse);
//
//        ugroBreService.invokeCounterOffer(lenderAssociationDetailsRequestDto);
//    }
//
//    @Test
//    public void testInvokeCounterOffer_EmptyLeadID() throws Exception {
//        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        lendingApplication.setLender("UGRO");
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus("APPROVED");
//
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);
//
//        ugroBreService.invokeCounterOffer(lenderAssociationDetailsRequestDto);
//    }
//
//    @Test
//    public void testInvokeCounterOffer_EmptyCounterOffer() throws Exception {
//        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        lendingApplication.setLender("UGRO");
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus("APPROVED");
//        lendingApplicationLenderDetails.setLeadId("LD123");
//
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);
//
//        ugroBreService.invokeCounterOffer(lenderAssociationDetailsRequestDto);
//    }
//
//    @Test
//    public void testInvokeCounterOffer_Exception() throws Exception {
//        when(ugroConfig.getSuccessResponse()).thenReturn("SUCCESS");
//        LendingApplication lendingApplication = new LendingApplication();
//        lendingApplication.setId(123L);
//        lendingApplication.setLender("UGRO");
//        lendingApplication.setProcessingFee(200D);
//        lendingApplication.setLoanAmount(2200D);
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadStatus("APPROVED");
//        lendingApplicationLenderDetails.setLeadId("LD123");
//
//        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto(600123L, 200289L, null, lendingApplication, null, lendingApplicationLenderDetails, true, true);
//
//        UgroConsentResponse ugroConsentResponse = new UgroConsentResponse();
//        ugroConsentResponse.setStatus("SUCCESS");
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroConsentResponse, "error");
//
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//        UgroConsentResponse counterOfferResponse = new UgroConsentResponse();
//        counterOfferResponse.setStatus("SUCCESS");
//        when(objectMapper.convertValue(any(), eq(UgroConsentResponse.class))).thenThrow(new RuntimeException("Some exception has occurred!"));
//
//        ugroBreService.invokeCounterOffer(lenderAssociationDetailsRequestDto);
//    }
//
//
//}
