package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class RiskDecisionSyncService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderGateway iLenderGateway;

    @Autowired
    ObjectMapper objectMapper;

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
                .build();
    }
}
