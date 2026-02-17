package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.enums.RejectionStage;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LenderDisbursalLimitsDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.entity.LendingLenderQuota;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.bharatpe.lending.constant.LendingConstants.OFFER_DOWNGRADE_PERCENTAGE;
import static com.bharatpe.lending.constant.LendingConstants.OFFER_DOWNGRADE_THRESHOLD;
import static com.bharatpe.lending.constant.RejectionReasons.LENDER_FAILED_BRE;


@Service
@Slf4j
public class RiskDecisionAsyncService {

    @Autowired
    CommonService commonService;

    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    private LendingPaymentScheduleDao lendingPaymentScheduledDao;

    @Autowired
    private LoanUtil loanUtil;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Value("${lender.change.enabled:false}")
    private Boolean enableLenderChange;

    @Value("${piramal.offer.downgrade.threshold:25.0}")
    private Double offerDowngradeThreshold;

    @Value("${piramal.bypass.offer.modification:false}")
    private Boolean bypassOfferModification;

    @Value("${topup.foreclosure.threshold.amount.check:10000}")
    private Double topupForecosureThreshodAmountCheck;

    @Value("${topup.foreclosure.threshold.rollout:false}")
    private Boolean topupForecosureThreshodRollout;


    @Autowired
    LenderDisbursalLimitsDao lenderDisbursalLimitsDao;


    public void invokeRiskDecision(NbfcResponseDto nbfcResponseDto) {

        log.info("risk decision response from nbfc: {} with applicationId: {}", nbfcResponseDto, nbfcResponseDto.getApplicationId());
        long applicationId = Long.parseLong(nbfcResponseDto.getApplicationId());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(), Lender.PIRAMAL.name());

        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if (!lendingApplicationOptional.isPresent()) {
            log.info("lending application not found for applicationId: {}", applicationId);
            return;
        }
        if (Arrays.asList(LenderAssociationStatus.RISK_FAILED.name(),LenderAssociationStatus.RISK_COMPLETED.name()).contains(lendingApplicationLenderDetails.getBreStatus())) {
            log.info("Need not to acknowledge callback since risk status is not in progress for applicationId: {}", applicationId);
            return;
        }
        LendingApplication lendingApplication = lendingApplicationOptional.get();
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = LenderAssociationDetailsRequestDto.builder()
                .applicationId(applicationId)
                .merchantId(lendingApplication.getMerchantId())
                .lendingApplication(lendingApplication)
                .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                .modifyLender(enableLenderChange)
                .manageState(Boolean.TRUE)
                .build();
        try {
            RiskDecisionResponseDto riskDecisionResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), RiskDecisionResponseDto.class);
            if (nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())
                    && Objects.nonNull(riskDecisionResponseDto.getRiskDecision()) && Objects.nonNull(riskDecisionResponseDto.getRiskDecision().getRiskDecisionType())
                    && riskDecisionResponseDto.getRiskDecision().getRiskDecisionType().equalsIgnoreCase("SANCTIONED")
                    && riskDecisionResponseDto.getLeadId().equalsIgnoreCase(lendingApplicationLenderDetails.getLeadId())) {

                log.info("Risk decision sanctioned for applicationId: {}", applicationId);
                lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                lendingApplicationLenderDetails.setNbfcApprovedLoanOfferAmt(riskDecisionResponseDto.getRiskDecision().getApprovedLoanAmount());
                lendingApplicationLenderDetails.setRoi(riskDecisionResponseDto.getRiskDecision().getRateOfInterest());
                lendingApplicationLenderDetails.setTenure(riskDecisionResponseDto.getRiskDecision().getLoanTenor());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

                double requestedLoanAmount = lendingApplication.getLoanAmount();
                double approvedLoanAmount = lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt();

                if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()) && topupForecosureThreshodRollout){
                    LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduledDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchantId(), "ACTIVE");
                    double foreclosureAmount = loanUtil.getForeClosureAmountForLender(lendingPaymentSchedule);
                    double newAmount = approvedLoanAmount - foreclosureAmount;
                    if(newAmount <= topupForecosureThreshodAmountCheck){
                        log.info("topup new Amount after subtracting nbfcAmount and foreclosure amount {} is less than threshold {}, rejecting applicationId: {}", newAmount, topupForecosureThreshodAmountCheck, applicationId);
                        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.TOPUP_ELIGIBLE_AND_FORECLOSURE_AMOUNT_BELOW_THRESHOLD.name());
                        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                        lendingApplication.setRejectionReason(LENDER_FAILED_BRE);
                        lendingApplication.setRejectionStage(RejectionStage.BRE);
                        lenderAssociationDetailsRequestDto.setLendingApplication(lendingApplication);
                        commonService.manageApplicationStateAndRejectApplication(lenderAssociationDetailsRequestDto);
                    }
                    return;
                }
                // If approved loan amount is less than requested loan amount, proceed with downgrade checks
                if (approvedLoanAmount < requestedLoanAmount) {
                    LendingApplication newApplication = commonService.createDuplicateApplication(lendingApplication,lendingApplicationLenderDetails);

                    // Perform additional lender downgrade checks
                    boolean downgradeChecksFailed = commonService.additionalLenderDowngradeChecksFailed(newApplication, lendingApplication.getLender());

                    // If downgrade checks fail, modify lender
                    if (downgradeChecksFailed) {
                        log.info("modifying lender for applicationId {}, as downgrade checks for apr and irr failed", lendingApplication.getId());
                        lenderAssociationDetailsRequestDto.setModifyLender(true);
                        lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.ValidationStatus.APR_IRR_CHECK_FAILED.name());
                        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                        return;
                    }

                    if(bypassOfferModification && !LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsRequestDto.getLendingApplication().getLoanType())){
                        boolean offerDowngradeThresholdCheckFailed = commonService.offerDowngradeThresholdChecksFailed(offerDowngradeThreshold, lenderAssociationDetailsRequestDto);
                        if(offerDowngradeThresholdCheckFailed) return;
                    }
                }


                //pushing to next stage
                String currStage =  lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getStage();
                LenderAssociationStages nextStage =
                        LenderAssociationStageFactory.getNextStage(Lender.valueOf(lenderAssociationDetailsRequestDto.getLendingApplication().getLender()),
                                LenderAssociationStages.valueOf(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getStage()));
                log.info("curr stage: {} and next stage: {} in risk async service for applicationId: {}",currStage, nextStage, applicationId);
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setStage(nextStage.name());
                nbfcUtils.pushApplicationToNextStage(lenderAssociationDetailsRequestDto.getApplicationId(),
                        lenderAssociationDetailsRequestDto.getLendingApplication().getLender(),
                        currStage,
                        LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lenderAssociationDetailsRequestDto.getLendingApplication().getLender()), LenderAssociationStages.valueOf(currStage)));
                return;

            }

        } catch (Exception e) {
            log.error("exception while risk descision callback handling for applicationId: {} {} {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
        return;
    }

}
