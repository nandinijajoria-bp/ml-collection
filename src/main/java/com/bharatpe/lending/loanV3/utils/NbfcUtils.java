package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LenderDisbursalLimitsDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.entity.LendingLenderQuota;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.EdiModel;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssignment;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.service.impl.LenderAssignService;
import com.bharatpe.lending.util.CommonUtil;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    @Lazy
    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Lazy
    @Autowired
    AssociationServiceUtil associationServiceUtil;

    @Lazy
    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonUtil commonUtil;

    @Autowired
    LenderDisbursalLimitsDao lenderDisbursalLimitsDao;

    @Async
    public void modifyLender(LendingApplication lendingApplication, LendingApplicationLenderDetails existingLendingApplicationLenderDetails, LenderAssociationStatus lenderAssociationStatus) {
        if(Lender.ABFL.name().equalsIgnoreCase(lendingApplication.getLender()) && LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
            log.info("restricting lender change for ABFL Topup application : {}", lendingApplication.getId());
            return;
        }

        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            lendingApplicationDetails = new LendingApplicationDetails();
            lendingApplicationDetails.setApplicationId(lendingApplication.getId());
            lendingApplicationDetails.setEdiModel(LoanUtil.getEdiModal(lendingApplication).name());
        }
        lendingApplicationDetails.setLenderAssc(Boolean.FALSE);
        lendingApplicationDetails.setStage(LenderAssociationStages.LENDER_CHANGE.name());
        lendingApplicationDetailsDao.save(lendingApplicationDetails);
        if (enableLenderChange) {
            existingLendingApplicationLenderDetails.setStatus(Status.INACTIVE.name());
        }
        if (LenderAssociationStages.BRE.name().equalsIgnoreCase(existingLendingApplicationLenderDetails.getStage())) {
            existingLendingApplicationLenderDetails.setBreStatus(lenderAssociationStatus.name());
        } else if (LenderAssociationStages.KYC.name().equalsIgnoreCase(existingLendingApplicationLenderDetails.getStage())) {
            existingLendingApplicationLenderDetails.setKycStatus(lenderAssociationStatus.name());
        } else if (LenderAssociationStages.SANCTION_WRAPPER.name().equalsIgnoreCase(existingLendingApplicationLenderDetails.getStage())) {
            existingLendingApplicationLenderDetails.setSanctionStatus(lenderAssociationStatus.name());
        } else if (LenderAssociationStages.PENNY_DROP.name().equalsIgnoreCase(existingLendingApplicationLenderDetails.getStage())) {
            existingLendingApplicationLenderDetails.setPennyDropStatus(lenderAssociationStatus.name());
        }
        lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);

        boolean isApplicableForAggregationFlow = !ObjectUtils.isEmpty(loanUtil.getLenderAggregationScreen(lendingApplication.getId()));
        if (enableLenderChange) {
            log.info("changing lender for the application {}", lendingApplication.getId());
            if(!Arrays.asList(LendingViewStates.SHOP_DETAILS_PAGE.name(), LendingViewStates.SHOP_PICTURES_PAGE.name(), LendingViewStates.KYC_PAGE.name(), LendingViewStates.LENDER_EVALUATION_PAGE.name()).contains(lendingApplicationDetails.getApplicationViewState())
                    || !ObjectUtils.isEmpty(lendingApplication.getAgreementAt())) {
                log.info("skipping lender change and rejecting application as agreement already done / lendingViewState {} for application is not correct for applicationId {}", lendingApplicationDetails.getApplicationViewState(), lendingApplication.getId());
                lendingApplication.setStatus("rejected");
                lendingApplicationDao.save(lendingApplication);
                lendingApplicationServiceV2.evictCache(lendingApplication.getMerchantId());
                return;
            }
            Lender modifiedLender = null;
            if (!isApplicableForAggregationFlow){
                modifiedLender = lenderAssignService.modifyLender(lendingApplication.getId());
                if(ObjectUtils.isEmpty(modifiedLender)) {
                    log.info("Rejecting application for the applicationId: {}",lendingApplication.getId());
                    lendingApplication.setManualKyc("rejected");
                    lendingApplication.setStatus("rejected");
                    lendingApplicationDao.save(lendingApplication);
                    commonUtil.saveApplicationRejectionAudit(lendingApplication, "rejected",
                            !ObjectUtils.isEmpty(lendingApplication.getStatus()) ? lendingApplication.getStatus() : "",
                            "APP_STATUS", "rejected due to nullable lender");
                    lendingApplicationServiceV2.evictCache(lendingApplication.getMerchantId());
                    return;
                }
                if (Arrays.asList(LenderAssociationStatus.SANCTION_FAILED.name(), LenderAssociationStatus.SANC_CALLBACK_CLIENT_FAILURE.name(), LenderAssociationStatus.SANC_REQUEST_CLIENT_FAILURE.name()).contains(lenderAssociationStatus.name())) {

                    log.info("modified lender for applicationId : {} and lenderAssociationStatus : {}", lendingApplication.getId(), lenderAssociationStatus.name());
                    loanUtil.putApplicationInResignAndRenach(lendingApplication, modifiedLender.name());
                }
                lendingApplicationDetails.setStage(LenderAssociationStages.INIT.name());
                lendingApplicationDetailsDao.save(lendingApplicationDetails);
                if(bharatPeKycLenderAlreadyAssigned(lendingApplication.getId(), lendingApplication.getMerchantId()) || (kycUtils.isELigibleForLenderKyc(modifiedLender.name(), lendingApplication.getMerchantId()))) {
                    log.info("Invoking lender association after lender change for applicationId {} {}", lendingApplication.getId(), lendingApplication.getLender());
                    pushApplicationToNextStage(lendingApplication.getId(), modifiedLender.name(), LenderAssociationStages.INIT.name(), Boolean.TRUE);
                }
            }
        }
    }

    public void pushApplicationToNextStage(Long applicationId, String lender, String lenderAssociationStage, Boolean autoInvoke) {
        log.info("push application to next stage from current {} stage for applicationId {}", lenderAssociationStage, applicationId);
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
        log.info("stage {} updated in app details for application {}", lendingApplicationDetails.getStage(), applicationId);
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

    public void retryApplicationStage(Long applicationId, String lender, String lenderAssociationStage) {
        try {
            log.info("retrying stage {} for {}", lenderAssociationStage, applicationId);
            ILenderAssociationService iLenderAssociationService =
                    lenderAssociationStageFactory.getStageAssociatedLenderService(lenderAssociationStage).getLenderAssociationService(lender);
            Map<String, Object> args = new HashMap<String, Object>() {{
                put("requestId", MDC.get("requestId"));
            }};
            iLenderAssociationService.invoke(applicationId, args);
            log.info("application {} successfully pushed to retry for stage {}", applicationId, lenderAssociationStage);
        } catch (Exception e) {
            log.error("Exception in retrying stage {} for applicationId {} {}", lenderAssociationStage, applicationId, Arrays.asList(e.getStackTrace()));
        }
    }

    private LenderAssociationStages nextStage(Lender lender, LenderAssociationStages stage) {
        switch (lender) {
            case USFB :
            case TRILLIONLOANS:
            case MUTHOOT:
            case CAPRI:
            case PAYU:
            case CREDITSAISON:
            case SMFG:
                return LenderAssociationStageFactoryV2.getNextStage(lender, stage);
            case ABFL :
            case PIRAMAL:
            default:
                return LenderAssociationStageFactory.getNextStage(lender, stage);
        }
    }

    public Boolean invokeSpecificStage(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, String stage) {
        switch (stage) {
            case "GENERATE_DOCUMENT":
                return associationServiceUtil.invokeDocsGenerateService(lender, lenderAssociationDetailsDto.getLendingApplication(), DocType.LOAN_AGREEMENT, true);
            case "EKYC_STATUS":
                return associationServiceUtil.invokeEkycStatusCheck(lender, lenderAssociationDetailsDto.getLendingApplication());
            case "EKYC":
                return associationServiceUtil.invokeEKyc(lender, lenderAssociationDetailsDto);
            case "CREATE_LEAD":
                return associationServiceUtil.invokeCreateLeadService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "AADHAR_UPLOAD":
            case "SELFIE_UPLOAD":
            case "SHOP_PHOTO_UPLOAD":
            case "SHOP_STOCK_PHOTO_UPLOAD":
            case "BUSINESS_DOC_UPLOAD":
                return associationServiceUtil.invokeDocUploadService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto, stage);
            case "CREATE_CLIENT" :
                return associationServiceUtil.invokeCreateClientService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "KYC":
                return associationServiceUtil.invokeKycService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "UPDATE_LEAD":
                return associationServiceUtil.invokeLeadUpdateService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "NACH_MANDATE":
                return associationServiceUtil.invokeNachMandateService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "PENNY_DROP":
                return associationServiceUtil.invokePennyDropService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "TOPUP_UNDO_APPROVE":
                return associationServiceUtil.invokeTopupUndoApproveService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "TOPUP_DATA":
                return associationServiceUtil.invokeTopupDataService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "ADD_CHARGE":
                return associationServiceUtil.invokeAddChargeService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "TOPUP_APPROVE":
                return associationServiceUtil.invokeTopupApproveService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            default:
                return false;
        }
    }


    public Boolean bharatPeKycLenderAlreadyAssigned(Long applicationId, Long merchantId) {
        try {
            Boolean bpKycLenderFound = Boolean.FALSE;
            List<String> alreadyAssignedLender = lendingApplicationLenderDetailsDao.findLendersByApplicationId(applicationId);
            for(String lender : alreadyAssignedLender) {
                if(!kycUtils.isELigibleForLenderKyc(lender, merchantId)) {
                    bpKycLenderFound = Boolean.TRUE;
                    break;
                }
            }
            return bpKycLenderFound;
        } catch (Exception e) {
            log.info("Exception in checking prev Bp Kyc lenders assigned for applicationId {}", applicationId);
        }
        return false;
    }
}
