package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;


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
    LendingApplicationDao lendingApplicationDao;

    @Value("${lender.change.enabled:false}")
    private Boolean enableLenderChange;


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

                if(lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt() < lendingApplication.getLoanAmount()) {
                    log.info("modifying lender for applicationId {}, as nbfc approved loan amount {} is less than loan amount {}",
                            lendingApplication.getId(),lendingApplicationLenderDetails.getNbfcApprovedLoanOfferAmt(), lendingApplication.getLoanAmount());
                    commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                    return;
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
