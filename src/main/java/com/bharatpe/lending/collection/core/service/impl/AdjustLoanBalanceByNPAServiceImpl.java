package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;
import com.bharatpe.lending.collection.core.service.AdjustLoanBalanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;


@Service
@Slf4j
public class AdjustLoanBalanceByNPAServiceImpl implements AdjustLoanBalanceService {


    @Autowired
    AdjustLoanBalanceByEdiByEdiServiceImpl ediService;

    @Override
    public PaymentCalculation adjustLoanBalance(LendingPaymentSchedule loan, double amount) {
        return ediService.adjustEdiSchedule(loan, amount, true);
    }

}
