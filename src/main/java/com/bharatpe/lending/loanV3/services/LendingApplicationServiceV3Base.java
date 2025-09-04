package com.bharatpe.lending.loanV3.services;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.service.SherlocLoanStatusChangeService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.ModifiedOfferResponseDto;
import com.bharatpe.lending.entity.LendingOfferModificationSnapshot;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.lendingplatform.lending.service.LoanCreationService;
import com.bharatpe.lending.lendingplatform.lending.util.RolloutUtil;
import com.bharatpe.lending.lendingplatform.lending.util.StageUtil;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.config.TrillionLoansConfig;
import com.bharatpe.lending.loanV3.consumer.KycRequestKafka;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.enums.KycMode;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.services.associationsV2.AbflDocGenerateService;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.util.EdiUtil;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

@Slf4j
public abstract class LendingApplicationServiceV3Base {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    @Lazy
    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    @Qualifier("ConfluentKafkaTemplate")
    KafkaTemplate confluentKafkaTemplate;

    @Autowired
    SherlocLoanStatusChangeService sherlocLoanStatusChangeService;

    @Lazy
    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Autowired
    LendingOfferModificationSnapshotDao lendingOfferModificationSnapshotDao;

    @Autowired
    private LendingEligibleLoanDao eligibleLoanDao;

    @Autowired
    private EasyLoanUtil easyLoanUtil;

    @Lazy
    @Autowired
    KycUtils kycUtils;

    @Autowired
    @Lazy
    LoanUtil loanUtil;

    @Lazy
    @Autowired
    KycRequestKafka kycRequestKafka;

    @Value("${ekyc.status.check.enabled.lenders:}")
    String eKycStatusCheckEnabledLenders;

    @Autowired
    LendingLenderPricingDao lendingLenderPricingDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    @Lazy
    TrillionLoansConfig trillionLoansConfig;

    @Value("${abfl.topup.downgrade.flow.rollout:0}")
    private Integer abflTopupDowngradeFlowRollout;

    @Autowired
    private LoanCreationService loanCreationService;
    @Autowired
    private RolloutUtil rolloutUtil;
    @Autowired
    private StageUtil stageUtil;

    @Value("${offer.modified.eligible.lender:}")
    String offerModifiedEligibleLenders;

    @Autowired
    AbflDocGenerateService abflDocGenerateService;

    @Autowired
    private EdiUtil ediUtil;

    @Value("${pricing.experiment.enable:false}")
    boolean pricingExpEnabled;

    @Autowired
    PricingExperimentDao pricingExperimentDao;

    public abstract void initLenderAssociation(InvokeLenderAssociationRequest invokeLenderAssociationRequest);

    public ApiResponse<?> fetchApplicationStatus(Long merchantId, String lenderKycStatus) {
        LendingApplication currentDraftApplication =  lendingApplicationDao.findByMerchantIdAndStatus(merchantId, "draft");
        if (ObjectUtils.isEmpty(currentDraftApplication)) {
            LendingApplication currentRejectApplication =  lendingApplicationDao.findByMerchantIdAndStatus(merchantId, "rejected");
            LendingApplicationDetails rejectedApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(currentRejectApplication.getId());
            if(!ObjectUtils.isEmpty(currentRejectApplication)) {
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.LENDER_ASSOCIATION_FAILED)
                        .stage(LenderAssociationStages.valueOf(rejectedApplicationDetails.getStage()))
                        .ediModelModified(false)
                        .lender(currentRejectApplication.getLender())
                        .applicationId(currentRejectApplication.getId())
                        .build());
            }
            return new ApiResponse<>(false, "open draft lending application not found");
        }
        Boolean isTopup = LoanType.TOPUP.name().equalsIgnoreCase(currentDraftApplication.getLoanType());
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(currentDraftApplication.getId());
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            return new ApiResponse<>(false, "lending application details not found");
        }
        if (LenderAssociationStages.LENDER_CHANGE.name().equalsIgnoreCase(lendingApplicationDetails.getStage())) {
            return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                    .status(LenderAssociationStatus.LENDER_CHANGE_IN_PROGRESS)
                    .stage(LenderAssociationStages.LENDER_CHANGE)
                    .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                    .lender(currentDraftApplication.getLender())
                    .isApplicableForAggregationFlow(
                            !ObjectUtils.isEmpty(loanUtil.getLenderAggregationScreenV2(currentDraftApplication.getId(), merchantId))
                                    || !ObjectUtils.isEmpty(loanUtil.getLenderAggregationScreen(currentDraftApplication.getId(), merchantId))
                    )
                    .applicationId(currentDraftApplication.getId())
                    .build());
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(currentDraftApplication.getId(), Status.ACTIVE.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            if (LenderAssociationStages.RE_INIT.name().equalsIgnoreCase(lendingApplicationDetails.getStage()) || checkForBPKycRequired(currentDraftApplication, LenderAssociationStages.INIT)) {
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.LENDER_ASSOCIATION_PENDING)
                        .stage(LenderAssociationStages.valueOf(lendingApplicationDetails.getStage()))
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .showBpKycPage(Boolean.TRUE)
                        .prevLender(!LenderAssociationStages.RE_INIT.name().equalsIgnoreCase(lendingApplicationDetails.getStage()) ? getPrevLender(currentDraftApplication) : null)
                        .applicationId(currentDraftApplication.getId())
                        .build());
            }

            InvokeLenderAssociationRequest invokeLenderAssociationRequest = new InvokeLenderAssociationRequest();
            invokeLenderAssociationRequest.setApplicationId(currentDraftApplication.getId());
            invokeLenderAssociationRequest.setStage(LenderAssociationStages.INIT.name());
            invokeLenderAssociationRequest.setForceEnable(false);
            initLenderAssociation(invokeLenderAssociationRequest);
            log.info("lead creation triggered ! Please retry for status in few minutes");
            return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                    .status(LenderAssociationStatus.LENDER_ASSOCIATION_PENDING)
                    .stage(LenderAssociationStages.INIT)
                    .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                    .lender(currentDraftApplication.getLender())
                    .applicationId(currentDraftApplication.getId())
                    .build());

        } else {
            if (rolloutUtil.lendingPlatformNbfcFlowApplicable(merchantId)) {
                log.info("Application rolled out to rearch v1 version: {}", lendingApplicationLenderDetails.getApplicationId());
                loanCreationService.initiateLoanCreationWorkflow(lendingApplicationLenderDetails.getApplicationId());
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(stageUtil.getLenderAssociationStatus(lendingApplicationLenderDetails.getApplicationId(), lendingApplicationLenderDetails.getLender()))
                        .stage(stageUtil.getLenderAssociationStages(lendingApplicationLenderDetails.getApplicationId(), lendingApplicationLenderDetails.getLender()))
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .applicationId(currentDraftApplication.getId())
                        .build());
            }
            Double approvedLoanOfferAmount = lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt();
            if (LenderAssociationStages.COMPLETED.name().equalsIgnoreCase(getWrapperStage(lendingApplicationLenderDetails.getStage()))) {
                // check if interest rate is lower ??
                boolean isEdiChanged = false;
                boolean isProcessingFeeChanged = false;
                boolean isDowngradeCase = false;
                if (!ObjectUtils.isEmpty(lendingApplicationDetails.getOfferId()) && loanUtil.isLenderPricingApplicableMerchant(merchantId)) {
                    Optional<LendingEligibleLoan> eligibleLoan = eligibleLoanDao.findById(lendingApplicationDetails.getOfferId());
                    if (eligibleLoan.isPresent() && currentDraftApplication.getEdi().intValue() < eligibleLoan.get().getEdi()) {
                        log.info("EDI decreased for applicationId {}", currentDraftApplication.getId());
                        isEdiChanged = true;
                    }
                    if (Boolean.FALSE.equals(isTopup) && eligibleLoan.isPresent() && currentDraftApplication.getProcessingFee().intValue() != eligibleLoan.get().getProcessingFee()) {
                        log.info("Processing fee changed for applicationId {}", currentDraftApplication.getId());
                        isProcessingFeeChanged = true;
                    }
                }
                if (offerModifiedEligibleLenders.contains(currentDraftApplication.getLender()) &&
                        !ObjectUtils.isEmpty(approvedLoanOfferAmount) && currentDraftApplication.getLoanAmount() > approvedLoanOfferAmount) {
                    log.info("offer downgraded for applicationId {}", currentDraftApplication.getId());
                    isDowngradeCase = true;
                }
                if(isEdiChanged || isProcessingFeeChanged || isDowngradeCase) {
                    return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                            .isRoiDecreased(true)
                            .lender(currentDraftApplication.getLender())
                            .status(LenderAssociationStatus.LENDER_ASSOCIATION_COMPLETED)
                            .stage(LenderAssociationStages.COMPLETED)
                            .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                            .applicationId(currentDraftApplication.getId())
                            .build());
                }

                log.info("Lender assoc completed but EDI not decreased for applicationId {}", currentDraftApplication.getId());
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .isRoiDecreased(false)
                        .status(LenderAssociationStatus.LENDER_ASSOCIATION_COMPLETED)
                        .stage(LenderAssociationStages.COMPLETED)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .applicationId(currentDraftApplication.getId())
                        .build());
            } else if (LenderAssociationStages.LEAD_WRAPPER.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                log.info("Lender assoc at LEAD_WRAPPER for applicationId {}", currentDraftApplication.getId());
                Boolean bpKycFlowNotInitiatedOnce = false;
                if (LenderAssociationStatus.KYC_PENDING.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus())) {
                    bpKycFlowNotInitiatedOnce = checkForBPKycRequired(currentDraftApplication, LenderAssociationStages.LEAD_WRAPPER);
                    if(Boolean.FALSE.equals(bpKycFlowNotInitiatedOnce)) {
                        LenderAssociationStages currStage = LenderAssociationStages.valueOf(lendingApplicationLenderDetails.getStage());
                        LenderAssociationStages nextStage = LenderAssociationStageFactoryV2.getNextStage(Lender.valueOf(lendingApplicationLenderDetails.getLender()), currStage);
                        lendingApplicationLenderDetails.setStage(nextStage.name());
                        lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                        Boolean autoInvoke = LenderAssociationStageFactoryV2.autoInvokeNextStage(Lender.valueOf(currentDraftApplication.getLender()), LenderAssociationStages.valueOf(lendingApplicationLenderDetails.getStage()));
                        nbfcUtils.pushApplicationToNextStage(currentDraftApplication.getId(), currentDraftApplication.getLender(), currStage.name(), autoInvoke);
                    }
                }
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.valueOf(Optional.ofNullable(lendingApplicationLenderDetails.getKycStatus()).orElse(LenderAssociationStatus.KYC_PENDING.name())))
                        .stage(LenderAssociationStages.LEAD_WRAPPER)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .showBpKycPage(bpKycFlowNotInitiatedOnce)
                        .applicationId(currentDraftApplication.getId())
                        .build());
            } else if (LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                log.info("Lender assoc at BRE for applicationId {}", currentDraftApplication.getId());
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.valueOf(Optional.ofNullable(lendingApplicationLenderDetails.getBreStatus()).orElse(LenderAssociationStatus.BRE_PENDING.name())))
                        .stage(LenderAssociationStages.BRE)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .applicationId(currentDraftApplication.getId())
                        .build());
            } else if (LenderAssociationStages.KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                log.info("Lender assoc at KYC for applicationId {}", currentDraftApplication.getId());
                if (Lender.TRILLIONLOANS.name().equalsIgnoreCase(currentDraftApplication.getLender())) {
                    if (Boolean.FALSE.equals(isTopup) && !ObjectUtils.isEmpty(lenderKycStatus) && lenderKycStatus.equalsIgnoreCase(trillionLoansConfig.getEKycStatusCheck())) {
                        invokeStageForLender(InvokeStageRequestDTO.builder()
                                .applicationId(currentDraftApplication.getId())
                                .lender(currentDraftApplication.getLender())
                                .stage(LenderAssociationStages.KYC_STATUS_CHECK.name())
                                .build());
                    }
                }
                String lenderKycRedirectionUrl = getLenderKycRedirectionUrl(currentDraftApplication, lendingApplicationLenderDetails, lenderKycStatus);
                if (ObjectUtils.isEmpty(lenderKycRedirectionUrl) && eKycStatusCheckEnabledLenders.contains(lendingApplicationLenderDetails.getLender())) {
                    lenderKycRedirectionUrl = updateEKycDetails(currentDraftApplication, lendingApplicationLenderDetails, lenderKycRedirectionUrl);
                }
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.valueOf(Optional.ofNullable(lendingApplicationLenderDetails.getKycStatus()).orElse(LenderAssociationStatus.KYC_PENDING.name())))
                        .stage(LenderAssociationStages.KYC)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .lenderKycRedirectionUrl(lenderKycRedirectionUrl)
                        .prevLender(LenderAssociationStatus.EKYC_PENDING.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus()) && Boolean.FALSE.equals(isTopup) ? getPrevLender(currentDraftApplication) : null)
                        .lenderKycRetry(LenderAssociationStatus.EKYC_RETRY.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus()))
                        .applicationId(currentDraftApplication.getId())
                        .build());
            }
        }
        return new ApiResponse<>(false, "something went wrong");
    }

    public Boolean checkForBPKycRequired(LendingApplication currentDraftApplication, LenderAssociationStages stage) {
        Boolean lenderKycPipe = kycUtils.isEligibleForLenderKyc(currentDraftApplication.getLender(), currentDraftApplication.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(currentDraftApplication.getLoanType()));
        Boolean skipKycEligible = kycUtils.isEligibleForSkipKyc(currentDraftApplication.getId(), Lender.valueOf(currentDraftApplication.getLender()), currentDraftApplication.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(currentDraftApplication.getLoanType()));
        LendingApplicationKycDetails consentKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndConsentDateNotNull(currentDraftApplication.getId());
        if(LenderAssociationStages.LEAD_WRAPPER.equals(stage) && (skipKycEligible || lenderKycPipe) && ObjectUtils.isEmpty(consentKycDetails)) {
            log.info("BP Selfie flow not initiated even once for application {}", currentDraftApplication.getId());
            return true;
        }
        if(LenderAssociationStages.INIT.equals(stage) && ObjectUtils.isEmpty(consentKycDetails) && skipKycEligible) {
            log.info("BP Selfie will be initiated after skip kyc flow for applicationId {}", currentDraftApplication.getId());
            return false;
        }
        if(LenderAssociationStages.INIT.equals(stage) && !ObjectUtils.isEmpty(loanUtil.getLenderAggregationScreen(currentDraftApplication.getId())) && ObjectUtils.isEmpty(consentKycDetails)) {
            log.info("BP KYC flow not initiated even once for application {}", currentDraftApplication.getId());
            return true;
        }
        LendingApplicationLenderDetails prevLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(currentDraftApplication.getId(), Status.INACTIVE.name());
        if (ObjectUtils.isEmpty(prevLenderDetails) || kycUtils.bharatPeKycLenderAlreadyAssigned(currentDraftApplication.getId()) || lenderKycPipe || skipKycEligible) {
            log.info("BP Kyc already done for applicationId {}", currentDraftApplication.getId());
            return false;
        }
        LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderAndKycModeOrderByIdDesc(currentDraftApplication.getId(), currentDraftApplication.getLender(), KycMode.BP_KYC.name());
        if (!ObjectUtils.isEmpty(lendingApplicationKycDetails) && !ObjectUtils.isEmpty(lendingApplicationKycDetails.getConsentDate())) {
            log.info("BP Kyc already done for applicationId {} with lender {}", currentDraftApplication.getId(), currentDraftApplication.getLender());
            return false;
        }
        return true;
    }

    private String getLenderKycRedirectionUrl(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String lenderKycStatus) {
        try {
            if (kycUtils.isEligibleForLenderKyc(lendingApplicationLenderDetails.getLender(), lendingApplication.getMerchantId(),LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))) {
                if (LenderAssociationStages.KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())
                        && LenderAssociationStages.EKYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycMode())
                        && LenderAssociationStages.EKYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getLeadStatus())) {
                    if (LenderAssociationStatus.EKYC_INITIATED.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus())
                       || (LenderAssociationStatus.EKYC_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus())
                       && "PENDING".equalsIgnoreCase(lenderKycStatus))) {
                        String lenderKycUrl = lendingApplicationLenderDetails.getNbfcKycAsyncId();
                        lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.EKYC_IN_PROGRESS.name());
                        lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                        return lenderKycUrl;
                    }
                }
            }
        } catch (Exception e) {
            log.info("Exception in getting {} kyc redirectionUrl for  applicationId {} {} ", lendingApplicationLenderDetails.getLender(), lendingApplicationLenderDetails.getApplicationId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getPrevLender(LendingApplication lendingApplication) {
        try {
            LendingApplicationLenderDetails prevLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(lendingApplication.getId(), Status.INACTIVE.name());
            if (!ObjectUtils.isEmpty(prevLenderDetails)) {
                return prevLenderDetails.getLender();
            }
        } catch (Exception e) {
            log.info("Exception in checking lender kyc required check for applicationId {} {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getWrapperStage(String stage) {
        switch (stage) {
            case "ASSC_COMPLETED":
            case "SANCTION_WRAPPER":
            case "DRAWDOWN":
            case "DOCUMENT_UPLOAD":
            case "DOC_UPLOAD":
            case "PUSH_AUDIT":
            case "COMPLETED":
                return "COMPLETED";
        }
        return stage;
    }

    public  ApiResponse<?> modifyAppDetails(ModifyAppRequest modifyAppRequest) {
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(modifyAppRequest.getApplicationId());
        boolean loanStatusFlag = false;
        if (!lendingApplication.isPresent()) {
            return new ApiResponse<>(false, "no app exists");
        }
        lendingApplication.get().setLender(!ObjectUtils.isEmpty(modifyAppRequest.getLender()) ? modifyAppRequest.getLender() : lendingApplication.get().getLender());
        lendingApplication.get().setLoanAmount(!ObjectUtils.isEmpty(modifyAppRequest.getLoanAmount()) ? modifyAppRequest.getLoanAmount() : lendingApplication.get().getLoanAmount());
        lendingApplication.get().setStatus(!ObjectUtils.isEmpty(modifyAppRequest.getAppStatus()) ? modifyAppRequest.getAppStatus() : lendingApplication.get().getStatus());
        lendingApplication.get().setExternalLoanId(!ObjectUtils.isEmpty(modifyAppRequest.getExternalLoanId()) ? modifyAppRequest.getExternalLoanId() : lendingApplication.get().getExternalLoanId());
        lendingApplication.get().setSendToNbfc(!ObjectUtils.isEmpty(modifyAppRequest.getSendToNbfc()) ? ("SET_NULL".equalsIgnoreCase(modifyAppRequest.getSendToNbfc()) ? null: modifyAppRequest.getSendToNbfc()) : lendingApplication.get().getSendToNbfc());
        lendingApplication.get().setLmsStage(!ObjectUtils.isEmpty(modifyAppRequest.getLmsStage()) ? ("SET_NULL".equalsIgnoreCase(modifyAppRequest.getLmsStage()) ? null : modifyAppRequest.getLmsStage()) : lendingApplication.get().getLmsStage());
        lendingApplication.get().setNbfcId(!ObjectUtils.isEmpty(modifyAppRequest.getNbfcId()) ? ("SET_NULL".equalsIgnoreCase(modifyAppRequest.getNbfcId()) ? null : modifyAppRequest.getNbfcId()) : lendingApplication.get().getNbfcId());
        lendingApplication.get().setEdi(!ObjectUtils.isEmpty(modifyAppRequest.getEdi()) ? modifyAppRequest.getEdi() : lendingApplication.get().getEdi());
        lendingApplication.get().setRepayment(!ObjectUtils.isEmpty(modifyAppRequest.getRepaymentAmount()) ? modifyAppRequest.getRepaymentAmount() : lendingApplication.get().getRepayment());
        lendingApplication.get().setPayableDays(!ObjectUtils.isEmpty(modifyAppRequest.getPayableDays()) ? modifyAppRequest.getPayableDays() : lendingApplication.get().getPayableDays());
        lendingApplication.get().setNbfcSendDate(!ObjectUtils.isEmpty(modifyAppRequest.getNbfcSendDate()) ? modifyAppRequest.getNbfcSendDate() : lendingApplication.get().getNbfcSendDate());
        lendingApplication.get().setDisbursalPartner(!ObjectUtils.isEmpty(modifyAppRequest.getDisbursalPartner()) ? modifyAppRequest.getDisbursalPartner() : lendingApplication.get().getDisbursalPartner());
        lendingApplication.get().setLoanDisbursalStatus(!ObjectUtils.isEmpty(modifyAppRequest.getLoanDisbursalStatus()) ? modifyAppRequest.getLoanDisbursalStatus() : lendingApplication.get().getLoanDisbursalStatus());
        lendingApplication.get().setTenure(!ObjectUtils.isEmpty(modifyAppRequest.getTenure()) ? modifyAppRequest.getTenure() + " months" : lendingApplication.get().getTenure());
        lendingApplication.get().setTenureInMonths(!ObjectUtils.isEmpty(modifyAppRequest.getTenure()) ? modifyAppRequest.getTenure() : lendingApplication.get().getTenureInMonths());
        lendingApplication.get().setDisbursalAmount(!ObjectUtils.isEmpty(modifyAppRequest.getDisbursalAmt()) ? modifyAppRequest.getDisbursalAmt() : lendingApplication.get().getDisbursalAmount());
        lendingApplication.get().setProcessingFee(!ObjectUtils.isEmpty(modifyAppRequest.getProcessingFee()) ? modifyAppRequest.getProcessingFee() : lendingApplication.get().getProcessingFee());
        lendingApplication.get().setDisburseTimestamp(!ObjectUtils.isEmpty(modifyAppRequest.getDisbursalDate()) ? modifyAppRequest.getDisbursalDate() : lendingApplication.get().getDisburseTimestamp());
        lendingApplicationDao.save(lendingApplication.get());
        log.info("successfully updated lending app  {}", modifyAppRequest.getApplicationId());
        if (!ObjectUtils.isEmpty(modifyAppRequest.getLenderDetailsId())) {
            Optional<LendingApplicationLenderDetails> lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findById(modifyAppRequest.getLenderDetailsId());
            if (lendingApplicationLenderDetails.isPresent()) {
                lendingApplicationLenderDetails.get().setStage(!ObjectUtils.isEmpty(modifyAppRequest.getStage()) ? modifyAppRequest.getStage() : lendingApplicationLenderDetails.get().getStage());
                lendingApplicationLenderDetails.get().setStatus(!ObjectUtils.isEmpty(modifyAppRequest.getLenderDetailStatus()) ? modifyAppRequest.getLenderDetailStatus() : lendingApplicationLenderDetails.get().getStatus());
                lendingApplicationLenderDetails.get().setBreStatus(!ObjectUtils.isEmpty(modifyAppRequest.getBreStatus()) ? modifyAppRequest.getBreStatus() : lendingApplicationLenderDetails.get().getBreStatus());
                lendingApplicationLenderDetails.get().setKycStatus(!ObjectUtils.isEmpty(modifyAppRequest.getKycStatus()) ? modifyAppRequest.getKycStatus() : lendingApplicationLenderDetails.get().getKycStatus());
                lendingApplicationLenderDetails.get().setSanctionStatus(!ObjectUtils.isEmpty(modifyAppRequest.getSancStatus()) ? modifyAppRequest.getSancStatus() : lendingApplicationLenderDetails.get().getSanctionStatus());
                lendingApplicationLenderDetails.get().setDrawDownStatus(!ObjectUtils.isEmpty(modifyAppRequest.getDrawdownStatus()) ? modifyAppRequest.getDrawdownStatus() : lendingApplicationLenderDetails.get().getDrawDownStatus());
                lendingApplicationLenderDetails.get().setLan(!ObjectUtils.isEmpty(modifyAppRequest.getLan()) ? modifyAppRequest.getLan() : lendingApplicationLenderDetails.get().getLan());
                lendingApplicationLenderDetails.get().setAccountId(!ObjectUtils.isEmpty(modifyAppRequest.getExternalLoanId()) ? modifyAppRequest.getExternalLoanId() : lendingApplicationLenderDetails.get().getAccountId());
                lendingApplicationLenderDetails.get().setUtrNo((modifyAppRequest.getUtr() != null) ? modifyAppRequest.getUtr() : lendingApplicationLenderDetails.get().getUtrNo());
                lendingApplicationLenderDetails.get().setLeadId((modifyAppRequest.getLeadId() != null) ? modifyAppRequest.getLeadId() : lendingApplicationLenderDetails.get().getLeadId());
                lendingApplicationLenderDetails.get().setLender((modifyAppRequest.getLaldLender() != null) ? modifyAppRequest.getLaldLender() : lendingApplicationLenderDetails.get().getLender());
                lendingApplicationLenderDetails.get().setLoanCreationTimestamp(!ObjectUtils.isEmpty(modifyAppRequest.getDisbursalDate()) ? modifyAppRequest.getDisbursalDate() : lendingApplicationLenderDetails.get().getLoanCreationTimestamp());
                if (modifyAppRequest.getDocStatusUpdate()) {
                    lendingApplicationLenderDetails.get().setDocUploadStatus(modifyAppRequest.getDocUploadStatus());
                    lendingApplicationLenderDetails.get().setFailedUpload(modifyAppRequest.getFailedUpload());
                }
                if (modifyAppRequest.getUpdateApr()) {
                    lendingApplicationLenderDetails.get().setAnnualRoi(null);
                    lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails.get());
                    DecimalFormat df = new DecimalFormat("#.##");
                    df.setRoundingMode(ediUtil.isRoundDownEligibleLender(lendingApplication.get().getLender()) ? RoundingMode.UP : RoundingMode.DOWN);
                    if (Lender.UGRO.name().equalsIgnoreCase(lendingApplicationLenderDetails.get().getLender())) {
                        df = new DecimalFormat("#.######");
                    }
                    lendingApplicationLenderDetails.get().setAnnualRoi(Double.valueOf(df.format(
                            lendingApplicationServiceV2.getApr(lendingApplication.get().getMerchantId(), lendingApplication.get().getId(), lendingApplication.get().getLoanAmount(),
                                    LenderOffDays.valueOf(lendingApplication.get().getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplication.get().getLender()))));
                }
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails.get());
                log.info("successfully updated lending lender details for {}", modifyAppRequest.getApplicationId());
            }
        }
        if (!ObjectUtils.isEmpty(modifyAppRequest.getLendingAppDetailsId())) {
            Optional<LendingApplicationDetails> lendingApplicationDetails = lendingApplicationDetailsDao.findById(modifyAppRequest.getLendingAppDetailsId());
            if (lendingApplicationDetails.isPresent()) {
                lendingApplicationDetails.get().setStage(!ObjectUtils.isEmpty(modifyAppRequest.getStage()) ? modifyAppRequest.getStage(): lendingApplicationDetails.get().getStage());
                lendingApplicationDetails.get().setEdiModel(!ObjectUtils.isEmpty(modifyAppRequest.getEdiModel()) ? modifyAppRequest.getEdiModel(): lendingApplicationDetails.get().getEdiModel());
                lendingApplicationDetails.get().setEdiModelModified(!ObjectUtils.isEmpty(modifyAppRequest.getEdiModelModified()) ? modifyAppRequest.getEdiModelModified(): lendingApplicationDetails.get().getEdiModelModified());
                lendingApplicationDetails.get().setLenderAssc(!ObjectUtils.isEmpty(modifyAppRequest.getLenderAssc()) ? modifyAppRequest.getLenderAssc(): lendingApplicationDetails.get().getLenderAssc());
                lendingApplicationDetailsDao.save(lendingApplicationDetails.get());
                log.info("successfully updated lending app details for {}", modifyAppRequest.getApplicationId());
            }
        }
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(modifyAppRequest.getApplicationId());
        if (!ObjectUtils.isEmpty(lendingGstDetail)) {
            lendingGstDetail.setDisbursedAccountPersonal(true);
            lendingGstDao.save(lendingGstDetail);
        }
        if (!ObjectUtils.isEmpty(modifyAppRequest.getLpsId())) {
            Optional<LendingPaymentSchedule> lendingPaymentSchedule = lendingPaymentScheduleDao.findById(modifyAppRequest.getLpsId());
            if (lendingPaymentSchedule.isPresent() && "CLOSED".equalsIgnoreCase(modifyAppRequest.getLpsStatus())) {
                if(!"CLOSED".equalsIgnoreCase(lendingPaymentSchedule.get().getStatus())){
                    loanStatusFlag = true;
                    log.info("setting loan flag as true in modifyAppDetails for merchantId :{}",lendingPaymentSchedule.get().getMerchantId());
                }
                lendingPaymentSchedule.get().setClosingDate(new Date());
                lendingPaymentSchedule.get().setStatus("CLOSED");
                log.info("closed loan {}", modifyAppRequest.getLpsId());
            }
            else if (lendingPaymentSchedule.isPresent() && "ACTIVE".equalsIgnoreCase(modifyAppRequest.getLpsStatus())) {
                lendingPaymentSchedule.get().setClosingDate(null);
                if(!"ACTIVE".equalsIgnoreCase(lendingPaymentSchedule.get().getStatus())){
                    loanStatusFlag = true;
                    log.info("setting loan flag as true in modifyAppDetails for merchantId :{}",lendingPaymentSchedule.get().getMerchantId());
                }
                lendingPaymentSchedule.get().setStatus("ACTIVE");
                log.info("active marked loan {}", modifyAppRequest.getLpsId());
            }
            if(lendingPaymentSchedule.isPresent() && !ObjectUtils.isEmpty(modifyAppRequest.getLpsStartDate())) {
                log.info("setting loan start date as {} for lpsId : {} ", modifyAppRequest.getLpsStartDate(), lendingPaymentSchedule.get().getId());
                lendingPaymentSchedule.get().setStartDate(modifyAppRequest.getLpsStartDate());
                lendingPaymentSchedule.get().setNextEdiDate(modifyAppRequest.getLpsStartDate());
            }
            lendingPaymentScheduleDao.save(lendingPaymentSchedule.get());

            if(loanStatusFlag) {
                Long merchantId = lendingPaymentSchedule.get().getMerchantId();
                log.info("sending loan flag status in modifyAppDetails for merchantId {}:",merchantId);
                sherlocLoanStatusChangeService.pushLoanStatusChangeEventToKafka(merchantId, lendingPaymentSchedule.get().getStatus());
            }
        }
        return new ApiResponse<>(true,"successfully updated application details");
    }

    public  ApiResponse<?> modifyAppDetailsV2(ModifyAppRequest modifyAppRequest) {
        for (String apps : modifyAppRequest.getApplicationList().split(";")) {
            Map<String, String> request = new HashMap() {{
                put("application_id", apps);
                put("documents", modifyAppRequest.getDocs());
                put("systemManagedState", false);
            }};
            confluentKafkaTemplate.send("invoke_data_upload", request);
        }
        return new ApiResponse<>(true,"success");
    }

    public ApiResponse<?> invokeStageForLender(InvokeStageRequestDTO invokeStageRequest) {
        try {
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(invokeStageRequest.getApplicationId());
            log.info("lending application {}", lendingApplication.get());
            if (ObjectUtils.isEmpty(lendingApplication.get()) || Arrays.asList("deleted", "rejected").contains(lendingApplication.get().getStatus())) {
                log.info("no application found for {}", invokeStageRequest.getApplicationId());
                return new ApiResponse<>(false, "No application found for given Id");
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto();
            lenderAssociationDetailsDto.setApplicationId(lendingApplication.get().getId());
            lenderAssociationDetailsDto.setLendingApplication(lendingApplication.get());
            lenderAssociationDetailsDto.setMerchantId(lendingApplication.get().getMerchantId());
            lenderAssociationDetailsDto.setManageState(Boolean.TRUE);
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                    .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(), Status.ACTIVE.name(), lendingApplication.get().getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("Lending application lender details not found for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                return new ApiResponse<>(false, "Lending application lender details not found for applicationId");
            }
            lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
            Boolean success = nbfcUtils.invokeSpecificStage(lendingApplication.get().getLender(), lenderAssociationDetailsDto, invokeStageRequest.getStage());
            return new ApiResponse<>(success, invokeStageRequest.getStage() + " stage invoked for " + invokeStageRequest.getApplicationId());
        } catch (Exception e) {
            log.info("Exception in invoke stage {} of {} for applicationId {} {}", invokeStageRequest.getStage(), invokeStageRequest.getLender(), invokeStageRequest.getApplicationId(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something went wrong in invoking stage");
    }

    public String updateEKycDetails(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String lenderKycRedirectionUrl) {
         try {
             if (LenderAssociationStages.KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())
                     && LenderAssociationStages.EKYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycMode())
                     && LenderAssociationStages.EKYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getLeadStatus())
                     && Collections.singletonList(LenderAssociationStatus.EKYC_IN_PROGRESS.name()).contains(lendingApplicationLenderDetails.getKycStatus())) {
                 LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto();
                 lenderAssociationDetailsDto.setApplicationId(lendingApplication.getId());
                 lenderAssociationDetailsDto.setLendingApplication(lendingApplication);
                 lenderAssociationDetailsDto.setMerchantId(lendingApplication.getMerchantId());
                 lenderAssociationDetailsDto.setManageState(Boolean.TRUE);
                 lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
                 Boolean success = nbfcUtils.invokeSpecificStage(lendingApplication.getLender(), lenderAssociationDetailsDto, LenderAssociationStages.EKYC_STATUS.name());
                 if(success) {
                     lendingApplication = lendingApplicationDao.findById(lendingApplication.getId()).orElse(lendingApplication);
                     lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name());
                     lenderKycRedirectionUrl = getLenderKycRedirectionUrl(lendingApplication, lendingApplicationLenderDetails, null);
                 }
                 log.info("Successfully updated eKyc status of {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
             }
         } catch (Exception e) {
             log.info("Exception in updating eKyc status of {} for applicationId {}  {}", lendingApplication.getLender(), lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
         }
         return lenderKycRedirectionUrl;
    }

    public ApiResponse<?> initiateLenderEKyc(InvokeStageRequestDTO invokeStageRequest) {
        try {
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(invokeStageRequest.getApplicationId());
            log.info("lending application {}", lendingApplication.get());
            if (ObjectUtils.isEmpty(lendingApplication.get())) {
                log.info("no application found for {}", invokeStageRequest.getApplicationId());
                return new ApiResponse<>(false, "No application found for given Id");
            }
            if(Lender.ABFL.name().equalsIgnoreCase(lendingApplication.get().getLender())) {
                Map<String, Object> request = new HashMap<>();
                request.put("application_id", lendingApplication.get().getId());
                kycRequestKafka.eKycRequestListener(new ObjectMapper().writeValueAsString(request));
                return new ApiResponse<>(true, "lender eKyc initiated for applicationId " + invokeStageRequest.getApplicationId());
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto();
            lenderAssociationDetailsDto.setApplicationId(lendingApplication.get().getId());
            lenderAssociationDetailsDto.setLendingApplication(lendingApplication.get());
            lenderAssociationDetailsDto.setMerchantId(lendingApplication.get().getMerchantId());
            lenderAssociationDetailsDto.setManageState(Boolean.TRUE);
            lenderAssociationDetailsDto.setModifyLender(Boolean.TRUE);
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                    .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(), Status.ACTIVE.name(), lendingApplication.get().getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("Lending application lender details not found for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                return new ApiResponse<>(false, "Lending application lender details not found for applicationId");
            }
            lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
            nbfcUtils.invokeSpecificStage(lendingApplication.get().getLender(), lenderAssociationDetailsDto, invokeStageRequest.getStage());
            return new ApiResponse<>(true, "lender eKyc initiated for applicationId " + invokeStageRequest.getApplicationId());
        } catch (Exception e) {
            log.info("Exception in initiating lender eKyc {} of {} for applicationId {} {}", invokeStageRequest.getStage(), invokeStageRequest.getLender(), invokeStageRequest.getApplicationId(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something went wrong in initiating lender eKyc");
    }

    public ApiResponse<?> modifyOffer(Long applicationId, Long merchantId){
        try{
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if (ObjectUtils.isEmpty(lendingApplication)){
                return new ApiResponse<>(false, createResponse("false", "Something went wrong"), "Something went wrong");
            }

            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)){
                return new ApiResponse<>(false, createResponse("false", "Something went wrong"), "Something went wrong");

            }

            if(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt()<=0){
                return new ApiResponse<>(false, createResponse("false", "Revised offer amount is less than 0"), "Revised offer amount is less than 0");

            }

            if(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() >= lendingApplication.getLoanAmount()) {
                log.info("nbfcApprovedLoanOfferAmt is equal to loan amount for applicationId {}", lendingApplication.getId());
                return new ApiResponse<>(true, createResponse("true", "Offer already modified"), "Offer already modified");
            }

            // calling update loan for Trillions
            if(Lender.TRILLIONLOANS.name().equals(lendingApplication.getLender())){
                ApiResponse<?> response = invokeStageForLender(new InvokeStageRequestDTO(lendingApplication.getId(), lendingApplication.getLender(), "UPDATE_LOAN"));
                if(ObjectUtils.isEmpty(response) || !response.success){
                    log.error("Update Loan failed for application:{}", lendingApplication.getId());
                    return new ApiResponse<>(false, createResponse("false", "Please try again later"), "Please try again later");
                }
            }

            LendingOfferModificationSnapshot lendingOfferModificationSnapshot = new LendingOfferModificationSnapshot();
            lendingOfferModificationSnapshot.setApplicationId(lendingApplication.getId());
            lendingOfferModificationSnapshot.setPayableDays(lendingApplication.getPayableDays());
            lendingOfferModificationSnapshot.setDisbursalAmount(lendingApplication.getDisbursalAmount());
            lendingOfferModificationSnapshot.setLoanAmount(lendingApplication.getLoanAmount());
            lendingOfferModificationSnapshot.setEdiAmount(lendingApplication.getEdi());
            lendingOfferModificationSnapshot.setProceeingFee(lendingApplication.getProcessingFee());
            lendingOfferModificationSnapshot.setRepaymentAmount(lendingApplication.getRepayment());

            lendingOfferModificationSnapshotDao.save(lendingOfferModificationSnapshot);

            Double interestAmt = (lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() * (lendingApplication.getInterestRate() * lendingApplication.getTenureInMonths()) / 100);
            Long payableDays = lendingApplication.getPayableDays();
            double ediAmount = ((lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() + interestAmt) / payableDays);
            ediAmount = ediUtil.getEdiAfterRoundingLogic(lendingApplication.getId(), ediAmount, lendingApplication.getLender());
//            double initialDisbursalAmountWithoutProcessingFee = lendingApplication.getDisbursalAmount() + lendingApplication.getProcessingFee();
//            double processingFeeRate = lendingApplication.getProcessingFee()/initialDisbursalAmountWithoutProcessingFee;
//            double processingFee = Math.ceil(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() * processingFeeRate);
            BigDecimal processingFee;
            BigDecimal processingFeeRate;
            BigDecimal finalDisbursalAmount;
            if(lendingApplication.getDisbursalAmount() != null && lendingApplication.getProcessingFee() != null && lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() != null){
                BigDecimal disbursalAmount = BigDecimal.valueOf(lendingApplication.getDisbursalAmount());
                BigDecimal processingFeeAmount = BigDecimal.valueOf(lendingApplication.getProcessingFee());
                BigDecimal initialDisbursalAmountWithoutProcessingFee = disbursalAmount.add(processingFeeAmount);
                processingFeeRate = processingFeeAmount.divide(initialDisbursalAmountWithoutProcessingFee, 10, RoundingMode.HALF_UP);
                BigDecimal nbfcApprovedLoanOfferAmt = BigDecimal.valueOf(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt());
                processingFee= nbfcApprovedLoanOfferAmt.multiply(processingFeeRate).setScale(0, RoundingMode.CEILING);
                finalDisbursalAmount = nbfcApprovedLoanOfferAmt.subtract(processingFee);
            }else{
                throw new NullPointerException("Either processing fee or disbursal amount or nbfc approved amount cannot be null");
            }

            double previousAmount=0;
            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()) && Lender.ABFL.name().equalsIgnoreCase(lendingApplication.getLender())
                    && easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), abflTopupDowngradeFlowRollout) ){
                LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(lendingApplication.getMerchantId(),  Collections.singletonList("ACTIVE"));
                previousAmount = loanUtil.getForeClosureAmountForLender(activeLoan);
                log.info("Previous amount for ABFL topup applicationId {} is {}", lendingApplication.getId(), previousAmount);
                if(previousAmount <= 0){
                    throw new RuntimeException(String.format("Error getting %s foreclosure details", lendingApplication.getLender()));
                }
                BigDecimal nbfcApprovedLoanOfferAmt = BigDecimal.valueOf(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt());
                BigDecimal previousAmountValue = BigDecimal.valueOf(previousAmount);
                BigDecimal disbursalAmount = nbfcApprovedLoanOfferAmt.subtract(previousAmountValue);
                processingFee = disbursalAmount.multiply(processingFeeRate).setScale(0,RoundingMode.CEILING);
                finalDisbursalAmount = disbursalAmount.subtract(processingFee);
            }
            lendingApplication.setProcessingFee(processingFee.doubleValue());
            lendingApplication.setLoanAmount(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt());
            lendingApplication.setRepayment(ediAmount * payableDays);
            lendingApplication.setDisbursalAmount(finalDisbursalAmount.doubleValue());
            lendingApplication.setEdi(ediAmount);
            log.info("Modified offer for applicationId: {}, loanAmount: {}, ediAmount: {}, processingFee: {}, repayment: {}, disbursalAmount: {}",
                    lendingApplication.getId(), lendingApplication.getLoanAmount(), lendingApplication.getEdi(), lendingApplication.getProcessingFee(), lendingApplication.getRepayment(), lendingApplication.getDisbursalAmount());

            lendingApplicationDao.save(lendingApplication);

            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()) && Lender.ABFL.name().equalsIgnoreCase(lendingApplication.getLender())
                    && easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), abflTopupDowngradeFlowRollout) ) {
                log.info("Generating lender document for applicationId in modify offer api: {}", lendingApplication.getId());
                final LendingApplication finalLendingApplication = lendingApplication;
                new Thread(() -> abflDocGenerateService.invokeDocGenerate(finalLendingApplication, DocType.LOAN_AGREEMENT, true, false)).start();
            }

            LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
            lendingAuditTrial.setLoanId(Objects.nonNull(lendingApplication.getExternalLoanId())?lendingApplication.getExternalLoanId():"");
            lendingAuditTrial.setApplicationId(lendingApplication.getId());
            lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
            lendingAuditTrial.setType("OFFER_MODIFIED");
            lendingAuditTrial.setOldStatus(lendingApplication.getStatus());
            lendingAuditTrial.setNewStatus(lendingApplication.getStatus());
            lendingAuditTrial.setRemarks("LENDER_BRE_OFFER_MODIFIED");
            lendingAuditTrialDao.save(lendingAuditTrial);


            return new ApiResponse<>(true, createResponse("true", "Offer successfully modified"), "Offer successfully modified");

        } catch (Exception ex){
            log.info("Exception occurred while modifying offer for application:{}, {}, {}", applicationId, ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return new ApiResponse<>(false, createResponse("false", "something went wrong"), "something went wrong");
    }

    private Map<String, Object> createResponse(String success, String message){
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        return response;
    }

    public ModifiedOfferResponseDto modifiedOfferDetails(Long applicationId, Long merchantId){
        if(!loanUtil.isLenderPricingApplicableMerchant(merchantId)){
            log.info("pricing flow not applicable for merchant:{}", merchantId);
            return null;
        }

        try{
            ModifiedOfferResponseDto modifiedOfferResponseDto = new ModifiedOfferResponseDto();
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if(ObjectUtils.isEmpty(lendingApplication)){
                log.info("application:{} not found for merchant:{}", applicationId, merchantId);
                return null;
            }
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(lendingApplication.getId());
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());

            if(!ObjectUtils.isEmpty(lendingApplicationDetails.getOfferId())){
                log.info("offerId not found for application:{}", applicationId);
                Optional<LendingEligibleLoan> eligibleLoan = eligibleLoanDao.findById(lendingApplicationDetails.getOfferId());
                if(eligibleLoan.isPresent()){
                    log.info("eligible loan not found for id :{}", lendingApplicationDetails.getOfferId());
                    DecimalFormat df = new DecimalFormat("#.##");
//                    df.setRoundingMode(RoundingMode.DOWN);
                    ModifiedOfferResponseDto.OfferDetails oldOfferDetails = new ModifiedOfferResponseDto.OfferDetails(
                            eligibleLoan.get().getAmount()
                            ,eligibleLoan.get().getEdi().doubleValue(),
                            eligibleLoan.get().getRateOfInterest(),
                            eligibleLoan.get().getProcessingFee().doubleValue(),
                            eligibleLoan.get().getRepayment().doubleValue(),
                            eligibleLoan.get().getAmount() - eligibleLoan.get().getProcessingFee(),
                            eligibleLoan.get().getApr(),
                            eligibleLoan.get().getIrr(),
                            eligibleLoan.get().getEdiCount(), eligibleLoan.get().getTenureInMonths());

                    LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
                    ModifiedOfferResponseDto.OfferDetails newOfferDetails = null;
                    Double approvedLoanOfferAmount = lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt();

                    if(offerModifiedEligibleLenders.contains(lendingApplication.getLender()) &&
                            !ObjectUtils.isEmpty(approvedLoanOfferAmount) && lendingApplication.getLoanAmount() > approvedLoanOfferAmount) {
                        PricingExperiment pricingExperiment = null;
                        if(pricingExpEnabled) {
                            pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMidEndsWithAndPincodeColor(lendingRiskVariablesSnapshot.getRiskSegment().name(), lendingRiskVariablesSnapshot.getRiskGroup(),
                                    lendingRiskVariablesSnapshot.getTenure(), (int)(lendingRiskVariablesSnapshot.getMerchantId()%10), lendingRiskVariablesSnapshot.getPincodeColor().name(), lendingApplication.getCreatedAt());
                        }
                        Double pfRate;
                        if(!ObjectUtils.isEmpty(pricingExperiment)) {
                            log.info("pricing experiment fetched for {}: {}",merchantId, pricingExperiment);
                            pfRate = pricingExperiment.getProcessingFeeRate();
                        }else{
                            LendingLenderPricing lendingLenderPricing = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColor(
                                    lendingRiskVariablesSnapshot.getRiskSegment().name(),
                                    lendingRiskVariablesSnapshot.getRiskGroup(),
                                    lendingApplication.getTenureInMonths(),
                                    lendingApplication.getLender(),
                                    lendingRiskVariablesSnapshot.getPincodeColor().name(),
                                    lendingApplication.getCreatedAt()
                            );
                            if(ObjectUtils.isEmpty(lendingLenderPricing)){
                                log.info("Lending lender pricing not available, using eligible loan values");
                                pfRate = eligibleLoan.get().getProcessingFeeRate();
                            } else {
                                pfRate = lendingLenderPricing.getProcessingFeeRate();
                            }
                        }


                        Double processingFee = Math.ceil((pfRate * approvedLoanOfferAmount) / 100);
                        Double interestAmt = (approvedLoanOfferAmount * (lendingApplication.getInterestRate() * lendingApplication.getTenureInMonths()) / 100) ;
                        double ediAmount = ((approvedLoanOfferAmount + interestAmt) / lendingApplication.getPayableDays());
                        ediAmount = ediUtil.getEdiAfterRoundingLogic(lendingApplication.getId(), ediAmount, lendingApplication.getLender());
                        Double repayment = ediAmount * lendingApplication.getPayableDays();

                        newOfferDetails = new ModifiedOfferResponseDto.OfferDetails(
                                approvedLoanOfferAmount,
                                ediAmount,
                                lendingApplication.getInterestRate(),
                                processingFee,
                                repayment,
                                approvedLoanOfferAmount - processingFee,
                                Double.valueOf(df.format(lendingApplicationServiceV2.getApr(lendingApplication.getPayableDays().intValue(),ediAmount,approvedLoanOfferAmount - processingFee, merchantId, lendingApplication.getLender()))),
                                Double.valueOf(df.format(lendingApplicationServiceV2.getApr(lendingApplication.getPayableDays().intValue(),ediAmount,approvedLoanOfferAmount,merchantId, lendingApplication.getLender()))),
                                lendingApplication.getPayableDays().intValue(),
                                lendingApplication.getTenureInMonths()
                        );
                        modifiedOfferResponseDto.setIsOfferModified(true);
                    } else {
                        newOfferDetails = new ModifiedOfferResponseDto.OfferDetails(
                                lendingApplication.getLoanAmount(),
                                lendingApplication.getEdi(),
                                lendingApplication.getInterestRate(),
                                lendingApplication.getProcessingFee(),
                                lendingApplication.getRepayment(),
                                lendingApplication.getDisbursalAmount(),
                                Double.valueOf(df.format(lendingApplicationServiceV2.getApr(lendingApplication.getPayableDays().intValue(),lendingApplication.getEdi(),lendingApplication.getLoanAmount() - lendingApplication.getProcessingFee(), merchantId, lendingApplication.getLender()))),
                                Double.valueOf(df.format(lendingApplicationServiceV2.getApr(lendingApplication.getPayableDays().intValue(),lendingApplication.getEdi(),lendingApplication.getLoanAmount(),merchantId, lendingApplication.getLender()))),
                                lendingApplication.getPayableDays().intValue(),
                                lendingApplication.getTenureInMonths()
                        );
                    }

                    modifiedOfferResponseDto.setOldOffer(oldOfferDetails);
                    modifiedOfferResponseDto.setNewOffer(newOfferDetails);
                    return modifiedOfferResponseDto;
                }
            }
            return null;
        } catch (Exception ex){
            log.error("Exception occurred:{}, {} for application:{}", ex.getMessage(), Arrays.asList(ex.getStackTrace()), applicationId);
        }
        return null;
    }

    public ApiResponse<?> skipKycConsent(Long merchantId, SkipKycConsentRequestDTO skipKycConsentRequest) {
        try {
            LendingApplication application = lendingApplicationDao.findByIdAndMerchantId(skipKycConsentRequest.getApplicationId(), merchantId);
            if(ObjectUtils.isEmpty(application)) {
                log.info("Application not found with given id {} and merchantId {}", skipKycConsentRequest.getApplicationId(), merchantId);
                return new ApiResponse<>(false, "application not found");
            }
            LendingApplicationLenderDetails lenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(skipKycConsentRequest.getApplicationId(), Status.ACTIVE.name(), application.getLender());
            if(ObjectUtils.isEmpty(lenderDetails)) {
                log.info("Lender details not found with lender {} and applicationId {}", application.getLender(), application.getId());
                return new ApiResponse<>(false, "lender details not found");
            }
            LendingApplicationKycDetails applicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderAndKycModeOrderByIdDesc(application.getId(), application.getLender(), KycMode.SKIP_KYC.name());
            if(ObjectUtils.isEmpty(applicationKycDetails)) {
                log.info("Application kyc details not found with lender {} and applicationId {}", application.getLender(), application.getId());
                return new ApiResponse<>(false, "Application Kyc details not found");
            }
            Boolean isEligibleForLenderKyc = kycUtils.isEligibleForLenderKyc(application.getLender(), application.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(application.getLoanType()));
            KycMode kycMode = skipKycConsentRequest.isOptForSkipKyc() ? KycMode.SKIP_KYC : isEligibleForLenderKyc ? KycMode.LENDER_KYC : KycMode.BP_KYC;
            LenderAssociationStatus kycStatus = isEligibleForLenderKyc ? skipKycConsentRequest.isOptForSkipKyc() ? LenderAssociationStatus.SKIP_KYC_PENDING : LenderAssociationStatus.EKYC_PENDING : LenderAssociationStatus.KYC_PENDING;
            lenderDetails.setKycMode(skipKycConsentRequest.isOptForSkipKyc() ? LenderAssociationStages.SKIP_KYC.name() : isEligibleForLenderKyc ? LenderAssociationStages.EKYC.name() : LenderAssociationStages.KYC.name());
            lenderDetails.setKycStatus(kycStatus.name());
            lendingApplicationLenderDetailsDao.save(lenderDetails);
            applicationKycDetails.setAadharApprovedAt(new Date());
            lendingApplicationKycDetailsDao.save(applicationKycDetails);
            if(LenderAssociationStatus.SKIP_KYC_PENDING.name().equalsIgnoreCase(lenderDetails.getKycStatus())) {
                LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto();
                lenderAssociationDetailsDto.setApplicationId(application.getId());
                lenderAssociationDetailsDto.setLendingApplication(application);
                lenderAssociationDetailsDto.setMerchantId(application.getMerchantId());
                lenderAssociationDetailsDto.setManageState(Boolean.TRUE);
                lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lenderDetails);
                lenderAssociationDetailsDto.setModifyLender(Boolean.TRUE);
                new Thread(() -> nbfcUtils.invokeSpecificStage(application.getLender(), lenderAssociationDetailsDto, LenderAssociationStages.SKIP_KYC.name())).start();
            }
            LendingViewStates nextPage = isEligibleForLenderKyc ? LendingViewStates.LENDER_EVALUATION_PAGE : LendingViewStates.KYC_PAGE;
            SkipKycConsentResponseDTO response = SkipKycConsentResponseDTO.builder()
                    .kycMode(kycMode.name())
                    .nextPage(nextPage)
                    .build();
            return new ApiResponse<>(response);
        } catch (Exception e) {
            log.error("Exception in skip kyc consent for merchant {} and applicationId {} {}", merchantId, skipKycConsentRequest.getApplicationId(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "something went wrong");
    }

    public ApiResponse<?> getSkipKycDetails(Long merchantId, Long applicationId) {
        try {
            LendingApplication application = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if(ObjectUtils.isEmpty(application)) {
                log.info("Application not found for skip kyc details with given id {} and merchantId {}", applicationId, merchantId);
                return new ApiResponse<>(false, "application not found");
            }
            LendingApplicationKycDetails kycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderAndKycModeOrderByIdDesc(applicationId, application.getLender(), KycMode.SKIP_KYC.name());
            if(ObjectUtils.isEmpty(kycDetails)) {
                log.info("Skip Kyc details not found with applicationId {}", application.getId());
                return new ApiResponse<>(false, "Skip kyc details not found");
            }
            SkipKycDetailsResponseDTO response = SkipKycDetailsResponseDTO.builder()
                    .applicationId(application.getId())
                    .aadhaarName(kycDetails.getAadharName())
                    .dob(kycDetails.getDob())
                    .aadhaarAddress(kycDetails.getAadharAddress())
                    .build();
            return new ApiResponse<>(response);
        } catch (Exception e) {
            log.error("Exception in fetching skip kyc details for merchant {} and applicationId {} {}", merchantId, applicationId, Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "something went wrong");
    }
}
