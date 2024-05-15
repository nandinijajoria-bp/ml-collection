package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import org.junit.jupiter.api.Test;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.LoanClosureDTO;
import com.bharatpe.lending.collection.core.service.LoanClosureService;
import com.bharatpe.lending.collection.core.service.LoanPaymentLedgerAdjustmentService;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

class LoanStatusServiceImplTest {
    @Mock
    private LoanPaymentLedgerAdjustmentService ledgerAdjustmentService;

    @Mock
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Mock
    private LoanClosureService loanClosureService;

    @InjectMocks
    private LoanStatusServiceImpl loanStatusService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void processLoanForeClosure() {
        LoanClosureDTO loanClosureDTO = new LoanClosureDTO();
        loanClosureDTO.setPaymentType(PaymentType.FORECLOSURE.name());
        loanClosureDTO.setForeClosure(true);
        LendingPaymentSchedule lendingPaymentSchedule = new LendingPaymentSchedule();
        lendingPaymentSchedule.setId(1L);
        loanClosureDTO.setActiveLoan(lendingPaymentSchedule);
        loanStatusService.processLoanClosure(loanClosureDTO);
        verify(loanClosureService, times(1)).foreClosureLoan(any(), any(), any());
    }

    @Test
    void waiverSettleLoan() {
        // Prepare test data
        LendingPaymentSchedule activeLoan = new LendingPaymentSchedule();
        activeLoan.setDueInterest(10d);
        activeLoan.setOtherCharges(10d);
        activeLoan.setInterest(10d);
        activeLoan.setPaidInterest(10d);
        Double amount = 1000.0;
        String bankRefNo = "123456";
        String source = "Test";
        String terminalOrderId = "987654";
        Double excessCollectionBalance = 0.0;
        List<LendingCollectionExcess> lendingCollectionExcessList = Collections.emptyList();
        // Call method under test
        loanStatusService.waiverSettleLoan(activeLoan, amount, bankRefNo, source, terminalOrderId, excessCollectionBalance, lendingCollectionExcessList);
        // Verify that the appropriate methods were called
        verify(ledgerAdjustmentService, times(2)).createLendingLedger(any(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyString(), anyString(), anyString(), anyDouble(), anyDouble());
        verify(ledgerAdjustmentService, times(0)).createLendingLedgerForExcessCollectionOnForeclosure(any(), any());
        verify(ledgerAdjustmentService, times(0)).settleExcessCollectionBalance(any(), any());
        verify(lendingPaymentScheduleDao, times(1)).save(any());
    }
    @Test
    void waiverSettleLoanIfExcessCollectionBalancePresent() {
        // Prepare test data
        LendingPaymentSchedule activeLoan = new LendingPaymentSchedule();
        activeLoan.setDueInterest(10d);
        activeLoan.setOtherCharges(10d);
        activeLoan.setInterest(10d);
        activeLoan.setPaidInterest(10d);
        activeLoan.setPaidAmount(10d);
        Double amount = 1000.0;
        String bankRefNo = "123456";
        String source = "Test";
        String terminalOrderId = "987654";
        Double excessCollectionBalance = 10.0;
        List<LendingCollectionExcess> lendingCollectionExcessList = Collections.emptyList();
        // Call method under test
        loanStatusService.waiverSettleLoan(activeLoan, amount, bankRefNo, source, terminalOrderId, excessCollectionBalance, lendingCollectionExcessList);
        // Verify that the appropriate methods were called
        verify(ledgerAdjustmentService, times(2)).createLendingLedger(any(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyString(), anyString(), anyString(), anyDouble(), anyDouble());
        verify(ledgerAdjustmentService, times(1)).createLendingLedgerForExcessCollectionOnForeclosure(any(), any());
        verify(ledgerAdjustmentService, times(1)).settleExcessCollectionBalance(any(), any());
        verify(lendingPaymentScheduleDao, times(1)).save(any());
    }
}