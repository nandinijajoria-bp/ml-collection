package com.bharatpe.lending.ai.services.impl;

import com.bharatpe.lending.ai.dto.StageDetail;
import com.bharatpe.lending.ai.dto.stageDetailResponse.KfsStageDetail;
import com.bharatpe.lending.ai.services.ILoanStageDetailBuilder;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.exception.CustomLendingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class KfsStageDetailBuilder implements ILoanStageDetailBuilder {
    private final LendingKfsDao lendingKfsDao;
    @Override
    public StageDetail buildStageResponse(LendingApplicationSlave lendingApplication, LendingApplicationDetails lendingApplicationDetails) {
        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
        if(Objects.isNull(lendingKfs)){
            throw new CustomLendingException(HttpStatus.UNPROCESSABLE_ENTITY, "KFS details not found");
        }
        KfsStageDetail kfsStageDetail = new KfsStageDetail(lendingKfs);
        StageDetail stageDetail = new StageDetail();
        stageDetail.setKfsStageDetail(kfsStageDetail);
        return stageDetail;
    }
}
