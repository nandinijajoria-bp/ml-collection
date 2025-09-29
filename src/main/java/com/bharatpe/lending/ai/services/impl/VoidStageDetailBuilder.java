package com.bharatpe.lending.ai.services.impl;

import com.bharatpe.lending.ai.dto.StageDetail;
import com.bharatpe.lending.ai.services.ILoanStageDetailBuilder;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import org.springframework.stereotype.Service;

@Service
public class VoidStageDetailBuilder implements ILoanStageDetailBuilder {
    @Override
    public StageDetail buildStageResponse(LendingApplicationSlave lendingApplication, LendingApplicationDetails lendingApplicationDetails) {
        return null;
    }
}
