package com.bharatpe.lending.collection.core.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;

public interface LoanClosurePostingService {
    void postForeclosureReceiptPiramal(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger);

    void sendForeclosureEvent(Long applicationId, String mobile, LendingLedger lendingLedger);

    void postForeclosureReceipt(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger);

    void sendForeclosureEventTrillionLoans(Long applicationId, LendingLedger lendingLedger, Long orderId);


    void sendForeclosureChargesEventLiquiLoans(long applicationId, long loanId, long lendingLedgerId, String lender, long orderId);

    void sendForeclosureEventPayu(Long applicationId, LendingLedger lendingLedger, Long orderId);

}
