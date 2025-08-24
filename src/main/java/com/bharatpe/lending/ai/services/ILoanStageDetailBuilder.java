package com.bharatpe.lending.ai.services;

import com.bharatpe.lending.ai.dto.StageDetail;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;

public interface ILoanStageDetailBuilder {
    StageDetail buildStageResponse(LendingApplicationSlave lendingApplication,
                                   LendingApplicationDetails lendingApplicationDetails);
}
