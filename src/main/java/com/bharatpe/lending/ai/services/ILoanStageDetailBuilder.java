package com.bharatpe.lending.ai.services;

import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;

public interface ILoanStageDetailBuilder {
    Object build(LendingApplicationSlave lendingApplication,
                              LendingApplicationDetails lendingApplicationDetails);
}
