package com.bharatpe.lending.collection.core.service;

import com.bharatpe.common.entities.LendingPaymentSchedule;

public interface IPenaltyService {

    int getPenaltyVersion(LendingPaymentSchedule lendingPaymentSchedule);

}
