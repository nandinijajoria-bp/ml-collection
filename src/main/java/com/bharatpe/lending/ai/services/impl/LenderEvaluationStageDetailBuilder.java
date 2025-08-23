package com.bharatpe.lending.ai.services.impl;

import com.bharatpe.lending.ai.dto.stageDetailResponse.LenderEvaluationStageDetail;
import com.bharatpe.lending.ai.services.ILoanStageDetailBuilder;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.query.dao.LendingApplicationLenderDetailsDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationLenderDetailsSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LenderEvaluationStageDetailBuilder implements ILoanStageDetailBuilder {
    private final LendingApplicationLenderDetailsDaoSlave laldDaoSlave;
    @Override
    public Object build(LendingApplicationSlave lendingApplication, LendingApplicationDetails lendingApplicationDetails) {
        LendingApplicationLenderDetailsSlave lald = laldDaoSlave.findTop1ByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender());
        LenderEvaluationStageDetail lenderEvaluationStageDetail = new LenderEvaluationStageDetail();
        if(lald==null){
            lenderEvaluationStageDetail.setStatus("PENDING");
            return lenderEvaluationStageDetail;
        }
        lenderEvaluationStageDetail.setStatus(lald.getStatus());
        lenderEvaluationStageDetail.setKycStatus(lald.getKycStatus());
        lenderEvaluationStageDetail.setBreStatus(lald.getBreStatus());
        return lenderEvaluationStageDetail;
    }
}
