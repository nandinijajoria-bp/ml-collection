package com.bharatpe.lending.ai.services.impl;

import com.bharatpe.lending.ai.dto.LedgerApiResponse;
import com.bharatpe.lending.ai.dto.StageDetail;
import com.bharatpe.lending.ai.services.AILedgerService;
import com.bharatpe.lending.ai.services.ILoanStageDetailBuilder;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PostDisbursalStageDetailBuilder implements ILoanStageDetailBuilder {

    private final AILedgerService aiLedgerService;

    @Override
    public StageDetail buildStageResponse(LendingApplicationSlave lendingApplication, LendingApplicationDetails lendingApplicationDetails) {
        StageDetail stageDetail = new StageDetail();
        LedgerApiResponse apiResponse = aiLedgerService.fetchLedger(lendingApplication.getMerchantId(), null);
        stageDetail.setLedgerApiResponse(apiResponse);
        return stageDetail;
    }
}
