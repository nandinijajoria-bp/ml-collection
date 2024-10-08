package com.bharatpe.lending.loanV3.services;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.ModifyLenderDto;
import com.bharatpe.lending.loanV3.dto.PushApplicationNextStageDto;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Service
public class ModifyStageService {

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    private NbfcUtils nbfcUtils;

    public void modifyLender(ModifyLenderDto modifyLenderDto) {

        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(modifyLenderDto.getId());
        if (!lendingApplication.isPresent()) {
            log.info("no application exists for {}", modifyLenderDto.getId());
            return;
        }
        LendingApplicationLenderDetails existingLendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(lendingApplication.get().getId(), Status.ACTIVE.name());
        if (ObjectUtils.isEmpty(existingLendingApplicationLenderDetails) || !Arrays.asList(Lender.ABFL.name(),Lender.PIRAMAL.name(), Lender.TRILLIONLOANS.name(), Lender.MUTHOOT.name(), Lender.CAPRI.name(), Lender.PAYU.name()).contains(existingLendingApplicationLenderDetails.getLender())) {
            log.info("no existingLendingApplicationLenderDetails exists for {}", modifyLenderDto.getId());
            return;
        }
        LenderAssociationStatus lenderAssociationStatus = LenderAssociationStatus.valueOf(modifyLenderDto.getStatus());
        log.info("Fetched the required details for modify lender {},{},{}",lendingApplication,
                existingLendingApplicationLenderDetails,lenderAssociationStatus);
        nbfcUtils.modifyLender(lendingApplication.get(), existingLendingApplicationLenderDetails, lenderAssociationStatus);
    }

    @Async
    public void pushToNextStageAsync(PushApplicationNextStageDto pushApplicationNextStageDto) {
        nbfcUtils.pushApplicationToNextStage(pushApplicationNextStageDto.getApplicationId(),pushApplicationNextStageDto.getLender(),
                pushApplicationNextStageDto.getLenderAssociationStage(),pushApplicationNextStageDto.getAutoInvoke());
    }
}