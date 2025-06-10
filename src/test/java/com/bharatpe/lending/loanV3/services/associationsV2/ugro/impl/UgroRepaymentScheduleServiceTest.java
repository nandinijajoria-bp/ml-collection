//package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;
//
//import com.bharatpe.common.entities.LendingApplication;
//import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
//import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
//import com.bharatpe.lending.common.enums.LenderAssociationStages;
//import com.bharatpe.lending.common.enums.Status;
//import com.bharatpe.lending.dao.LendingApplicationDao;
//import com.bharatpe.lending.enums.Lender;
//import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
//import com.bharatpe.lending.loanV3.dto.response.ugro.UgroRPSResponse;
//import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.test.context.junit4.SpringRunner;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.when;
//
//@RunWith(SpringRunner.class)
//public class UgroRepaymentScheduleServiceTest {
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
//    @InjectMocks
//    UgroRepaymentScheduleService ugroRepaymentScheduleService;
//
//    @Test
//    public void testInvokeRpsGenerate() throws Exception {
//        Long applicationId = 600123L;
//
//        Optional<LendingApplication> lendingApplicationOptional = Optional.of(new LendingApplication());
//        when(lendingApplicationDao.findById(applicationId)).thenReturn(lendingApplicationOptional);
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroRPSResponse ugroRPSResponse = new UgroRPSResponse("status", null);
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroRPSResponse, "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//
//        UgroRPSResponse.DataDetails dataDetails = new UgroRPSResponse.DataDetails();
//        List<UgroRPSResponse.RepaymentSchedule> list= new ArrayList<>();
//        list.add(new UgroRPSResponse.RepaymentSchedule("222", "sId", 2323L, 23232D, 3232323D, 2323D));
//        dataDetails.setRepaymentSchedule(list);
//        dataDetails.setExpiryDate(3232323L);
//        UgroRPSResponse response = new UgroRPSResponse("SUCCESS", dataDetails);
//        when(objectMapper.convertValue(any(), eq(UgroRPSResponse.class))).thenReturn(response);
//
//        ugroRepaymentScheduleService.invokeRpsGenerate(applicationId);
//    }
//
//    @Test
//    public void testInvokeRpsGenerate_EmptyLA() throws Exception {
//        Long applicationId = 600123L;
//
//        Optional<LendingApplication> lendingApplicationOptional = Optional.empty();
//        when(lendingApplicationDao.findById(applicationId)).thenReturn(lendingApplicationOptional);
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        ugroRepaymentScheduleService.invokeRpsGenerate(applicationId);
//    }
//
//
//    @Test
//    public void testInvokeRpsGenerate_Exception() throws Exception {
//        Long applicationId = 600123L;
//
//        Optional<LendingApplication> lendingApplicationOptional = Optional.of(new LendingApplication());
//        when(lendingApplicationDao.findById(applicationId)).thenReturn(lendingApplicationOptional);
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroRPSResponse ugroRPSResponse = new UgroRPSResponse("status", null);
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroRPSResponse, "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//
//        when(objectMapper.convertValue(any(), eq(UgroRPSResponse.class))).thenThrow(new RuntimeException("Some exception has occured!"));
//
//        ugroRepaymentScheduleService.invokeRpsGenerate(applicationId);
//    }
//
//    @Test
//    public void testInvokeRpsGenerate_EmptyEdiSchedule() throws Exception {
//        Long applicationId = 600123L;
//
//        Optional<LendingApplication> lendingApplicationOptional = Optional.of(new LendingApplication());
//        when(lendingApplicationDao.findById(applicationId)).thenReturn(lendingApplicationOptional);
//
//        LendingApplicationLenderDetails lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
//        when(lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(any(), any(), any())).thenReturn(lendingApplicationLenderDetails);
//
//        UgroRPSResponse ugroRPSResponse = new UgroRPSResponse("status", null);
//        NBFCResponseDTO<?> nbfcResponseDto = new NBFCResponseDTO<>(true, "600123", "LENDING", "UGRO", ugroRPSResponse, "");
//        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
//
//        UgroRPSResponse.DataDetails dataDetails = new UgroRPSResponse.DataDetails();
//        dataDetails.setRepaymentSchedule(new ArrayList<>());
//        dataDetails.setExpiryDate(3232323L);
//        UgroRPSResponse response = new UgroRPSResponse("SUCCESS", dataDetails);
//        when(objectMapper.convertValue(any(), eq(UgroRPSResponse.class))).thenReturn(response);
//
//        ugroRepaymentScheduleService.invokeRpsGenerate(applicationId);
//    }
//}
