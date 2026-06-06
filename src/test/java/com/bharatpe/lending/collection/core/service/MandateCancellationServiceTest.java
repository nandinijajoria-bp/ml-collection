package com.bharatpe.lending.collection.core.service;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingPullPaymentDao;
import com.bharatpe.lending.common.dto.ResponseDTO;
import com.bharatpe.lending.common.entity.LendingPullPayment;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPullPaymentSlave;
import com.bharatpe.lending.dto.PgMandateExecutionResponse;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.service.APIGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MandateCancellationService Tests")
class MandateCancellationServiceTest {
//
//    @InjectMocks
//    private MandateCancellationService mandateCancellationService;
//
//    @Mock
//    private LendingPullPaymentDao lendingPullPaymentDao;
//
//    @Mock
//    private LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;
//
//    @Mock
//    private APIGatewayService apiGatewayService;
//
//    private LendingPaymentSchedule lendingPaymentSchedule;
//    private LendingPullPayment lendingPullPayment;
//    private LendingPullPaymentSlave lendingPullPaymentSlave;
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.initMocks(this);
//
//        // Initialize loan
//        lendingPaymentSchedule = new LendingPaymentSchedule();
//        lendingPaymentSchedule.setId(1L);
//        lendingPaymentSchedule.setStatus("FORECLOSED");
//        lendingPaymentSchedule.setNbfc("NBFC1");
//        lendingPaymentSchedule.setMerchantId(100L);
//
//        // Initialize pull payment
//        lendingPullPayment = new LendingPullPayment();
//        lendingPullPayment.setId(1L);
//        lendingPullPayment.setStatus("PENDING");
//        lendingPullPayment.setProvider("UPI");
//
//        // Initialize slave pull payment
//        lendingPullPaymentSlave = new LendingPullPaymentSlave();
//        lendingPullPaymentSlave.setId(1L);
//    }
//
//    @Test
//    @DisplayName("Should skip mandate cancellation when loan status is ACTIVE")
//    void testSkipCancelWhenLoanStatusIsActive() {
//        lendingPaymentSchedule.setStatus("ACTIVE");
//
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(lendingPullPaymentDaoSlave, never()).findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class));
//    }
//
//    @Test
//    @DisplayName("Should skip mandate cancellation when no pending executions found")
//    void testSkipCancelWhenNoPendingExecutions() {
//        when(lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class)))
//                .thenReturn(Collections.emptyList());
//
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(lendingPullPaymentDao, never()).save(any());
//    }
//
//    @Test
//    @DisplayName("Should skip mandate when pull payment not found")
//    void testSkipCancelWhenPullPaymentNotFound() {
//        when(lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class)))
//                .thenReturn(Arrays.asList(lendingPullPaymentSlave));
//        when(lendingPullPaymentDao.findById(anyLong())).thenReturn(Optional.empty());
//
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(lendingPullPaymentDao, never()).save(any());
//    }
//
//    @Test
//    @DisplayName("Should skip mandate when pull payment status is not PENDING")
//    void testSkipCancelWhenPullPaymentStatusNotPending() {
//        lendingPullPayment.setStatus("CANCELLED");
//
//        when(lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class)))
//                .thenReturn(Arrays.asList(lendingPullPaymentSlave));
//        when(lendingPullPaymentDao.findById(anyLong())).thenReturn(Optional.of(lendingPullPayment));
//
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(lendingPullPaymentDao, never()).save(any());
//    }
//
//    @Test
//    @DisplayName("Should cancel Digio pending mandate execution successfully")
//    void testCancelDigioPendingMandateExecutionSuccess() {
//        lendingPullPayment.setProvider("DIGIO");
//        lendingPullPayment.setNachTransactionId("NACH123456");
//
//        ResponseDTO<Map<String, Object>> responseDTO = new ResponseDTO<>();
//        responseDTO.setSuccess(true);
//
//        when(lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class)))
//                .thenReturn(Arrays.asList(lendingPullPaymentSlave));
//        when(lendingPullPaymentDao.findById(anyLong())).thenReturn(Optional.of(lendingPullPayment));
//        when(apiGatewayService.cancelDigioPresentmentOnForeclosure(anyString())).thenReturn(responseDTO);
//
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(apiGatewayService, times(1)).cancelDigioPresentmentOnForeclosure("NACH123456");
//        verify(lendingPullPaymentDao, times(1)).save(any(LendingPullPayment.class));
//    }
//
//    @Test
//    @DisplayName("Should handle Digio cancellation failure gracefully")
//    void testCancelDigioPendingMandateExecutionFailure() {
//        lendingPullPayment.setProvider("DIGIO");
//        lendingPullPayment.setNachTransactionId("NACH123456");
//
//        ResponseDTO<Map<String, Object>> responseDTO = new ResponseDTO<>();
//        responseDTO.setSuccess(false);
//
//        when(lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class)))
//                .thenReturn(Arrays.asList(lendingPullPaymentSlave));
//        when(lendingPullPaymentDao.findById(anyLong())).thenReturn(Optional.of(lendingPullPayment));
//        when(apiGatewayService.cancelDigioPresentmentOnForeclosure(anyString())).thenReturn(responseDTO);
//
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(apiGatewayService, times(1)).cancelDigioPresentmentOnForeclosure("NACH123456");
//        verify(lendingPullPaymentDao, never()).save(any());
//    }
//
//    @Test
//    @DisplayName("Should skip Digio cancellation when nach transaction id is null")
//    void testSkipDigioCancelWhenNachTransactionIdIsNull() {
//        lendingPullPayment.setProvider("DIGIO");
//        lendingPullPayment.setNachTransactionId(null);
//
//        when(lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class)))
//                .thenReturn(Arrays.asList(lendingPullPaymentSlave));
//        when(lendingPullPaymentDao.findById(anyLong())).thenReturn(Optional.of(lendingPullPayment));
//
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(apiGatewayService, never()).cancelDigioPresentmentOnForeclosure(anyString());
//        verify(lendingPullPaymentDao, never()).save(any());
//    }
//
//    @Test
//    @DisplayName("Should cancel non-Digio UPI mandate execution successfully")
//    void testCancelNonDigioUpiMandateExecutionSuccess() {
//        lendingPullPayment.setProvider("UPI");
//
//        PgMandateExecutionResponse pgResponse = new PgMandateExecutionResponse();
//        pgResponse.setStatusCode("200");
//        PgMandateExecutionResponse.Data data = new PgMandateExecutionResponse.Data();
//        data.setStatus("CLIENT_CANCELLED");
//        pgResponse.setData(data);
//
//        when(lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class)))
//                .thenReturn(Arrays.asList(lendingPullPaymentSlave));
//        when(lendingPullPaymentDao.findById(anyLong())).thenReturn(Optional.of(lendingPullPayment));
//        when(apiGatewayService.cancelMandateExecution(anyString(), anyLong(), any(Lender.class)))
//                .thenReturn(pgResponse);
//
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(apiGatewayService, times(1)).cancelMandateExecution("LENDING1", 100L, Lender.NBFC1);
//        verify(lendingPullPaymentDao, times(1)).save(any(LendingPullPayment.class));
//    }
//
//    @Test
//    @DisplayName("Should process multiple pending mandate executions")
//    void testProcessMultiplePendingMandateExecutions() {
//        LendingPullPaymentSlave payment2 = new LendingPullPaymentSlave();
//        payment2.setId(2L);
//
//        LendingPullPayment pullPayment2 = new LendingPullPayment();
//        pullPayment2.setId(2L);
//        pullPayment2.setStatus("PENDING");
//        pullPayment2.setProvider("DIGIO");
//        pullPayment2.setNachTransactionId("NACH789012");
//
//        ResponseDTO<Map<String, Object>> responseDTO = new ResponseDTO<>();
//        responseDTO.setSuccess(true);
//
//        when(lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class)))
//                .thenReturn(Arrays.asList(lendingPullPaymentSlave, payment2));
//        when(lendingPullPaymentDao.findById(1L)).thenReturn(Optional.of(lendingPullPayment));
//        when(lendingPullPaymentDao.findById(2L)).thenReturn(Optional.of(pullPayment2));
//        when(apiGatewayService.cancelDigioPresentmentOnForeclosure(anyString())).thenReturn(responseDTO);
//
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(apiGatewayService, times(1)).cancelDigioPresentmentOnForeclosure("NACH123456");
//        verify(apiGatewayService, times(1)).cancelDigioPresentmentOnForeclosure("NACH789012");
//        verify(lendingPullPaymentDao, times(2)).save(any(LendingPullPayment.class));
//    }
//
//    @Test
//    @DisplayName("Should set correct error description for cancelled Digio payment")
//    void testDigioCancelSetsCorrectErrorDescription() {
//        lendingPullPayment.setProvider("DIGIO");
//        lendingPullPayment.setNachTransactionId("NACH123456");
//
//        ResponseDTO<Map<String, Object>> responseDTO = new ResponseDTO<>();
//        responseDTO.setSuccess(true);
//
//        when(lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class)))
//                .thenReturn(Arrays.asList(lendingPullPaymentSlave));
//        when(lendingPullPaymentDao.findById(anyLong())).thenReturn(Optional.of(lendingPullPayment));
//        when(apiGatewayService.cancelDigioPresentmentOnForeclosure(anyString())).thenReturn(responseDTO);
//
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(lendingPullPaymentDao).save(any(LendingPullPayment.class));
//    }
//
//    @Test
//    @DisplayName("Should handle exception gracefully during mandate cancellation")
//    void testHandleExceptionGracefully() {
//        when(lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class)))
//                .thenThrow(new RuntimeException("Database error"));
//
//        // Should not throw exception
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(lendingPullPaymentDao, never()).save(any());
//    }
//
//    @Test
//    @DisplayName("Should skip Digio execution when provider name has whitespace")
//    void testDigioProviderDetectionWithWhitespace() {
//        lendingPullPayment.setProvider("  DIGIO  ");
//        lendingPullPayment.setNachTransactionId("NACH123456");
//
//        ResponseDTO<Map<String, Object>> responseDTO = new ResponseDTO<>();
//        responseDTO.setSuccess(true);
//
//        when(lendingPullPaymentDaoSlave.findPendingPullPaymentsByLoanIdCreatedAfter(anyLong(), any(Date.class)))
//                .thenReturn(Arrays.asList(lendingPullPaymentSlave));
//        when(lendingPullPaymentDao.findById(anyLong())).thenReturn(Optional.of(lendingPullPayment));
//        when(apiGatewayService.cancelDigioPresentmentOnForeclosure(anyString())).thenReturn(responseDTO);
//
//        mandateCancellationService.cancelPendingMandateExecutions(lendingPaymentSchedule);
//
//        verify(apiGatewayService, times(1)).cancelDigioPresentmentOnForeclosure("NACH123456");
//    }
}
