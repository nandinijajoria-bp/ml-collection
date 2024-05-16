package com.bharatpe.lending.collection.core.service;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.dto.ResponseDTO;

public interface PenaltyFeeService{

    double createPenaltyFee(LendingPaymentSchedule activeLoan);

    ResponseDTO applyPenaltyWaiver(Long applicationId, Double amount);
}
