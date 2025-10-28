package com.bharatpe.lending.ai.services.impl;

import com.bharatpe.lending.ai.dto.StageDetail;
import com.bharatpe.lending.ai.dto.stageDetailResponse.LenderEvaluationStageDetail;
import com.bharatpe.lending.ai.services.ILoanStageDetailBuilder;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.query.dao.LendingApplicationLenderDetailsDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationLenderDetailsSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LenderEvaluationStageDetailBuilder implements ILoanStageDetailBuilder {
    private final LendingApplicationLenderDetailsDaoSlave laldDaoSlave;
    @Override
    public StageDetail buildStageResponse(LendingApplicationSlave lendingApplication,
                                          LendingApplicationDetails lendingApplicationDetails) {
        LendingApplicationLenderDetailsSlave lald = laldDaoSlave
                .findTop1ByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender());
        LenderEvaluationStageDetail lenderEvaluationStageDetail = null;
        if(lald==null){
            lenderEvaluationStageDetail = new LenderEvaluationStageDetail();
            lenderEvaluationStageDetail.setStatus("PENDING");
        }else {
            lenderEvaluationStageDetail = new LenderEvaluationStageDetail(lald);
        }
        StageDetail stageDetail = new StageDetail();
        stageDetail.setLenderEvaluationStageDetail(lenderEvaluationStageDetail);
        return stageDetail;
    }
}
