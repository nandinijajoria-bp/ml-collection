package com.bharatpe.lending.collection.core.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;

public interface LoanClosureService {

    void closeLoanAndUpdateLender(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger, Long orderId);

    void updateForeclosureChargesStatus(String status, Long orderId);

}
