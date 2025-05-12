package com.bharatpe.lending.collection.core.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.LoanClosureDTO;

public interface LoanClosureService {

    void closeLoanAndUpdateLender(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger, LoanClosureDTO loanClosureDTO);

    void updateForeclosureChargesStatus(String status, Long orderId);

}
