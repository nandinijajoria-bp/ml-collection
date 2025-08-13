package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV3.revamp.dto.EligibilityStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.OfferEvaluationRequestDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

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

    @Value("#{'ABFL,PIRAMAL,TRILLIONLOANS,MUTHOOT,CAPRI,PAYU,CREDITSAISON,SMFG,UGRO,OXYZO'.split(',')}")
    private List<String> activeLenders;

    @Override
    public LendingStateDTO<EligibilityStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<EligibilityStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        if (loanUtil.isApplicableForAggregationFlowV2(scopeDataArgs.getMerchant().getId(), null)){
            lendingStateDTO.setLendingViewStates(LendingViewStates.LENDER_AGGREGATION);
        } else if(lendingStateDTO.getData().getIsPreapprovedRepeatLoan()){
            lendingStateDTO.setLendingViewStates(LendingViewStates.KYC_PAGE);
        } else{
            lendingStateDTO.setLendingViewStates(LendingViewStates.SHOP_DETAILS_PAGE);
        }
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<EligibilityStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        try {
            trackFunnelEvent(String.valueOf(scopeDataArgs.getMerchant().getId()), FunnelEnums.StageEvent.INITIATED);

            OfferEvaluationRequestDTO requestData = collectRequestData(scopeDataArgs);

            if (requestData.getLendingApplication() == null) {
                log.info("Application not found for merchant: {}", scopeDataArgs.getMerchant().getId());
                return new LendingStateDTO<>(null, LendingViewStates.OFFER_EVALUATION_PAGE, LendingViewStates.OFFER_EVALUATION_PAGE);
            }

            if (isMaxLenderAttemptsReached(requestData)) {
                handleMaxLenderAttemptsReached(requestData.getLendingApplication(), String.valueOf(scopeDataArgs.getMerchant().getId()));
                return new LendingStateDTO<>(null, LendingViewStates.OFFER_EVALUATION_PAGE, LendingViewStates.OFFER_EVALUATION_PAGE);
            }

            EligibilityStateDTO eligibilityStateDTO = mapToEligibilityState(requestData);

            trackFunnelEvent(String.valueOf(scopeDataArgs.getMerchant().getId()), FunnelEnums.StageEvent.COMPLETED);

            return new LendingStateDTO<>(eligibilityStateDTO, LendingViewStates.OFFER_EVALUATION_PAGE, LendingViewStates.OFFER_EVALUATION_PAGE);
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
            builder.lendingApplication(lendingApplication)
                    .previousLenders(lendingApplicationLenderDetailsDao.findPreviousLenders(lendingApplication.getId()))
                    .isRepeatLoan(loanUtil.isRepeatLoan(lendingApplication.getMerchantId()))
                    .lenderAggregationScreen(loanUtil.getLenderAggregationScreen(lendingApplication.getId()));
        }

        return builder.build();
    }

    // Add these methods to OfferEvaluationStageDataService class

    private void trackFunnelEvent(String merchantId, FunnelEnums.StageEvent stageEvent) {
        try {
            funnelService.submitEventV3(Long.valueOf(merchantId),null, null,  FunnelEnums.StageId.OFFER_EVALUATION, stageEvent , null, null);
        } catch (Exception e) {
            log.error("Error tracking funnel event for merchant {}: {}", merchantId, e.getMessage());
        }
    }

    private boolean isMaxLenderAttemptsReached(OfferEvaluationRequestDTO requestData) {
        LendingApplication lendingApplication = requestData.getLendingApplication();
        if (lendingApplication == null) {
            return false;
        }
        return requestData.getPreviousLenders().size() >= activeLenders.size();
    }

    private void handleMaxLenderAttemptsReached(LendingApplication lendingApplication, String merchantId) {
        log.info("Max lender attempts reached for merchant: {}", merchantId);
        lendingApplication.setStatus(ApplicationStatus.REJECTED.name());
        lendingApplication.setRejectionReason("Max lender selection attempts reached");
        lendingApplication.setManualKyc(ApplicationStatus.REJECTED.name().toLowerCase());
        lendingApplication.setManualKycReason("NONE_ELIGIBLE_LENDER");
        lendingApplicationDao.save(lendingApplication);

        funnelService.submitEventV3(Long.valueOf(merchantId),null, null, FunnelEnums.StageId.OFFER_EVALUATION,
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
}
