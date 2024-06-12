package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.service.LoanClosurePostingService;
import com.bharatpe.lending.collection.core.service.LoanClosureService;
import com.bharatpe.lending.common.dao.LoanForeClosureChargesDao;
import com.bharatpe.lending.common.service.SherlocLoanStatusChangeService;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.Lender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Date;

import static org.mockito.Mockito.*;

class LoanClosureServiceImplTest {

    @Mock
    private LendingPaymentSchedule loan;

    @Mock
    private LendingLedger lendingLedger;

    @Mock
    private LoanClosurePostingService loanClosurePostingService;

    @Mock
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Mock
    private LoanForeClosureChargesDao loanForeClosureChargesDao;
    @Mock
    private SherlocLoanStatusChangeService sherlocLoanStatusChangeService;

    @InjectMocks
    private LoanClosureServiceImpl loanClosureService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testCloseLoanAndUpdateLender() {
        // Arrange
        LendingPaymentSchedule loan = new LendingPaymentSchedule();
        loan.setId(1l);
        loan.setNbfc(Lender.TRILLIONLOANS.name());


        // Act
        loanClosureService.closeLoanAndUpdateLender(loan, lendingLedger, 123L);


        verify(lendingPaymentScheduleDao, times(1)).save(loan);
        verify(loanClosurePostingService, times(1)).sendForeclosureEventTrillionLoans(any(),any(),any());
        verify(sherlocLoanStatusChangeService, times(1)).pushLoanStatusChangeEventToKafka(any(),any());
    }

//    @Test
//    void testForeClosureLoan() {
//        // Arrange
//        when(loan.getId()).thenReturn(1L);
//        when(loan.getLoanAmount()).thenReturn(1000.0);
//        when(loan.getDueInterest()).thenReturn(100.0);
//        when(loan.getPaidInterest()).thenReturn(50.0);
//        when(loan.getDuePenalty()).thenReturn(20.0);
//        when(loan.getOtherCharges()).thenReturn(10.0);
//        when(loan.getTotalPayableAmount()).thenReturn(1180.0);
//        when(loan.getPaidAmount()).thenReturn(1180.0);
//
//        // Act
//        loanClosureService.foreClosureLoan(loan, lendingLedger, 123L);
//
//        // Assert
//        verify(loan, times(1)).setStatus("CLOSED");
//        verify(loan, times(1)).setDueAmount(0.0);
//        verify(loan, times(1)).setDueInterest(0.0);
//        verify(loan, times(1)).setDuePrinciple(0.0);
//        verify(loan, times(1)).setDueOtherCharges(0.0);
//        verify(loan, times(1)).setDuePenalty(0.0);
//        verify(loan, times(1)).setClosingDate(any(Date.class));
//        verify(lendingPaymentScheduleDao, times(1)).save(loan);
//    }

}