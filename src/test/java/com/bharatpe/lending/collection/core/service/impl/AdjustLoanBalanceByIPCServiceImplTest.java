package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdjustLoanBalanceByIPCServiceImplTest {


    @InjectMocks
    private AdjustLoanBalanceByIPCServiceImpl adjustLoanBalanceByIPCService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }
    @Test
    void testAdjustLoanBalance() {
        double amount = 1700.0;
        double dueInterest = 500.0;
        double duePrinciple = 700.0;
        double duePenalty = 200.0;
        double dueOtherCharges = 300.0;
        double dueAmount = 1700;
        double paidAmount = 0;
        double paidOtherCharges = 0;
        LendingPaymentSchedule activeLoan = new LendingPaymentSchedule();
        activeLoan.setId(123L);
        activeLoan.setDueInterest(dueInterest);
        activeLoan.setDuePenalty(duePenalty);
        activeLoan.setDuePrinciple(duePrinciple);
        activeLoan.setDueOtherCharges(dueOtherCharges);
        activeLoan.setDueAmount(dueAmount);
        activeLoan.setPaidAmount(paidAmount);
        activeLoan.setPaidOtherCharges(paidOtherCharges);

        PaymentCalculation result = adjustLoanBalanceByIPCService.adjustLoanBalance(activeLoan, amount);

        assertEquals(amount, result.getReceived());
        assertEquals(1700.0, result.getUsed());
        assertEquals(0.0, result.getBalance());
        assertEquals(700.0, result.getPrincipleSettled());
        assertEquals(500.0, result.getInterestSettled());
        assertEquals(200.0, result.getPenaltySettled());
        assertEquals(300.0, result.getChargesSettled());
    }
    @Test
    void testAdjustLoanBalanceAmountIsGreaterThanDueAmount() {
        double amount = 2000.0;
        double dueInterest = 500.0;
        double duePrinciple = 700.0;
        double duePenalty = 200.0;
        double dueOtherCharges = 300.0;
        double dueAmount = 1700;
        double paidAmount = 0;
        double paidOtherCharges = 0;
        LendingPaymentSchedule activeLoan = new LendingPaymentSchedule();
        activeLoan.setId(123L);
        activeLoan.setDueInterest(dueInterest);
        activeLoan.setDuePenalty(duePenalty);
        activeLoan.setDuePrinciple(duePrinciple);
        activeLoan.setDueOtherCharges(dueOtherCharges);
        activeLoan.setDueAmount(dueAmount);
        activeLoan.setPaidAmount(paidAmount);
        activeLoan.setPaidOtherCharges(paidOtherCharges);

        PaymentCalculation result = adjustLoanBalanceByIPCService.adjustLoanBalance(activeLoan, amount);

        assertEquals(amount, result.getReceived());
        assertEquals(1700.0, result.getUsed());
        assertEquals(300.0, result.getBalance());
        assertEquals(700.0, result.getPrincipleSettled());
        assertEquals(500.0, result.getInterestSettled());
        assertEquals(200.0, result.getPenaltySettled());
        assertEquals(300.0, result.getChargesSettled());
    }
    @Test
    void testAdjustLoanBalanceIsLessThanDueAmount() {
        double amount = 1400.0;
        double dueInterest = 500.0;
        double duePrinciple = 700.0;
        double duePenalty = 200.0;
        double dueOtherCharges = 300.0;
        double dueAmount = 1700;
        double paidAmount = 0;
        double paidOtherCharges = 0;
        LendingPaymentSchedule activeLoan = new LendingPaymentSchedule();
        activeLoan.setId(123L);
        activeLoan.setDueInterest(dueInterest);
        activeLoan.setDuePenalty(duePenalty);
        activeLoan.setDuePrinciple(duePrinciple);
        activeLoan.setDueOtherCharges(dueOtherCharges);
        activeLoan.setDueAmount(dueAmount);
        activeLoan.setPaidAmount(paidAmount);
        activeLoan.setPaidOtherCharges(paidOtherCharges);

        PaymentCalculation result = adjustLoanBalanceByIPCService.adjustLoanBalance(activeLoan, amount);

        assertEquals(amount, result.getReceived());
        assertEquals(1400.0, result.getUsed());
        assertEquals(0.0, result.getBalance());
        assertEquals(700.0, result.getPrincipleSettled());
        assertEquals(500.0, result.getInterestSettled());
        assertEquals(200.0, result.getPenaltySettled());
        assertEquals(0.0, result.getChargesSettled());
    }

}