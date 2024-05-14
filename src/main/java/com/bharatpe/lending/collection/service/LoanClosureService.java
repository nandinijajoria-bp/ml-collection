package com.bharatpe.lending.collection.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;

public interface LoanClosureService {

    void closeLoanAndUpdateLender(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger, Long orderId);

    void foreClosureLoan(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger, Long orderId);

    void postClosureStatusToLender(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger, Long orderId);
}
