//package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;
//
//import com.bharatpe.common.entities.LendingApplication;
//import com.bharatpe.common.entities.LendingLedger;
//import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
//import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
//import com.bharatpe.lending.common.enums.LenderAssociationStages;
//import com.bharatpe.lending.common.enums.Status;
//import com.bharatpe.lending.dao.LendingApplicationDao;
//import com.bharatpe.lending.enums.Lender;
//import com.bharatpe.lending.loanV3.config.UgroConfig;
//import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
//import com.bharatpe.lending.loanV3.dto.response.ugro.UgroClosureBreakupResponse;
//import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.test.context.junit4.SpringRunner;
//
//import java.util.Optional;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.when;
//
//@RunWith(SpringRunner.class)
//public class UgroForeclosureServiceTest {
//
//    @Mock
//    LendingApplicationDao lendingApplicationDao;
//
//    @Mock
//    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
//
//    @Mock
//    ILenderAPIGateway lenderAPIGateway;
//
//    @Mock
//    ObjectMapper objectMapper;
//
//    @Mock
//    UgroConfig ugroConfig;
//
//    @InjectMocks
//    UgroForeclosureService ugroForeclosureService;
//
//    @Test
//    public void testGetForeclosureDetails() throws Exception{
//        Long applicationId = 6000123L;
//
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroClosureBreakupResponse ugroClosureBreakupResponse = new UgroClosureBreakupResponse();
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroClosureBreakupResponse, "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//
//        UgroClosureBreakupResponse response = new UgroClosureBreakupResponse();
//        UgroClosureBreakupResponse.BreakupDetails breakupDetails = new UgroClosureBreakupResponse.BreakupDetails();
//        breakupDetails.setTotal(2222D);
//        response.setBreakup(breakupDetails);
//        when(objectMapper.convertValue(any(), eq(UgroClosureBreakupResponse.class))).thenReturn(response);
//
//        ugroForeclosureService.getForeclosureDetails(applicationId);
//    }
//
//    @Test
//    public void testGetForeclosureDetails_Exception() throws Exception{
//        Long applicationId = 6000123L;
//
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroClosureBreakupResponse ugroClosureBreakupResponse = new UgroClosureBreakupResponse();
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroClosureBreakupResponse, "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//
//        UgroClosureBreakupResponse response = new UgroClosureBreakupResponse();
//        UgroClosureBreakupResponse.BreakupDetails breakupDetails = new UgroClosureBreakupResponse.BreakupDetails();
//        breakupDetails.setTotal(2222D);
//        response.setBreakup(breakupDetails);
//        when(objectMapper.convertValue(any(), eq(UgroClosureBreakupResponse.class))).thenThrow(new RuntimeException("Some exception has occurred!"));
//
//        ugroForeclosureService.getForeclosureDetails(applicationId);
//    }
//
//    @Test
//    public void testGetForeclosureDetails_EmptyBreakupResponse() throws Exception{
//        Long applicationId = 6000123L;
//
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//
//        when(lendingApplicationDao.findById(any())).thenReturn(Optional.of(lendingApplication));
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroClosureBreakupResponse ugroClosureBreakupResponse = new UgroClosureBreakupResponse();
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroClosureBreakupResponse, "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//
//        ugroForeclosureService.getForeclosureDetails(applicationId);
//    }
//
//    @Test
//    public void testGetForeclosureDetails_EmptyLA() throws Exception{
//        Long applicationId = 6000123L;
//
//        ugroForeclosureService.getForeclosureDetails(applicationId);
//    }
//
//    @Test
//    public void testGetForeclosureReceiptRequest() throws Exception {
//        when(ugroConfig.getForeclosureIntent()).thenReturn("FORECLOSURE");
//        Long applicationId = 2000182L;
//
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadId("LD123");
//        when(lendingApplicationDao.findById(applicationId)).thenReturn(Optional.of(lendingApplication));
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        LendingLedger lendingLedger = new LendingLedger();
//        lendingLedger.setAmount(1000D);
//        lendingLedger.setId(1000L);
//
//        ugroForeclosureService.getForeclosureReceiptRequest(applicationId, lendingLedger);
//    }
//
//    @Test
//    public void testGetForeclosureReceiptRequest_EmptyLA() throws Exception {
//        Long applicationId = 2000182L;
//
//        LendingLedger lendingLedger = new LendingLedger();
//
//        ugroForeclosureService.getForeclosureReceiptRequest(applicationId, lendingLedger);
//    }
//
//    @Test
//    public void testGetForeclosureReceiptRequest_Exception() throws Exception {
//        when(ugroConfig.getForeclosureIntent()).thenReturn("FORECLOSURE");
//        Long applicationId = 2000182L;
//
//        LendingApplication lendingApplication = new LendingApplication();
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        lendingApplicationLenderDetails.setLeadId("LD123");
//        when(lendingApplicationDao.findById(applicationId)).thenReturn(Optional.of(lendingApplication));
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenThrow(new RuntimeException("Some exception has occurred!"));
//
//        LendingLedger lendingLedger = new LendingLedger();
//        lendingLedger.setAmount(1000D);
//        lendingLedger.setId(1000L);
//
//        ugroForeclosureService.getForeclosureReceiptRequest(applicationId, lendingLedger);
//    }
//}
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
