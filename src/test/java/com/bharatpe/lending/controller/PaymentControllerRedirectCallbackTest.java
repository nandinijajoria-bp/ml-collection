package com.bharatpe.lending.controller;

import com.bharatpe.lending.dto.PgPaymentCallbackDTO;
import com.bharatpe.lending.service.LendingCollectionAuditService;
import com.bharatpe.lending.service.LoanCancellationService;
import com.bharatpe.lending.service.PaymentCallbackRequestDTO;
import com.bharatpe.lending.service.PaymentService;
import com.bharatpe.lending.service.SettlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class PaymentControllerRedirectCallbackTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private LoanCancellationService loanCancellationService;

    @Mock
    private SettlementService settlementService;

    @Mock
    private LendingCollectionAuditService lendingCollectionAuditService;

    private PaymentController paymentController;

    @Before
    public void setUp() {
        paymentController = new PaymentController();
        ReflectionTestUtils.setField(paymentController, "paymentService", paymentService);
        ReflectionTestUtils.setField(paymentController, "loanCancellationService", loanCancellationService);
        ReflectionTestUtils.setField(paymentController, "settlementService", settlementService);
        ReflectionTestUtils.setField(paymentController, "lendingCollectionAuditService", lendingCollectionAuditService);
    }

    @Test
    public void redirectCallback_delegatesToHandleCallback() throws Exception {
        when(paymentService.handleCallback(any(PaymentCallbackRequestDTO.class))).thenReturn("OK");
        PaymentCallbackRequestDTO body = new PaymentCallbackRequestDTO();
        body.setOrderId("ORD-NACH-1");
        body.setAmount(50.0);
        String json = new ObjectMapper().writeValueAsString(body);
        MockMvcBuilders.standaloneSetup(paymentController)
                .build()
                .perform(MockMvcRequestBuilders.post("/lending/payment/redirect/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
        verify(paymentService).handleCallback(any(PaymentCallbackRequestDTO.class));
    }

    @Test
    public void redirectCallbackV2_delegatesToHandlePgCallback() throws Exception {
        when(paymentService.handlePgCallback(any(PgPaymentCallbackDTO.class))).thenReturn("OK");
        PgPaymentCallbackDTO body = new PgPaymentCallbackDTO();
        body.setOrderId("ORD-PG-1");
        String json = new ObjectMapper().writeValueAsString(body);
        MockMvcBuilders.standaloneSetup(paymentController)
                .build()
                .perform(MockMvcRequestBuilders.post("/lending/payment/redirect/callback/v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
        verify(paymentService).handlePgCallback(any(PgPaymentCallbackDTO.class));
    }
}
