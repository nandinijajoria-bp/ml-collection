package com.bharatpe.lending.loanV3.services.associations.piramal;

import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CommonService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Lazy
    @Autowired
    NbfcUtils nbfcUtils;

    public void manageApplicationState(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        if (lenderAssociationDetailsDto.isManageState()) {
            lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetailsDao.save(lenderAssociationDetailsDto.getLendingApplicationLenderDetails()));
        }
    }

    public void modfifyApplicationLender(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto,  LenderAssociationStatus lenderAssociationStatus) {
        log.info("request for modify lender received for {}", lenderAssociationDetailsDto.getApplicationId());
        if (lenderAssociationDetailsDto.isModifyLender()) {
            log.info("modifying lender !");
            nbfcUtils.modifyLender(lenderAssociationDetailsDto.getLendingApplication(), lenderAssociationDetailsDto.getLendingApplicationLenderDetails(), lenderAssociationStatus);
        }
    }

    public void manageApplicationStateAndModifyLender(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, LenderAssociationStatus lenderAssociationStatus) {
        manageApplicationState(lenderAssociationDetailsDto);
        modfifyApplicationLender(lenderAssociationDetailsDto, lenderAssociationStatus);
    }

    public void manageApplicationStateAndPushToNextStage(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        String currStage =  lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getStage();
        LenderAssociationStages nextStage =
                LenderAssociationStageFactoryV2.getNextStage(Lender.valueOf(lenderAssociationDetailsRequest.getLendingApplication().getLender()),
                        LenderAssociationStages.valueOf(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getStage()));
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setStage(nextStage.name());
        manageApplicationState(lenderAssociationDetailsRequest);
        nbfcUtils.pushApplicationToNextStage(lenderAssociationDetailsRequest.getApplicationId(),
                lenderAssociationDetailsRequest.getLendingApplication().getLender(),
                currStage,
                LenderAssociationStageFactoryV2.autoInvokeNextStage(Lender.valueOf(lenderAssociationDetailsRequest.getLendingApplication().getLender()), LenderAssociationStages.valueOf(currStage)));
    }
}
