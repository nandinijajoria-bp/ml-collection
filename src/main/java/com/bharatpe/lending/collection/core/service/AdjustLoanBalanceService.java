package com.bharatpe.lending.collection.core.service;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;

public interface AdjustLoanBalanceService {

    PaymentCalculation adjustLoanBalance(LendingPaymentSchedule loan, double amount);
}
