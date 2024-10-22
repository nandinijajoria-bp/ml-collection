package com.bharatpe.lending.loanV3.services.associations.piramal;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Service
@Slf4j
public class CommonService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Lazy
    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    CommonUtil commonUtil;

    public void manageApplicationState(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        if (lenderAssociationDetailsDto.isManageState()) {
            log.info("setting stage manageApplicationState {}", lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getStage());
            lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetailsDao.save(lenderAssociationDetailsDto.getLendingApplicationLenderDetails()));
        }
    }

    public void modfifyApplicationLender(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, LenderAssociationStatus lenderAssociationStatus) {
        log.info("request for modify lender received for {}", lenderAssociationDetailsDto.getApplicationId());
        if (lenderAssociationDetailsDto.isModifyLender()) {
            log.info("modifying lender !");
            nbfcUtils.modifyLender(lenderAssociationDetailsDto.getLendingApplication(), lenderAssociationDetailsDto.getLendingApplicationLenderDetails(), lenderAssociationStatus);
        }
    }

    public void manageApplicationStateAndModifyLender(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, LenderAssociationStatus lenderAssociationStatus) {
        manageApplicationState(lenderAssociationDetailsDto);
        if (LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsDto.getLendingApplication().getLoanType()) && Lender.TRILLIONLOANS.name().equalsIgnoreCase(lenderAssociationDetailsDto.getLendingApplication().getLender()))
            manageApplicationStateAndRejectApplication(lenderAssociationDetailsDto);
        else
            modfifyApplicationLender(lenderAssociationDetailsDto, lenderAssociationStatus);
    }

    public void manageApplicationStateAndPushToNextStage(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        String currStage = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getStage();
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

    public void manageApplicationStateAndRejectApplication(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        manageApplicationState(lenderAssociationDetailsRequest);
        rejectApplication(lenderAssociationDetailsRequest.getLendingApplication(), lenderAssociationDetailsRequest.getLendingApplicationLenderDetails());
    }

    public void rejectApplication(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        log.info("rejecting application due to {} for applicationId {}", lendingApplicationLenderDetails.getLeadStatus(), lendingApplication.getId());
        if (!ObjectUtils.isEmpty(lendingApplication)) {
            String oldStatus = lendingApplication.getStatus();
            lendingApplication.setStatus("rejected");
            lendingApplication.setManualKyc("rejected");
            lendingApplication.setManualKycReason(lendingApplication.getLender() + "_" + lendingApplicationLenderDetails.getLeadStatus());
            lendingApplicationDao.save(lendingApplication);
            commonUtil.saveApplicationRejectionAudit(lendingApplication, "rejected", oldStatus, "APP_STATUS", lendingApplication.getManualKyc());
        }
        if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            lendingApplicationLenderDetails.setStatus(Status.INACTIVE.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        }
    }
}
