package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.common.entities.LendingApplication;

import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingLenderPricingDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dao.PricingExperimentDao;
import com.bharatpe.common.enums.RejectionStage;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.entity.PricingExperiment;
import com.bharatpe.lending.common.entity.LendingLenderPricing;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LenderDisbursalLimitsDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.RiskVariablesDTO;
import com.bharatpe.lending.entity.LendingLenderQuota;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.common.enums.EdiModel;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.lendingplatform.lending.service.LoanCreationService;
import com.bharatpe.lending.lendingplatform.lending.util.RolloutUtil;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssignment;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.bharatpe.lending.constant.LendingConstants.OFFER_DOWNGRADE_PERCENTAGE;
import static com.bharatpe.lending.constant.LendingConstants.OFFER_DOWNGRADE_THRESHOLD;

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

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LendingLenderPricingDao lendingLenderPricingDao;
    @Autowired
    CommonService commonService;

    @Autowired
    PricingExperimentDao pricingExperimentDao;

    @Value("${pricing.experiment.enable:false}")
    boolean pricingExpEnabled;

    @Autowired
    private RolloutUtil rolloutUtil;

    @Autowired
    @Lazy
    private LoanCreationService loanCreationService;

    @Async
    public void modifyLender(LendingApplication lendingApplication, LendingApplicationLenderDetails existingLendingApplicationLenderDetails, LenderAssociationStatus lenderAssociationStatus) {
        if(Arrays.asList(Lender.ABFL.name(),Lender.PIRAMAL.name()).contains(lendingApplication.getLender()) && LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
            log.info("restricting lender change for {} Topup application : {}",lendingApplication.getLender(), lendingApplication.getId());
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
            existingLendingApplicationLenderDetails.setBreRejectionReason(existingLendingApplicationLenderDetails.getBreRejectionReason());
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
                    if (!ObjectUtils.isEmpty(existingLendingApplicationLenderDetails.getBreRejectionReason())) {
                        log.info("Rejecting application for the applicationId: {} due to : {}", lendingApplication.getId(), existingLendingApplicationLenderDetails.getBreRejectionReason());
                        lendingApplication.setRejectionStage(RejectionStage.BRE);
                        lendingApplication.setRejectionReason(existingLendingApplicationLenderDetails.getBreRejectionReason());
                    } else {
                        lendingApplication.setManualKyc("rejected");
                    }
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
                if(bharatPeKycLenderAlreadyAssigned(lendingApplication.getId(), lendingApplication.getMerchantId(),LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())) || (kycUtils.isELigibleForLenderKyc(modifiedLender.name(), lendingApplication.getMerchantId(),LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())))) {
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
        //sending the application through new rearch flow
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(lendingApplicationDetails.getApplicationId());
        if (lendingApplication.isPresent() && rolloutUtil.lendingPlatformNbfcFlowApplicable(lendingApplication.get().getMerchantId())) {
            log.info("Application rolled out to rearch v1 version from NbfcUtils for applicationId: {} {}", lendingApplication.get().getId(), lendingApplication.get().getLender());
            loanCreationService.initiateLoanCreationWorkflow(lendingApplicationDetails.getApplicationId());
            return ;
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
            case UGRO:
            case OXYZO:
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
            case "POST_CONSENT":
                return  associationServiceUtil.invokeConsentPostingService(lender, lenderAssociationDetailsDto);
            case "GET_LEAD":
                return  associationServiceUtil.invokeGetLeadService(lender, lenderAssociationDetailsDto);
            case "KYC_STATUS_CHECK":
                return  associationServiceUtil.invokeKycStatusCheck(lender, lenderAssociationDetailsDto);
            case "UPDATE_LOAN":
                return associationServiceUtil.invokeUpdateLoan(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "SKIP_VKYC":
                return associationServiceUtil.invokeSkipVkyc(lenderAssociationDetailsDto);
            case "UPDATE_ADDRESS":
                return associationServiceUtil.invokeAddressUpdateService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            case "UPDATE_BANK_DETAILS":
                return associationServiceUtil.invokeBankAccountUpdateService(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto);
            default:
                return false;
        }
    }


    public Boolean bharatPeKycLenderAlreadyAssigned(Long applicationId, Long merchantId, boolean isTopup) {
        try {
            Boolean bpKycLenderFound = Boolean.FALSE;
            List<String> alreadyAssignedLender = lendingApplicationLenderDetailsDao.findLendersByApplicationId(applicationId);
            for(String lender : alreadyAssignedLender) {
                if(!kycUtils.isELigibleForLenderKyc(lender, merchantId, isTopup)) {
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

    public boolean additionalLenderDowngradeChecksFailed(LendingApplication lendingApplication){
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        RiskVariablesDTO riskVariables = new RiskVariablesDTO();
        PricingExperiment pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMidEndsWithAndPincodeColor(
                lendingRiskVariablesSnapshot.getRiskSegment().name(),
                lendingRiskVariablesSnapshot.getRiskGroup(),
                lendingRiskVariablesSnapshot.getTenure(),
                (int) (lendingApplication.getMerchantId()%10),
                lendingRiskVariablesSnapshot.getPincodeColor().name(),
                lendingApplication.getCreatedAt()
        );

        if(pricingExpEnabled && !ObjectUtils.isEmpty(pricingExperiment)) {
            log.info("pricing experiment fetched for {}: {}", lendingApplication.getMerchantId(), pricingExperiment);
            riskVariables.setPricingExperimentMap(Collections.singletonMap(lendingApplication.getMerchantId(), pricingExperiment));
        }else {
            LendingLenderPricing lenderPricingList = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColor(
                    lendingRiskVariablesSnapshot.getRiskSegment().name(),
                    lendingRiskVariablesSnapshot.getRiskGroup(),
                    lendingApplication.getTenureInMonths(),
                    lendingApplication.getLender(),
                    lendingRiskVariablesSnapshot.getPincodeColor().name(),
                    lendingApplication.getCreatedAt()
            );
            riskVariables.setLenderPricingMap(Collections.singletonMap(lendingApplication.getLender(), lenderPricingList));
        }

        // Check APR and IRR conditions
        boolean aprCheckResult = lenderAssignService.maxAprCheckFailedV2(lendingApplication, LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel(), lendingApplication.getLender(), riskVariables);
        boolean irrCheckResult = lenderAssignService.maxIrrCheckFailedV2(lendingApplication, LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel(), lendingApplication.getLender(), riskVariables);

        boolean result = aprCheckResult || irrCheckResult;

        log.info("Additional lender downgrade checks failed for LendingApplication [{}] with lender [{}] -> {}",
                lendingApplication.getId(), lendingApplication.getLender(), result);

        return result;
    }

    public boolean offerDowngradeThresholdChecksFailed(double offerDowngradeThreshold, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        double requestedLoanAmount = lenderAssociationDetailsRequestDto.getLendingApplication().getLoanAmount();
        double approvedLoanAmount = lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getNbfcApprovedLoanOfferAmt();
        // Convert to BigDecimal
        BigDecimal requestedAmount = BigDecimal.valueOf(requestedLoanAmount);
        BigDecimal approvedAmount = BigDecimal.valueOf(approvedLoanAmount);
        BigDecimal downgradeThreshold = BigDecimal.valueOf(offerDowngradeThreshold);

        // Perform downgrade calculation without rounding up
        BigDecimal one = BigDecimal.valueOf(1);
        BigDecimal downgradePercentage = one.subtract(approvedAmount.divide(requestedAmount, 10, RoundingMode.DOWN)) // Trim in division
                .multiply(BigDecimal.valueOf(100)) // Convert to percentage
                .setScale(2, RoundingMode.DOWN); // Trim final result to 2 decimal places

        LendingLenderQuota fallbackLender = lenderDisbursalLimitsDao.findByEdiModelIsNull();

        // If downgrade percentage exceeds the threshold, modify the lender
        if (downgradePercentage.compareTo(downgradeThreshold) > 0 && !ObjectUtils.isEmpty(fallbackLender.getLender())) {
            log.info("downgrade percentage {} exceeds the threshold {} for lender {}, modifying lender",
                    downgradePercentage, downgradeThreshold, lenderAssociationDetailsRequestDto.getLendingApplication().getLender());
            lenderAssociationDetailsRequestDto.setModifyLender(true);
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.OFFER_DOWNGRADE_THRESHOLD_BREACHED.name());
            Map<String, Object> metaData = new HashMap<>();
            metaData.put(OFFER_DOWNGRADE_PERCENTAGE, downgradePercentage);
            metaData.put(OFFER_DOWNGRADE_THRESHOLD, downgradeThreshold);
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setMetaData(metaData);
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
            return true ;
        }

        return false;
    }

}