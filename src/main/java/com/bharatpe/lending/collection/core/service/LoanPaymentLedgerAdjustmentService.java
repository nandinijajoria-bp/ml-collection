package com.bharatpe.lending.collection.core.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.LoanPaymentDetailDTO;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.entity.LoanPaymentOrder;

import java.util.List;

public interface LoanPaymentLedgerAdjustmentService {
    LendingCollectionExcess adjustNachLedger(LendingCollectionExcess lendingCollectionExcess, PaymentCalculation paymentAdjusted);

    void adjustAdvanceEdiLedger(LendingPaymentSchedule loan, PaymentCalculation advanceAdjustment);

    void adjustLendingLedger(LendingPaymentSchedule loan, PaymentCalculation paymentAdjustment, LoanPaymentOrder order, String desc, String adjustmentMode, String transferType, String bankReferenceNo);

    void updateCollectionAuditAndOrder(LendingLedger lendingLedger, LoanPaymentOrder order);

    LendingLedger createLendingLedger(LendingPaymentSchedule loan, PaymentCalculation paymentAdjusted, String description, String adjustmentMode, String transferType, String terminalOrderId);

    LendingLedger createLendingLedger(LendingPaymentSchedule loan, Double amount, Double principle,
                                      Double interest, String description, String source, String transferType, String terminalOrderId, Double penaltyFee, Double charges);

    LoanPaymentOrder createLoanPaymentOrder(LendingPaymentSchedule loan, double orderAmount, String paymentReferenceNo, String status, String source);

    void markOrderSuccess(LoanPaymentOrder order);

    void createLendingLedgerForExcessCollectionOnForeclosure(LendingPaymentSchedule activeLoan, List<LendingCollectionExcess> lendingCollectionExcessList);

    void settleExcessCollectionBalance(Long loanId, List<LendingCollectionExcess> lendingCollectionExcessList);
}
