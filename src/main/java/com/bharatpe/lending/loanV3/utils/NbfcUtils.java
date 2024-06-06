package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssignment;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.service.impl.LenderAssignService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class NbfcUtils {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    ILenderAssignment iLenderAssignment;

    @Autowired
    LenderAssignService lenderAssignService;

    @Autowired
    LenderAssociationStageFactory lenderAssociationStageFactory;

    @Autowired
    LoanUtil loanUtil;

    @Value("${lender.change.enabled:false}")
    private Boolean enableLenderChange;

    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Async
    public void modifyLender(LendingApplication lendingApplication, LendingApplicationLenderDetails existingLendingApplicationLenderDetails, LenderAssociationStatus lenderAssociationStatus) {
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            lendingApplicationDetails = new LendingApplicationDetails();
            lendingApplicationDetails.setApplicationId(lendingApplication.getId());
            lendingApplicationDetails.setEdiModel(LoanUtil.getEdiModal(lendingApplication).name());
            lendingApplicationDetails.setStage(LenderAssociationStages.LENDER_CHANGE.name());
        }
        log.info("changing lender for the application {}", lendingApplication.getId());
        // restore this
        if (enableLenderChange) {
            existingLendingApplicationLenderDetails.setStatus(Status.INACTIVE.name());
        }
        if (LenderAssociationStages.BRE.name().equalsIgnoreCase(existingLendingApplicationLenderDetails.getStage())) {
            existingLendingApplicationLenderDetails.setBreStatus(lenderAssociationStatus.name());
        } else if (LenderAssociationStages.KYC.name().equalsIgnoreCase(existingLendingApplicationLenderDetails.getStage())) {
            existingLendingApplicationLenderDetails.setKycStatus(lenderAssociationStatus.name());
        } else if (LenderAssociationStages.SANCTION_WRAPPER.name().equalsIgnoreCase(existingLendingApplicationLenderDetails.getStage())) {
            existingLendingApplicationLenderDetails.setSanctionStatus(lenderAssociationStatus.name());
        }
        lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);
        // TODO: 08/11/22 todo final integrate with lender change svc (set lender in app and modify app details params)
//        Lender lender = iLenderAssignment.changeLender(lendingApplication.getId());
//        Lender lender = Lender.LDC;
//        lendingApplication.setLender(lender.name());
//        lendingApplicationDao.save(lendingApplication);
//        // TODO: 08/11/22  todo final these can be removed later
//        if (!lendingApplicationDetails.getEdiModel().equalsIgnoreCase(LenderOffDays.valueOf(lender.name()).getEdiModel().name())) {
//            lendingApplicationDetails.setEdiModel(LenderOffDays.valueOf(lender.name()).getEdiModel().name());
//            lendingApplicationDetails.setEdiModelModified(Boolean.TRUE);
//        }
//        lendingApplicationDetailsDao.save(lendingApplicationDetails);
        // TODO: 12/12/22 todo final uncomment this
        if (enableLenderChange) {
            if(!Arrays.asList(LendingViewStates.SHOP_PICTURES_PAGE.name(), LendingViewStates.KYC_PAGE, LendingViewStates.LENDER_EVALUATION_PAGE).contains(lendingApplicationDetails.getApplicationViewState())
                    || !ObjectUtils.isEmpty(lendingApplication.getAgreementAt())) {
                log.info("skipping lender change and rejecting application as agreement already done / lendingViewState {} for application is not correct for applicationId {}", lendingApplicationDetails.getApplicationViewState(), lendingApplication.getId());
                lendingApplication.setStatus("rejected");
                lendingApplicationDao.save(lendingApplication);
                lendingApplicationServiceV2.evictCache(lendingApplication.getMerchantId());
                return;
            }
            Lender modifiedLender = lenderAssignService.modifyLender(lendingApplication.getId());
            if(ObjectUtils.isEmpty(modifiedLender)) {
                log.info("modifiedLender is null, rejecting application for applicationId {}", lendingApplication.getId());
                lendingApplication.setStatus("rejected");
                lendingApplicationDao.save(lendingApplication);
                lendingApplicationServiceV2.evictCache(lendingApplication.getMerchantId());
                return;
            }
            if (Arrays.asList(LenderAssociationStatus.SANCTION_FAILED.name(), LenderAssociationStatus.SANC_CALLBACK_CLIENT_FAILURE.name(), LenderAssociationStatus.SANC_REQUEST_CLIENT_FAILURE.name()).contains(lenderAssociationStatus.name())) {

                log.info("modified lender for applicationId : {} and lenderAssociationStatus : {}", lendingApplication.getId(), lenderAssociationStatus.name());
                loanUtil.putApplicationInResignAndRenach(lendingApplication, modifiedLender.name());
            }

            pushApplicationToNextStage(lendingApplication.getId(), modifiedLender.name(), LenderAssociationStages.INIT.name(), Boolean.TRUE);
        }
    }

    public void pushApplicationToNextStage(Long applicationId, String lender, String lenderAssociationStage, Boolean autoInvoke) {
        if (LenderAssociationStages.COMPLETED.name().equalsIgnoreCase(lenderAssociationStage)) {
            log.info("status completed for this application {}",applicationId);
            return;
        }
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            // TODO: 10/11/22 todo final remove this creation
//            log.info("lending application details missing for application {}", applicationId);
//            return;
            lendingApplicationDetails = new LendingApplicationDetails();
            lendingApplicationDetails.setApplicationId(applicationId);
            lendingApplicationDetails.setEdiModel(EdiModel.SEVEN_DAY_MODEL.name());
        }
        LenderAssociationStages nextStage = nextStage(Lender.valueOf(lender), LenderAssociationStages.valueOf(lenderAssociationStage));
        lendingApplicationDetails.setStage(nextStage.name());
        lendingApplicationDetails.setLenderAssc(Boolean.TRUE);
        lendingApplicationDetailsDao.save(lendingApplicationDetails);
        log.info("stage updated in app details for application {}", applicationId);
        if (autoInvoke) {
            ILenderAssociationService iLenderAssociationService =
                    lenderAssociationStageFactory.getStageAssociatedLenderService(nextStage.name()).getLenderAssociationService(lender);
            Map<String, Object> args = new HashMap<String, Object>() {{
                put("requestId", MDC.get("requestId"));
            }};
            iLenderAssociationService.invoke(applicationId,args);
            log.info("application {} successfully pushed to the next stage {}", applicationId, nextStage.name());
            }
        }

    private LenderAssociationStages nextStage(Lender lender, LenderAssociationStages stage) {
        switch (lender) {
            case USFB :
            case TRILLIONLOANS:
            case MUTHOOT:
            case CAPRI:
                return LenderAssociationStageFactoryV2.getNextStage(lender, stage);
            case ABFL :
            case PIRAMAL:
            default:
                return LenderAssociationStageFactory.getNextStage(lender, stage);
        }
    }

}
