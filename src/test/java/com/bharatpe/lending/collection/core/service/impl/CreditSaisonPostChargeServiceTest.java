package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl.CreditSaisonPostChargeService;
import com.bharatpe.lending.loanV3.services.gateway.NbfcLenderGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditSaisonPostChargeServiceTest {

    @InjectMocks
    private CreditSaisonPostChargeService creditSaisonPostChargeService;

    @Mock
    private PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Mock
    private LendingApplicationDao lendingApplicationDao;

    @Mock
    private NbfcLenderGateway nbfcLenderGateway;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void postPendingChargesToLender_doesNothing_whenPartnerChargesListIsEmpty() {
        LendingPaymentSchedule activeLoan = mock(LendingPaymentSchedule.class);

        creditSaisonPostChargeService.postPendingChargesToLender(activeLoan, Collections.emptyMap(), Collections.emptyMap());

        verifyZeroInteractions(nbfcLenderGateway, penaltyFeeLedgerDao);
    }

    @Test
    void postPendingChargesToLender_updatesStatus_whenPostingIsSuccessful() throws Exception {
        LendingPaymentSchedule activeLoan = mock(LendingPaymentSchedule.class);
        LendingApplication lendingApplication = mock(LendingApplication.class);
        PenaltyFeeLedger ledger = mock(PenaltyFeeLedger.class);

        Map<PenaltyFeeLedger, Double> paidNachBounceMap = new HashMap<>();
        paidNachBounceMap.put(ledger, 100.0);
        Map<PenaltyFeeLedger, Double> paidPenalChargeMap = Collections.emptyMap();

        when(activeLoan.getApplicationId()).thenReturn(1L);
        when(activeLoan.getMerchantId()).thenReturn(2L);
        when(lendingApplicationDao.findByIdAndMerchantId(1L, 2L)).thenReturn(lendingApplication);
        when(lendingApplication.getExternalLoanId()).thenReturn("EXT123");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(nbfcLenderGateway.invoke(anyString(), eq(NbfcResponseDto.class), anyString()))
                .thenReturn(NbfcResponseDto.builder()
                        .success(true)
                        .data(new Object())
                        .build());

        creditSaisonPostChargeService.postPendingChargesToLender(activeLoan, paidNachBounceMap, paidPenalChargeMap);

        verify(penaltyFeeLedgerDao).saveAll(anyList());
    }

    @Test
    void postPendingChargesToLender_updatesStatus_whenPostingFails() throws Exception {
        LendingPaymentSchedule activeLoan = mock(LendingPaymentSchedule.class);
        LendingApplication lendingApplication = mock(LendingApplication.class);
        PenaltyFeeLedger ledger = mock(PenaltyFeeLedger.class);

        Map<PenaltyFeeLedger, Double> paidNachBounceMap = new HashMap<>();
        paidNachBounceMap.put(ledger, 100.0);
        Map<PenaltyFeeLedger, Double> paidPenalChargeMap = Collections.emptyMap();

        when(activeLoan.getApplicationId()).thenReturn(1L);
        when(activeLoan.getMerchantId()).thenReturn(2L);
        when(lendingApplicationDao.findByIdAndMerchantId(1L, 2L)).thenReturn(lendingApplication);
        when(lendingApplication.getExternalLoanId()).thenReturn("EXT123");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(nbfcLenderGateway.invoke(anyString(), eq(NbfcResponseDto.class), anyString()))
                .thenReturn(NbfcResponseDto.builder()
                .success(false)
                .data(new Object())
                .build());

        creditSaisonPostChargeService.postPendingChargesToLender(activeLoan, paidNachBounceMap, paidPenalChargeMap);

        verify(penaltyFeeLedgerDao).saveAll(anyList());
    }
}
