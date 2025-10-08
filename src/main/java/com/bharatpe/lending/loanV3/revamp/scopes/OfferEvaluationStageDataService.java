package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.Eligibility;
import com.bharatpe.lending.loanV3.revamp.dto.EligibilityStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.OfferEvaluationRequestDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.services.EligibilityV3Service;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.services.LendingApplicationServiceV3Base;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.LoanEligibleService;
import com.bharatpe.lending.util.AsyncLoggerUtil;
import com.bharatpe.lending.util.CommonUtil;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;

@Component
@Slf4j
public class OfferEvaluationStageDataService implements IStageDataService<EligibilityStateDTO>{

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    FunnelService funnelService;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    EligibilityV3Service eligibilityV3Service;

    @Autowired
    CommonUtil commonUtil;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    LendingApplicationServiceV3Base lendingApplicationServiceV3Base;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    @Value("#{'${active.lenders:ABFL,PIRAMAL,TRILLIONLOANS,MUTHOOT,PAYU,CREDITSAISON,SMFG,UGRO,OXYZO}'.split(',')}")
    private List<String> activeLenders;

    @Override
    public LendingStateDTO<EligibilityStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<EligibilityStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<EligibilityStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        try {
            trackFunnelEvent(String.valueOf(scopeDataArgs.getMerchant().getId()), FunnelEnums.StageEvent.INITIATED);

            OfferEvaluationRequestDTO requestData = collectRequestData(scopeDataArgs);

            EligibilityStateDTO eligibilityStateDTO = mapToEligibilityState(requestData);
            eligibilityStateDTO.setMerchant(scopeDataArgs.getMerchant());
            //set merchant Id
            eligibilityStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
            eligibilityStateDTO.setAccountDetails(loanUtil.getAccountDetails(scopeDataArgs.getMerchant().getId()));
            Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
            if (experian != null) {
                eligibilityStateDTO.setPancard(experian.getPancardNumber());
                eligibilityStateDTO.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
                eligibilityStateDTO.setHasExperian(true);
                eligibilityStateDTO.setExperian(experian);
            }
            eligibilityStateDTO.setKycPanStatus(kycHandler.getPanStatus(scopeDataArgs.getMerchant().getId()));

            eligibilityStateDTO.setBpClubMember(apiGatewayService.eligibleForProcessingFee(scopeDataArgs.getMerchant().getId()));

            Long applicationId = requestData.getLendingApplication() != null ?
                    requestData.getLendingApplication().getId() : null;
            Long merchantId = scopeDataArgs.getMerchant().getId();

            String screenType = loanUtil.getLenderAggregationScreenV2(applicationId, merchantId);

            log.info("Lender Aggregation Screen for merchantId {} is {}", merchantId, screenType);
            eligibilityStateDTO.setScreenType(screenType);

            if (scopeDataArgs.getLoanDetailsV3Request() != null ) {
                eligibilityV3Service.fetchEligibility(scopeDataArgs.getLoanDetailsV3Request(), eligibilityStateDTO);
                Eligibility eligibility = eligibilityStateDTO.getEligibility();
                if(requestData.getLendingRiskVariablesSnapshot()!=null && requestData.getLendingRiskVariablesSnapshot().getRegularLimit() != null) {
                    eligibility.setMaxOfferAmount(requestData.getLendingRiskVariablesSnapshot().getRegularLimit());
                }

            } else {
                log.warn("LoanDetailsV3Request is null for merchant: {}", scopeDataArgs.getMerchant().getId());
            }

            if (isMaxLenderAttemptsReached(requestData, scopeDataArgs.getMerchant().getId())) {
                handleMaxLenderAttemptsReached(requestData.getLendingApplication(), String.valueOf(scopeDataArgs.getMerchant().getId()));
                return new LendingStateDTO<>(eligibilityStateDTO, LendingViewStates.OFFER_EVALUATION_PAGE, LendingViewStates.OFFER_EVALUATION_PAGE);
            }

            trackFunnelEvent(String.valueOf(scopeDataArgs.getMerchant().getId()), FunnelEnums.StageEvent.COMPLETED);

            LendingViewStates nextState = requestData.getLendingApplication() != null ?
                    getNextLendingViewState(requestData.getLendingApplication()) :
                    LendingViewStates.OFFER_EVALUATION_PAGE;

            return new LendingStateDTO<>(eligibilityStateDTO, nextState, LendingViewStates.OFFER_EVALUATION_PAGE);

        } catch (Exception e) {
            log.error("Error in getting offer stage data for {}: {}, {}",
                    scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            EligibilityStateDTO errorState = new EligibilityStateDTO();
            errorState.setErrorString("Something went wrong");
            return new LendingStateDTO<>(errorState, LendingViewStates.OFFER_EVALUATION_PAGE, LendingViewStates.OFFER_EVALUATION_PAGE);
        }
    }

    private OfferEvaluationRequestDTO collectRequestData(ScopeDataArgs scopeDataArgs) {
        OfferEvaluationRequestDTO.OfferEvaluationRequestDTOBuilder builder = OfferEvaluationRequestDTO.builder()
                .merchant(scopeDataArgs.getMerchant())
                .merchantId(String.valueOf(scopeDataArgs.getMerchant().getId()))
                .accountDetails(loanUtil.getAccountDetails(scopeDataArgs.getMerchant().getId()))
                .kycStatus(kycHandler.getPanStatus(scopeDataArgs.getMerchant().getId()));

        // Get Experian data if available
        Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
        if (experian != null) {
            builder.experianData(experian);
        }

        // Get lending application
        LendingApplication lendingApplication = lendingApplicationDao.findByMerchantIdAndStatus(
                scopeDataArgs.getMerchant().getId(), "draft");

        if (lendingApplication != null) {
            LendingRiskVariablesSnapshot riskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            builder.lendingApplication(lendingApplication)
                    .previousLenders(lendingApplicationLenderDetailsDao.findPreviousLenders(lendingApplication.getId()))
                    .isRepeatLoan(loanUtil.isRepeatLoan(lendingApplication.getMerchantId()))
                    .lenderAggregationScreen(loanUtil.getLenderAggregationScreenV2(lendingApplication.getId(), lendingApplication.getMerchantId()))
                    .lendingRiskVariablesSnapshot(riskVariablesSnapshot);
        }

        return builder.build();
    }

    // Add these methods to OfferEvaluationStageDataService class

    private void trackFunnelEvent(String merchantId, FunnelEnums.StageEvent stageEvent) {
        try {
            funnelService.submitEventV3(Long.valueOf(merchantId),null, null,  FunnelEnums.StageId.OFFER_EVALUATION_PAGE, stageEvent , null, null);
        } catch (Exception e) {
            log.error("Error tracking funnel event for merchant {}: {}", merchantId, e.getMessage());
        }
    }

    private boolean isMaxLenderAttemptsReached(OfferEvaluationRequestDTO requestData, Long merchantId) {
        LendingApplication lendingApplication = requestData.getLendingApplication();
        if (lendingApplication == null) {
            return false;
        }
        LendingAuditTrial eligibleLenders = lendingAuditTrialDao.findTopByApplicationIdAndMerchantIdAndLoanAmountAndTenureAndTypeOrderByIdDesc(
                lendingApplication.getId(), merchantId, lendingApplication.getLoanAmount(), lendingApplication.getTenureInMonths(), "ELIGIBLE_LENDERS");

        log.info("Eligible lenders audit trial for application {}: {}", lendingApplication.getId(), eligibleLenders != null ? eligibleLenders.getRemarks() : "None");

        List<LendingAuditTrial> removedLender = lendingAuditTrialDao.findAllByApplicationIdAndMerchantIdAndLoanAmountAndTypeOrderByIdDesc(
                lendingApplication.getId(), merchantId, lendingApplication.getLoanAmount(), "LENDER_REMOVED");

        log.info("Removed lenders audit trials for application {}: {}", lendingApplication.getId(), !CollectionUtils.isEmpty(removedLender) ? removedLender : 0);

        Set<String> distinctRemovedLenders = new HashSet<>();
        if (eligibleLenders != null && !CollectionUtils.isEmpty(removedLender)) {
            for (LendingAuditTrial lender : removedLender) {
                if (lender.getRemarks() != null) {
                    distinctRemovedLenders.add(lender.getOldStatus());
                }
            }
            log.info("Found {} distinct removed lenders for merchant {}", distinctRemovedLenders, merchantId);
        }

        if (!CollectionUtils.isEmpty(distinctRemovedLenders) && eligibleLenders != null &&
                eligibleLenders.getRemarks() != null) {

            String[] eligibleLendersList = eligibleLenders.getRemarks().split(",");
            boolean allLendersRemoved = true;

            for (String lender : eligibleLendersList) {
                String trimmedLender = lender.trim();
                if (!distinctRemovedLenders.contains(trimmedLender)) {
                    allLendersRemoved = false;
                    break;
                }
            }

            if (allLendersRemoved) {
                log.info("All eligible lenders have been removed for merchant {}", merchantId);
                return true;
            }
        }

        return false;
    }

    private void handleMaxLenderAttemptsReached(LendingApplication lendingApplication, String merchantId) {
        log.info("Max lender attempts reached for merchant: {}", merchantId);
        lendingApplication.setStatus(ApplicationStatus.REJECTED.name().toLowerCase());
        lendingApplication.setRejectionReason("Max lender selection attempts reached");
        lendingApplication.setManualKyc(ApplicationStatus.REJECTED.name().toLowerCase());
        lendingApplication.setManualKycReason("NONE_ELIGIBLE_LENDER");
        lendingApplicationDao.save(lendingApplication);

        funnelService.submitEventV3(Long.valueOf(merchantId),null, lendingApplication.getId(), FunnelEnums.StageId.OFFER_EVALUATION_PAGE,
                FunnelEnums.StageEvent.REJECTED, null, null);
    }

    private EligibilityStateDTO mapToEligibilityState(OfferEvaluationRequestDTO requestData) {
        EligibilityStateDTO stateDTO = new EligibilityStateDTO();

        stateDTO.setMerchant(requestData.getMerchant());
        stateDTO.setMerchantId(Long.parseLong(requestData.getMerchantId()));

        stateDTO.setAccountDetails(requestData.getAccountDetails());

        stateDTO.setKycPanStatus(requestData.getKycStatus());

        if (requestData.getExperianData() != null) {
            stateDTO.setExperian(requestData.getExperianData());
            stateDTO.setHasExperian(true);
        }

        if (requestData.getLendingApplication() != null) {
            LendingApplication application = requestData.getLendingApplication();
            stateDTO.setLendingApplication(application);
        }

        return stateDTO;
    }

    private LendingViewStates getNextLendingViewState(LendingApplication lendingApplication) {
        if (lendingApplication == null) {
            return LendingViewStates.OFFER_EVALUATION_PAGE;
        }
        boolean isAddressPresent = commonUtil.doesApplicationHaveCompleteAddress(lendingApplication);
        if (!isAddressPresent) {
            return LendingViewStates.SHOP_DETAILS_PAGE;
        }
        boolean hasValidShopPhotos = lendingShopDocumentsDao.hasValidProofTypes(
                lendingApplication.getMerchantId(),
                lendingApplication.getId()
        ) > 0;
        if(!hasValidShopPhotos)
        {
            return LendingViewStates.SHOP_PICTURES_PAGE;
        }

        Boolean bpKycRequired = lendingApplicationServiceV3Base.checkForBPKycRequired(lendingApplication, LenderAssociationStages.INIT);

        return bpKycRequired ? LendingViewStates.KYC_PAGE : LendingViewStates.LENDER_EVALUATION_PAGE;
    }

}
