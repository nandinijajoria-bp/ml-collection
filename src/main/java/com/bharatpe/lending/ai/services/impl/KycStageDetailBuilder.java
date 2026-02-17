package com.bharatpe.lending.ai.services.impl;

import com.bharatpe.lending.ai.dto.StageDetail;
import com.bharatpe.lending.ai.dto.stageDetailResponse.KycStageDetail;
import com.bharatpe.lending.ai.services.ILoanStageDetailBuilder;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycStageDetailBuilder implements ILoanStageDetailBuilder {
    private final LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;
    @Override
    public StageDetail buildStageResponse(LendingApplicationSlave lendingApplication,
                                          LendingApplicationDetails lendingApplicationDetails) {
        LendingApplicationKycDetails lendingApplicationKycDetails =
                lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
        KycStageDetail kycStageDetail = new KycStageDetail(lendingApplicationKycDetails);
        StageDetail stageDetail = new StageDetail();
        stageDetail.setKycStageDetail(kycStageDetail);
        return stageDetail;
    }
}
