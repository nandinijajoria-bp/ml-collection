package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.dao.LendingEDIScheduleDao;
import com.bharatpe.common.entities.LendingEDISchedule;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.LoanPaymentDetailDTO;
import com.bharatpe.lending.collection.core.service.LoanPaymentLedgerAdjustmentService;
import com.bharatpe.lending.collection.core.service.LoanStatusService;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.util.LoanUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoanPaymentServiceImplTest {
    @Mock
    private AdjustLoanBalanceByIPCServiceImpl adjustLoanBalanceByIPCService;

    @Mock
    private AdjustLoanBalanceByEdiByEdiServiceImpl adjustLoanBalanceByEdiByEdiService;

    @Mock
    private AdjustLoanBalanceByNPAServiceImpl adjustLoanBalanceByNPAService;

    @Mock
    private LoanStatusService loanStatusService;

    @Mock
    private LoanPaymentLedgerAdjustmentService ledgerAdjustmentService;
    @Mock
    private LoanUtil loanUtil;
    @Mock
    private LendingEDIScheduleDao lendingEDIScheduleDao;
    @Mock
    private LendingCollectionExcessDao lendingCollectionExcessDao;
    @Mock
    private LoanPaymentOrderDao loanPaymentOrderDao;
    @Mock
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @InjectMocks
    private LoanPaymentServiceImpl loanPaymentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }
    @Test
    void testAdjustMoneyForIPC() {
        double dueAmount = 1000;
        double ediAmount = 500;
        Integer principalDueAmount = 10000;
        List<LendingEDISchedule> lendingEDISchedules = new ArrayList<>();
        List<LendingCollectionExcess> lendingCollectionExcessList = new ArrayList<>();
        LendingPaymentSchedule loan = new LendingPaymentSchedule();
        loan.setDueAmount(dueAmount);
        loan.setEdiAmount(ediAmount);
        loan.setSettlementMechanism("IPC");
        LoanPaymentDetailDTO paymentDetailDTO = new LoanPaymentDetailDTO();
        paymentDetailDTO.setOtherAmount(1000);
        paymentDetailDTO.setAdjustExcessNach(false);
        paymentDetailDTO.setOrderId(1L);

        Mockito.when(loanUtil.getForeclosureAmount(loan)).thenReturn(principalDueAmount);
        Mockito.when(lendingEDIScheduleDao.getByLoanIdAndEdiType(loan.getId(),"EDIHOLIDAY")).thenReturn(lendingEDISchedules);
        Mockito.when(lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(loan.getMerchantId(),loan.getId(),"ACTIVE")).thenReturn(lendingCollectionExcessList);
        Mockito.when(loanPaymentOrderDao.findByOrderId(String.valueOf(paymentDetailDTO.getOrderId()))).thenReturn(null);
        LendingPaymentSchedule adjustedLoan = loanPaymentService.adjustMoney(loan, paymentDetailDTO);

        verify(ledgerAdjustmentService, times(1)).adjustLendingLedger(any(), any(),any(),any(),any(),any(),any());
        verify(lendingPaymentScheduleDao, times(1)).save(any());
    }

}