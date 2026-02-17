package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.enums.RejectionStage;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.dao.LenderDisbursalLimitsDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.entity.LendingLenderQuota;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.piramal.*;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.bharatpe.lending.constant.LendingConstants.OFFER_DOWNGRADE_PERCENTAGE;
import static com.bharatpe.lending.constant.LendingConstants.OFFER_DOWNGRADE_THRESHOLD;
import static com.bharatpe.lending.constant.RejectionReasons.LENDER_FAILED_BRE;

@Service
@Slf4j
public class RiskDecisionSyncService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderGateway iLenderGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private LoanUtil loanUtil;

    @Autowired
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Value("${topup.foreclosure.threshold.amount.check:10000}")
    private Double topupForecosureThreshodAmountCheck;

    @Value("${piramal.offer.downgrade.threshold:25.0}")
    private Double offerDowngradeThreshold;

    @Value("${piramal.bypass.offer.modification:false}")
    private Boolean bypassOfferModification;

    @Value("${topup.foreclosure.threshold.rollout:false}")
    private Boolean topupForecosureThreshodRollout;

    @Autowired
    LenderDisbursalLimitsDao lenderDisbursalLimitsDao;

    public boolean invokeRiskDecision(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.PiramalAssociationStages.RISK_DECISION.name());
            if (Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())
                    || Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                return false;
            }
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            NbfcRequestDto riskDecisionDto = getPayload(lenderAssociationDetailsRequestDto);
            NbfcResponseDto nbfcResponseDto = iLenderGateway.invokeStage(riskDecisionDto, LenderAssociationStages.PiramalAssociationStages.RISK_DECISION);
            log.info("risk decision response from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());

            RiskDecisionResponseDto riskDecisionResponseDto = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), RiskDecisionResponseDto.class);
            if (nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())
                    && Objects.nonNull(riskDecisionResponseDto.getRiskDecision()) && Objects.nonNull(riskDecisionResponseDto.getRiskDecision().getRiskDecisionType())
                    && riskDecisionResponseDto.getRiskDecision().getRiskDecisionType().equalsIgnoreCase("SANCTIONED")) {

                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setNbfcApprovedLoanOfferAmt(riskDecisionResponseDto.getRiskDecision().getApprovedLoanAmount());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setRoi(riskDecisionResponseDto.getRiskDecision().getRateOfInterest());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setTenure(riskDecisionResponseDto.getRiskDecision().getLoanTenor());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

                double requestedLoanAmount = lenderAssociationDetailsRequestDto.getLendingApplication().getLoanAmount();
                double approvedLoanAmount = lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getNbfcApprovedLoanOfferAmt();

                if(LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsRequestDto.getLendingApplication().getLoanType()) && topupForecosureThreshodRollout){
                    LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lenderAssociationDetailsRequestDto.getMerchantId(), "ACTIVE");
                    double foreclosureAmount = loanUtil.getForeClosureAmountForLender(lendingPaymentSchedule);
                    double newAmount = approvedLoanAmount - foreclosureAmount;
                    if(newAmount <= topupForecosureThreshodAmountCheck){
                        log.info("topup new Amount after subtracting nbfcAmount and foreclosure amount {} is less than threshold {}, rejecting applicationId: {}", newAmount, topupForecosureThreshodAmountCheck, lenderAssociationDetailsRequestDto.getLendingApplication().getId());
                        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.TOPUP_ELIGIBLE_AND_FORECLOSURE_AMOUNT_BELOW_THRESHOLD.name());
                        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                        lenderAssociationDetailsRequestDto.getLendingApplication().setRejectionReason(LENDER_FAILED_BRE);
                        lenderAssociationDetailsRequestDto.getLendingApplication().setRejectionStage(RejectionStage.BRE);
                        commonService.manageApplicationStateAndRejectApplication(lenderAssociationDetailsRequestDto);
                    }
                    return false;
                }

                // If approved loan amount is less than requested loan amount, proceed with downgrade checks
                if (approvedLoanAmount < requestedLoanAmount) {
                    // Perform additional lender downgrade checks
                    LendingApplication lendingApplication = lenderAssociationDetailsRequestDto.getLendingApplication();
                    LendingApplication newApplication = commonService.createDuplicateApplication(lendingApplication,lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails());
                    boolean downgradeChecksFailed = commonService.additionalLenderDowngradeChecksFailed(newApplication, lendingApplication.getLender());

                    // If downgrade checks fail, modify lender
                    if (downgradeChecksFailed) {
                        log.info("modifying lender for applicationId {}, as downgrade checks for apr and irr failed", lendingApplication.getId());
                        lenderAssociationDetailsRequestDto.setModifyLender(true);
                        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.APR_IRR_CHECK_FAILED.name());
                        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                        return false;
                    }

                    if(bypassOfferModification && !LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsRequestDto.getLendingApplication().getLoanType())){
                        boolean offerDowngradeThresholdCheckFailed = commonService.offerDowngradeThresholdChecksFailed(offerDowngradeThreshold, lenderAssociationDetailsRequestDto);
                        return !offerDowngradeThresholdCheckFailed;
                    }
                }

                return true;
            }
            //handling for async call
            if (nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())
                    && Objects.nonNull(riskDecisionResponseDto.getRiskDecision()) && Objects.nonNull(riskDecisionResponseDto.getRiskDecision().getRiskDecisionType())
                    && riskDecisionResponseDto.getRiskDecision().getRiskDecisionType().equalsIgnoreCase("PENDING")) {
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return false;
            }
        } catch (Exception e) {
            log.error("error while invoking risk decision lead for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
        return false;
    }
    private NbfcRequestDto getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        String leadId = lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId();
        Map<String, String> leadRequest = new HashMap<>();
        leadRequest.put("leadId", leadId);
        return NbfcRequestDto.builder()
                .applicationId(lenderAssociationDetailsRequestDto.getApplicationId())
                .payload(leadRequest)
                .lender("PIRAMAL")
                .productName("LENDING")
                .topup(LoanType.TOPUP.name().equals(lenderAssociationDetailsRequestDto.getLendingApplication().getLoanType()))
                .build();
    }
}
