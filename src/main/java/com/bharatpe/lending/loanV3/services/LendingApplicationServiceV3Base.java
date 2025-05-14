package com.bharatpe.lending.loanV3.services;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dao.mongo.NBFCRetryRepository;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.entity.mongo.NBFCRetry;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.service.SherlocLoanStatusChangeService;
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
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
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

    @Value("#{'${status.poll.enabled.lenders:ABFL}'.split(',')}")
    private Set<String> statusPollEnabledLenders;

    @Autowired
    LendingLenderPricingDao lendingLenderPricingDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    @Lazy
    TrillionLoansConfig trillionLoansConfig;

    @Autowired
    NBFCRetryRepository nbfcRetryRepository;

    @Value("#{${nbfc.ekyc-status.retry.timeout:{0:10, 1:300, 2:300}}}")
    private final Map<Integer, Long> ekycStatusRetryTimeoutsMap = new HashMap<>();

    @Value("${retry.timer.delay:5}")
    private long retryTimerDelay;

    @Value("${nbfc.retry.max-retries-count:3}")
    private int maxRetriesCount;

    @Value("${ekyc.status-poll.rollout.percentage:5}")
    private int ekycStatusPollRolloutPercentage;

    @Value("${offer.modified.eligible.lender:}")
    String offerModifiedEligibleLenders;

    @Autowired
    private NbfcRequestRetryService nbfcRequestRetryService;
    @Autowired
    private LoanCreationService loanCreationService;
    @Autowired
    private RolloutUtil rolloutUtil;
    @Autowired
    private StageUtil stageUtil;

    @Value("${offer.modified.eligible.lender:}")
    String offerModifiedEligibleLenders;

    public final Set<String> validStages = new HashSet<>(Arrays.asList(LenderAssociationStatus.EKYC_IN_PROGRESS.name(), LenderAssociationStatus.KYC_IN_PROGRESS.name()));

    public abstract void initLenderAssociation(InvokeLenderAssociationRequest invokeLenderAssociationRequest);

    public ApiResponse<?> fetchApplicationStatus(Long merchantId, String lenderKycStatus, boolean userReturnedFromLenderKyc) {
        LendingApplication currentDraftApplication =  lendingApplicationDao.findByMerchantIdAndStatus(merchantId, "draft");
        if (ObjectUtils.isEmpty(currentDraftApplication)) {
            LendingApplication currentRejectApplication =  lendingApplicationDao.findByMerchantIdAndStatus(merchantId, "rejected");
            if(!ObjectUtils.isEmpty(currentRejectApplication)) {
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.LENDER_ASSOCIATION_FAILED)
                        .stage(LenderAssociationStages.FAILED)
                        .ediModelModified(false)
                        .lender(currentRejectApplication.getLender())
                        .build());
            }
            return new ApiResponse<>(false,"open draft lending application not found");
        }
        if(Arrays.asList(Lender.ABFL.name(), Lender.TRILLIONLOANS.name(), Lender.PIRAMAL.name()).contains(currentDraftApplication.getLender()) && LoanType.TOPUP.name().equalsIgnoreCase(currentDraftApplication.getLoanType())) {
            return fetchTopupApplicationStatus(currentDraftApplication, lenderKycStatus, userReturnedFromLenderKyc);
        }

        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(currentDraftApplication.getId());
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            return new ApiResponse<>(false,"lending application details not found");
        }
        if (LenderAssociationStages.LENDER_CHANGE.name().equalsIgnoreCase(lendingApplicationDetails.getStage())) {
            return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                    .status(LenderAssociationStatus.LENDER_CHANGE_IN_PROGRESS)
                    .stage(LenderAssociationStages.LENDER_CHANGE)
                    .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                    .lender(currentDraftApplication.getLender())
                    .isApplicableForAggregationFlow(!ObjectUtils.isEmpty(loanUtil.getLenderAggregationScreen(currentDraftApplication.getId())))
                    .build());
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(currentDraftApplication.getId(), Status.ACTIVE.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {

            if (checkForBPKycRequired(currentDraftApplication)) {
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.LENDER_CHANGE_IN_PROGRESS)
                        .stage(LenderAssociationStages.LENDER_CHANGE)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .showBpKycPage(Boolean.TRUE)
                        .prevLender(getPrevLender(currentDraftApplication))
                        .build());
            }


            InvokeLenderAssociationRequest invokeLenderAssociationRequest = new InvokeLenderAssociationRequest();
            invokeLenderAssociationRequest.setApplicationId(currentDraftApplication.getId());
            invokeLenderAssociationRequest.setStage(LenderAssociationStages.INIT.name());
            invokeLenderAssociationRequest.setForceEnable(false);
            initLenderAssociation(invokeLenderAssociationRequest);
            return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                    .status(LenderAssociationStatus.LENDER_CHANGE_IN_PROGRESS)
                    .stage(LenderAssociationStages.INIT)
                    .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                    .lender(currentDraftApplication.getLender())
                    .build());

        }
        else {
            if (rolloutUtil.lendingPlatformNbfcFlowApplicable(merchantId)) {
                log.info("Application rolled out to rearch v1 version: {}", lendingApplicationLenderDetails.getApplicationId());
                loanCreationService.initiateLoanCreationWorkflow(lendingApplicationLenderDetails.getApplicationId());
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(stageUtil.getLenderAssociationStatus(lendingApplicationLenderDetails.getApplicationId(), lendingApplicationLenderDetails.getLender()))
                        .stage(stageUtil.getLenderAssociationStages(lendingApplicationLenderDetails.getApplicationId(), lendingApplicationLenderDetails.getLender()))
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .build());
            }
            Double approvedLoanOfferAmount = lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt();
            if (LenderAssociationStages.COMPLETED.name().equalsIgnoreCase(getWrapperStage(lendingApplicationLenderDetails.getStage()))) {
                // check if interest rate is lower ??
                if (!ObjectUtils.isEmpty(lendingApplicationDetails.getOfferId()) && loanUtil.isLenderPricingApplicableMerchant(merchantId)){
                    Optional<LendingEligibleLoan> eligibleLoan = eligibleLoanDao.findById(lendingApplicationDetails.getOfferId());
                    if(eligibleLoan.isPresent() && currentDraftApplication.getEdi().intValue() < eligibleLoan.get().getEdi()){
                        log.info("EDI decreased for applicationId {}", currentDraftApplication.getId());
                        return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                                .isRoiDecreased(true)
                                .lender(currentDraftApplication.getLender())
                                .status(LenderAssociationStatus.LENDER_ASSOCIATION_COMPLETED)
                                .stage(LenderAssociationStages.COMPLETED)
                                .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                                .lender(currentDraftApplication.getLender())
                                .build());
                    }
                }
                if(offerModifiedEligibleLenders.contains(currentDraftApplication.getLender()) &&
                        !ObjectUtils.isEmpty(approvedLoanOfferAmount) && currentDraftApplication.getLoanAmount() > approvedLoanOfferAmount) {
                    log.info("offer downgraded for applicationId {}", currentDraftApplication.getId());
                    return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                            .isRoiDecreased(true)
                            .lender(currentDraftApplication.getLender())
                            .status(LenderAssociationStatus.LENDER_ASSOCIATION_COMPLETED)
                            .stage(LenderAssociationStages.COMPLETED)
                            .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                            .lender(currentDraftApplication.getLender())
                            .build());
                }

                log.info("Lender assoc completed but EDI not decreased for applicationId {}", currentDraftApplication.getId());
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .isRoiDecreased(false)
                        .status(LenderAssociationStatus.LENDER_ASSOCIATION_COMPLETED)
                        .stage(LenderAssociationStages.COMPLETED)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .build());
            } else if (LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                log.info("Lender assoc at BRE for applicationId {}", currentDraftApplication.getId());
                return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.valueOf(Optional.ofNullable(lendingApplicationLenderDetails.getBreStatus()).orElse(LenderAssociationStatus.BRE_PENDING.name())))
                        .stage(LenderAssociationStages.BRE)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .build());
            } else if (LenderAssociationStages.KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                if (Lender.TRILLIONLOANS.name().equalsIgnoreCase(currentDraftApplication.getLender())) {
                    if (LenderAssociationStatus.SELFIE_PENDING_FOR_LENDER_KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus())) {
                        //INVOKING STAGE FOR LENDER PIPE
                        invokeStageForLender(InvokeStageRequestDTO.builder()
                                .applicationId(currentDraftApplication.getId())
                                .lender(currentDraftApplication.getLender())
                                .stage(LenderAssociationStages.SELFIE_UPLOAD.name())
                                .build());
                    }

                    if (!ObjectUtils.isEmpty(lenderKycStatus) && lenderKycStatus.equalsIgnoreCase(trillionLoansConfig.getEKycStatusCheck())) {
                        invokeStageForLender(InvokeStageRequestDTO.builder()
                                .applicationId(currentDraftApplication.getId())
                                .lender(currentDraftApplication.getLender())
                                .stage(LenderAssociationStages.KYC_STATUS_CHECK.name())
                                .build());
                    }
                }
                log.info("Lender assoc at KYC for applicationId {}", currentDraftApplication.getId());

                String originalLaldKycStatus = lendingApplicationLenderDetails.getKycStatus();
                String lenderKycRedirectionUrl = getLenderKycRedirectionUrl(currentDraftApplication, lendingApplicationLenderDetails, lenderKycStatus);
                if (ObjectUtils.isEmpty(lenderKycRedirectionUrl) && eKycStatusCheckEnabledLenders.contains(lendingApplicationLenderDetails.getLender())) {
                    lenderKycRedirectionUrl = updateEKycDetails(currentDraftApplication, lendingApplicationLenderDetails, lenderKycRedirectionUrl);
                }
                ApiResponse<LenderAssociationStatusResponse> lenderAssociationStatusResponse = new ApiResponse<>(LenderAssociationStatusResponse.builder()
                        .status(LenderAssociationStatus.valueOf(Optional.ofNullable(lendingApplicationLenderDetails.getKycStatus()).orElse(LenderAssociationStatus.KYC_PENDING.name())))
                        .stage(LenderAssociationStages.KYC)
                        .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                        .lender(currentDraftApplication.getLender())
                        .lenderKycRedirectionUrl(lenderKycRedirectionUrl)
                        .prevLender(LenderAssociationStatus.EKYC_PENDING.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus()) ? getPrevLender(currentDraftApplication) : null)
                        .lenderKycRetry(LenderAssociationStatus.EKYC_RETRY.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus()))
                        .build());

                //If status polling is enabled for lender, then check the latest status of the polling
                if (statusPollEnabledLenders.contains(currentDraftApplication.getLender())
                        && LoanUtil.isRolledOutByPercentage(String.valueOf(currentDraftApplication.getMerchantId()), ekycStatusPollRolloutPercentage)) {
                    checkEkycStatusRetry(currentDraftApplication, lendingApplicationLenderDetails, lenderAssociationStatusResponse, userReturnedFromLenderKyc, originalLaldKycStatus);
                }

                return lenderAssociationStatusResponse;
            }
        }
        return new ApiResponse<>(false,"something went wrong");
    }

    private void checkEkycStatusRetry(LendingApplication currentDraftApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails,
                                      ApiResponse<LenderAssociationStatusResponse> lenderAssociationStatusResponse, boolean userReturnedFromLenderKyc, String originalLaldKycStatus) {
        if (ObjectUtils.isEmpty(currentDraftApplication) || ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            return;
        }
        Optional<NBFCRetry> nbfcRetryObj = nbfcRetryRepository.findByApplicationIdAndLenderAndRequestTypeAndStatus(currentDraftApplication.getId(),
                lendingApplicationLenderDetails.getLender(), LenderAssociationStages.EKYC_STATUS.name(), NbfcRetryStatus.INIT);
        if (!ApplicationStatus.DRAFT.name().equalsIgnoreCase(currentDraftApplication.getStatus())
        || !(LenderAssociationStages.KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())
                && validStages.contains(lendingApplicationLenderDetails.getKycStatus()))) {
             nbfcRequestRetryService.forceUpdateRetryStatus(nbfcRetryObj.orElse(null), NbfcRetryStatus.SUCCESS);
            return;
        }

        log.info("Checking for ekyc status retries for applicationId {}", currentDraftApplication.getId());
        if (nbfcRetryObj.isPresent()) {
            NBFCRetry nbfcRequestRetry = nbfcRetryObj.get();
            log.info("Ekyc Retry request found with id {} for application {}", nbfcRequestRetry.getId(), nbfcRequestRetry.getApplicationId());

            int retriesRemaining = nbfcRequestRetry.getRetriesRemaining();
            long retryDelaySeconds = ekycStatusRetryTimeoutsMap.getOrDefault(maxRetriesCount - retriesRemaining, 10L);
            long retryAfter = nbfcRetryObj.get().getUpdatedAt().getTime() + retryDelaySeconds * 1000L - System.currentTimeMillis();

            if (retryAfter > 0) {
                setRetryAfterInAssociationResponseMetadata(lenderAssociationStatusResponse, retryAfter, currentDraftApplication.getId());
            } else {
                log.info("Processing retry request for applicationId {}", currentDraftApplication.getId());
                nbfcRequestRetryService.processRetryRequest(currentDraftApplication, lendingApplicationLenderDetails, nbfcRequestRetry);
                if (NbfcRetryStatus.INIT.equals(nbfcRequestRetry.getStatus())) {
                    retriesRemaining = nbfcRequestRetry.getRetriesRemaining();
                    retryDelaySeconds = ekycStatusRetryTimeoutsMap.getOrDefault(maxRetriesCount - retriesRemaining, 10L);
                    retryAfter = nbfcRetryObj.get().getUpdatedAt().getTime() + retryDelaySeconds * 1000L - System.currentTimeMillis();
                    setRetryAfterInAssociationResponseMetadata(lenderAssociationStatusResponse, retryAfter, currentDraftApplication.getId());
                } else {
                    lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(currentDraftApplication.getId(), currentDraftApplication.getLender());
                    currentDraftApplication = lendingApplicationDao.findById(currentDraftApplication.getId()).orElse(currentDraftApplication);
                    LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(currentDraftApplication.getId());
                    modifyAssociationStatusResponse(lenderAssociationStatusResponse.getData(), currentDraftApplication, lendingApplicationDetails, lendingApplicationLenderDetails);
                    log.info("Updated association response : {}", lenderAssociationStatusResponse.getData());
                }

            }
        } else if (userReturnedFromLenderKyc){
            nbfcRetryObj = Optional.ofNullable(enqueueNbfcRetry(currentDraftApplication, LenderAssociationStages.EKYC_STATUS));
            if (nbfcRetryObj.isPresent()) {
                long retryDelaySeconds = ekycStatusRetryTimeoutsMap.getOrDefault(maxRetriesCount - nbfcRetryObj.get().getRetriesRemaining(), 10L);
                //Add retryDelaySeconds to nbfcRetryObj.get().getUpdatedAt and subtract current datetime to get the retryAfter value
                long retryAfter = nbfcRetryObj.get().getUpdatedAt().getTime() + retryDelaySeconds * 1000L - System.currentTimeMillis();
                setRetryAfterInAssociationResponseMetadata(lenderAssociationStatusResponse, retryAfter, currentDraftApplication.getId());

            }
        } else if (LenderAssociationStatus.EKYC_IN_PROGRESS.name().equals(originalLaldKycStatus)) {
            log.info("Resetting to EKYC-PENDING for application ID : {}", currentDraftApplication.getId());
            lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.EKYC_PENDING.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
            lenderAssociationStatusResponse.getData().setStatus(LenderAssociationStatus.valueOf(Optional.ofNullable(lendingApplicationLenderDetails.getKycStatus()).orElse(LenderAssociationStatus.KYC_PENDING.name())));
        } else {
            log.info("ekyc retry check not applicable for application ID : {}", currentDraftApplication.getId());
        }
    }

    private void setRetryAfterInAssociationResponseMetadata(ApiResponse<LenderAssociationStatusResponse> lenderAssociationStatusResponse, long retryAfter, long applicationId) {
        if (retryAfter > 0) {
            log.info("Retry to execute after {} ms for applicationId {}", retryAfter, applicationId);
            lenderAssociationStatusResponse.getData().setMetadata(LenderAssociationStatusResponse.LenderAssociationStatusResponseMetadata.builder()
                    .retryAfter((retryAfter / 1000) + retryTimerDelay)
                    .build());
        }
    }

    private void modifyAssociationStatusResponse(LenderAssociationStatusResponse associationStatusResponse, LendingApplication currentDraftApplication, LendingApplicationDetails lendingApplicationDetails, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        if (!ObjectUtils.isEmpty(associationStatusResponse)) {
            associationStatusResponse.setStatus(LenderAssociationStatus.valueOf(Optional.ofNullable(lendingApplicationLenderDetails.getKycStatus()).orElse(LenderAssociationStatus.KYC_PENDING.name())));
            associationStatusResponse.setStage(LenderAssociationStages.KYC);
            associationStatusResponse.setEdiModelModified(lendingApplicationDetails.getEdiModelModified());
            associationStatusResponse.setLender(currentDraftApplication.getLender());
            associationStatusResponse.setPrevLender(LenderAssociationStatus.EKYC_PENDING.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus()) ? getPrevLender(currentDraftApplication) : null);
            associationStatusResponse.setLenderKycRetry(LenderAssociationStatus.EKYC_RETRY.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus()));
        }
    }

    public Boolean checkForBPKycRequired(LendingApplication currentDraftApplication) {
        LendingApplicationLenderDetails prevLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(currentDraftApplication.getId(), Status.INACTIVE.name());
        if (ObjectUtils.isEmpty(prevLenderDetails) || nbfcUtils.bharatPeKycLenderAlreadyAssigned(currentDraftApplication.getId(), currentDraftApplication.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(currentDraftApplication.getLoanType()))
                || kycUtils.isELigibleForLenderKyc(currentDraftApplication.getLender(), currentDraftApplication.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(currentDraftApplication.getLoanType()))) {
            log.info("BP Kyc already done for applicationId {}", currentDraftApplication.getId());
            return false;
        }
        LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(currentDraftApplication.getId(), currentDraftApplication.getLender());
        if (!ObjectUtils.isEmpty(lendingApplicationKycDetails) && !ObjectUtils.isEmpty(lendingApplicationKycDetails.getConsentDate())) {
            log.info("BP Kyc already done for applicationId {} with lender {}", currentDraftApplication.getId(), currentDraftApplication.getLender());
            return false;
        }
        return true;
    }

    private String getLenderKycRedirectionUrl(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String lenderKycStatus) {
        try {
            if (kycUtils.isELigibleForLenderKyc(lendingApplicationLenderDetails.getLender(), lendingApplication.getMerchantId(),LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))) {
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
                    df.setRoundingMode(RoundingMode.DOWN);
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

    public ApiResponse<?> fetchTopupApplicationStatus(LendingApplication currentDraftApplication, String lenderKycStatus, boolean userReturnedFromLenderKyc) {
        log.info("Fetching topup loan application status for : {}", currentDraftApplication.getId());
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(currentDraftApplication.getId());
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            return new ApiResponse<>(false, "lending application details not found");
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(currentDraftApplication.getId(), Status.ACTIVE.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            InvokeLenderAssociationRequest invokeLenderAssociationRequest = new InvokeLenderAssociationRequest();
            invokeLenderAssociationRequest.setApplicationId(currentDraftApplication.getId());
            invokeLenderAssociationRequest.setStage(LenderAssociationStages.INIT.name());
            invokeLenderAssociationRequest.setForceEnable(false);
            initLenderAssociation(invokeLenderAssociationRequest);
        }
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lead creation triggered ! Please retry for status in few minutes");
            return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                    .status(LenderAssociationStatus.BRE_PENDING)
                    .stage(LenderAssociationStages.BRE)
                    .ediModelModified(false)
                    .lender(currentDraftApplication.getLender())
                    .build());
        } else if (LenderAssociationStages.COMPLETED.name().equalsIgnoreCase(getWrapperStage(lendingApplicationLenderDetails.getStage()))) {
            return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                    .status(LenderAssociationStatus.LENDER_ASSOCIATION_COMPLETED)
                    .stage(LenderAssociationStages.COMPLETED)
                    .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                    .lender(currentDraftApplication.getLender())
                    .build());
        } else if (LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
            return new ApiResponse<>(LenderAssociationStatusResponse.builder()
                    .status(LenderAssociationStatus.valueOf(Optional.ofNullable(lendingApplicationLenderDetails.getBreStatus()).orElse(LenderAssociationStatus.BRE_PENDING.name())))
                    .stage(LenderAssociationStages.BRE)
                    .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                    .lender(currentDraftApplication.getLender())
                    .build());
        } else if (LenderAssociationStages.KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
            String originalLaldKycStatus = lendingApplicationLenderDetails.getKycStatus();
            String lenderKycRedirectionUrl = getLenderKycRedirectionUrl(currentDraftApplication, lendingApplicationLenderDetails, lenderKycStatus);
            if(eKycStatusCheckEnabledLenders.contains(lendingApplicationLenderDetails.getLender()) && ObjectUtils.isEmpty(lenderKycRedirectionUrl)) {
                lenderKycRedirectionUrl = updateEKycDetails(currentDraftApplication, lendingApplicationLenderDetails, lenderKycRedirectionUrl);
            }
            ApiResponse<LenderAssociationStatusResponse> lenderAssociationStatusResponseApiResponse = new ApiResponse<>(LenderAssociationStatusResponse.builder()
                    .status(LenderAssociationStatus.valueOf(Optional.ofNullable(lendingApplicationLenderDetails.getKycStatus()).orElse(LenderAssociationStatus.KYC_PENDING.name())))
                    .stage(LenderAssociationStages.KYC)
                    .ediModelModified(lendingApplicationDetails.getEdiModelModified())
                    .lender(currentDraftApplication.getLender())
                    .lenderKycRedirectionUrl(lenderKycRedirectionUrl)
                    .lenderKycRetry(LenderAssociationStatus.EKYC_RETRY.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus()))
                    .build());

            //If status polling is enabled for lender, then check the latest status of the polling
            if (statusPollEnabledLenders.contains(currentDraftApplication.getLender())
                    && LoanUtil.isRolledOutByPercentage(String.valueOf(currentDraftApplication.getMerchantId()), ekycStatusPollRolloutPercentage)) {
                checkEkycStatusRetry(currentDraftApplication, lendingApplicationLenderDetails, lenderAssociationStatusResponseApiResponse, userReturnedFromLenderKyc, originalLaldKycStatus);
            }

            return lenderAssociationStatusResponseApiResponse;
        }
        return new ApiResponse<>(false, "something went wrong");
    }


    public ApiResponse<?> invokeStageForLender(InvokeStageRequestDTO invokeStageRequest) {
        try {
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(invokeStageRequest.getApplicationId());
            log.info("lending application {}", lendingApplication.get());
            if (ObjectUtils.isEmpty(lendingApplication.get())) {
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
                return new ApiResponse<>(false, "Something went wrong");
            }

            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender());

            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)){
                return new ApiResponse<>(false, "Something went wrong");
            }

            if(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt()<=0){
                return new ApiResponse<>(false, "Revised offer amount is less than 0");
            }

            if(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() >= lendingApplication.getLoanAmount()) {
                log.info("nbfcApprovedLoanOfferAmt is equal to loan amount for applicationId {}", lendingApplication.getId());
                return new ApiResponse<>(true, "Offer already modified");
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
            Double ediAmount = Math.ceil((lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() + interestAmt) / payableDays);
//            double initialDisbursalAmountWithoutProcessingFee = lendingApplication.getDisbursalAmount() + lendingApplication.getProcessingFee();
//            double processingFeeRate = lendingApplication.getProcessingFee()/initialDisbursalAmountWithoutProcessingFee;
//            double processingFee = Math.ceil(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() * processingFeeRate);
            BigDecimal processingFee;
            if(lendingApplication.getDisbursalAmount() != null && lendingApplication.getProcessingFee() != null && lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() != null){
                BigDecimal disbursalAmount = BigDecimal.valueOf(lendingApplication.getDisbursalAmount());
                BigDecimal processingFeeAmount = BigDecimal.valueOf(lendingApplication.getProcessingFee());
                BigDecimal initialDisbursalAmountWithoutProcessingFee = disbursalAmount.add(processingFeeAmount);
                BigDecimal processingFeeRate = processingFeeAmount.divide(initialDisbursalAmountWithoutProcessingFee, 10, RoundingMode.HALF_UP);
                BigDecimal nbfcApprovedLoanOfferAmt = BigDecimal.valueOf(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt());
                processingFee= nbfcApprovedLoanOfferAmt.multiply(processingFeeRate).setScale(0, RoundingMode.CEILING);
            }else{
                throw new NullPointerException("Either processing fee or disbursal amount or nbfc approved amount cannot be null");
            }


            lendingApplication.setProcessingFee(processingFee.doubleValue());
            lendingApplication.setLoanAmount(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt());
            lendingApplication.setRepayment(ediAmount * payableDays);
            lendingApplication.setDisbursalAmount(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() - processingFee.doubleValue());
            lendingApplication.setEdi(ediAmount);

            lendingApplicationDao.save(lendingApplication);
            // calling update loan for Trillions
            if(Lender.TRILLIONLOANS.name().equals(lendingApplication.getLender())){
                ApiResponse<?> response =invokeStageForLender(new InvokeStageRequestDTO(lendingApplication.getId(), lendingApplication.getLender(), "UPDATE_LOAN"));
                if(!response.success){
                    log.error("Update Lead failed for application:{}", lendingApplication.getId());
                }
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



            return new ApiResponse<>(true, "Offer successfully modified");
        } catch (Exception ex){
            log.info("Exception occurred while modifying offer for application:{}, {}, {}", applicationId, ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return new ApiResponse<>(false, "something went wrong");
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

                    LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender());
                    ModifiedOfferResponseDto.OfferDetails newOfferDetails = null;
                    Double approvedLoanOfferAmount = lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt();

                    if(offerModifiedEligibleLenders.contains(lendingApplication.getLender()) &&
                            !ObjectUtils.isEmpty(approvedLoanOfferAmount) && lendingApplication.getLoanAmount() > approvedLoanOfferAmount) {
                        LendingLenderPricing lendingLenderPricing = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColor(
                                lendingRiskVariablesSnapshot.getRiskSegment().name(),
                                lendingRiskVariablesSnapshot.getRiskGroup(),
                                lendingApplication.getTenureInMonths(),
                                lendingApplication.getLender(),
                                lendingRiskVariablesSnapshot.getPincodeColor().name(),
                                lendingApplication.getCreatedAt()
                        );

                        Double pfRate;
                        if(ObjectUtils.isEmpty(lendingLenderPricing)){
                            log.info("Lending lender pricing not available, using eligible loan values");
                            pfRate = eligibleLoan.get().getProcessingFeeRate();
                        } else {
                            pfRate = lendingLenderPricing.getProcessingFeeRate();
                        }

                        Double processingFee = Math.ceil((pfRate * approvedLoanOfferAmount) / 100);
                        Double interestAmt = (approvedLoanOfferAmount * (lendingApplication.getInterestRate() * lendingApplication.getTenureInMonths()) / 100) ;
                        Double ediAmount = Math.ceil((approvedLoanOfferAmount + interestAmt) / lendingApplication.getPayableDays());
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

    public NBFCResponseDTO<?> getStageDetails(InvokeStageRequestDTO invokeStageRequest) {
        try {
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(invokeStageRequest.getApplicationId());
            if (ObjectUtils.isEmpty(lendingApplication.get())) {
                log.error("No application found for {}", invokeStageRequest.getApplicationId());
                return NBFCResponseDTO.builder()
                        .applicationId(invokeStageRequest.getApplicationId().toString())
                        .lender(invokeStageRequest.getLender()).productName("LENDING")
                        .success(Boolean.FALSE).error("No application found").build();
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsDto = new LenderAssociationDetailsRequestDto();
            lenderAssociationDetailsDto.setApplicationId(lendingApplication.get().getId());
            lenderAssociationDetailsDto.setLendingApplication(lendingApplication.get());
            lenderAssociationDetailsDto.setMerchantId(lendingApplication.get().getMerchantId());
            lenderAssociationDetailsDto.setManageState(Boolean.TRUE);
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                    .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(), Status.ACTIVE.name(), lendingApplication.get().getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.error("Lending application lender details not found for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                return NBFCResponseDTO.builder()
                        .applicationId(lendingApplication.get().getId().toString())
                        .lender(invokeStageRequest.getLender()).productName("LENDING")
                        .success(Boolean.FALSE).error("Lending application lender details not found").build();
            }
            lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);

            LenderAssociationStages stage = LenderAssociationStages.valueOf(invokeStageRequest.getStage());
            return nbfcUtils.getStageDetails(lendingApplication.get().getLender(), lenderAssociationDetailsDto, stage);
        } catch (Exception e) {
            log.error("Exception in stage details {} of {} for applicationId {} {}", invokeStageRequest.getStage(), invokeStageRequest.getLender(), invokeStageRequest.getApplicationId(), Arrays.asList(e.getStackTrace()));
        }
        return NBFCResponseDTO.builder()
                .applicationId(invokeStageRequest.getApplicationId().toString())
                .lender(invokeStageRequest.getLender()).productName("LENDING")
                .success(Boolean.FALSE).error("Something went wrong").build();
    }

    private NBFCRetry enqueueNbfcRetry(LendingApplication lendingApplication, LenderAssociationStages associationStage) {
        if (ObjectUtils.isEmpty(lendingApplication)) {
            return null;
        }
        log.info("Creating a new nbfc retry request for applicationId: {}, lender: {} at stage: {}",
                lendingApplication.getId(), lendingApplication.getLender(), associationStage.name());
        NBFCRetry nbfcRetryRequest = null;
        try {
            nbfcRetryRequest = NBFCRetry.builder()
                    .merchantId(lendingApplication.getMerchantId())
                    .applicationId(lendingApplication.getId())
                    .requestType(associationStage.name())
                    .lender(lendingApplication.getLender())
                    .retriesRemaining(maxRetriesCount)
                    .status(NbfcRetryStatus.INIT)
                    .remarks(new LinkedHashMap<>())
                    .build();

            nbfcRetryRequest.setCreatedAt(new Date());
            nbfcRetryRequest.setUpdatedAt(new Date());
            nbfcRetryRepository.save(nbfcRetryRequest);
        } catch (Exception e) {
            log.error("Error while initiating retry for applicationId: {}, lender: {} at stage: {}",
                    lendingApplication.getId(), lendingApplication.getLender(), associationStage.name());
        }
        return nbfcRetryRequest;
    }

}
