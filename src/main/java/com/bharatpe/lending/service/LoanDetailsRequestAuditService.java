package com.bharatpe.lending.service;

import com.bharatpe.lending.dto.LoanDetailsEligibilityAuditDto;
import com.bharatpe.lending.dto.RequestResponseAuditDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.LoanDetailsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Service
@Slf4j
public class LoanDetailsRequestAuditService implements IRequestAudit<RequestResponseAuditDto,LoanDetailsEligibilityAuditDto> {

    @Autowired
    ObjectMapper objectMapper;

    public LoanDetailsEligibilityAuditDto refineAuditData(RequestResponseAuditDto payload) {
        LoanDetailsEligibilityAuditDto loanDetailsEligibilityAuditDto = new LoanDetailsEligibilityAuditDto(payload);
        ApiResponse<LoanDetailsResponse> apiResponse = null;
        try {
            apiResponse = objectMapper.readValue(payload.getResponse(), new TypeReference<ApiResponse<LoanDetailsResponse>>(){});
        } catch (Exception e) {
            log.error("exception occurred while auditing data {}", e.getMessage());
            return loanDetailsEligibilityAuditDto;
        }
        LoanDetailsResponse loanDetailsResponse = null;
        try {
            loanDetailsResponse = apiResponse.getData();
        } catch (Exception e) {
            log.error("exception occurred while parsing data {}", e.getMessage());
            return loanDetailsEligibilityAuditDto;
        }
        if (ObjectUtils.isEmpty(loanDetailsResponse)) {
            return loanDetailsEligibilityAuditDto;
        }
        if (!ObjectUtils.isEmpty(loanDetailsResponse.getLoanApplication()) &&
                ("pending_verification".equalsIgnoreCase(loanDetailsResponse.getLoanApplication().getApplicationStatus())
                        && loanDetailsResponse.getLoanApplication().getEnachDone()) ||
                ("approved".equalsIgnoreCase(loanDetailsResponse.getLoanApplication().getApplicationStatus()))) {
            log.info("ignoring this audit ! {}", payload.getRequestId());
            return null;
        }
        loanDetailsEligibilityAuditDto.setApplicationId(ObjectUtils.isEmpty(loanDetailsResponse.getLoanApplication()) ? null : loanDetailsResponse.getLoanApplication().getApplicationId());
        loanDetailsEligibilityAuditDto.setMerchantId(loanDetailsResponse.getMerchantId());
        loanDetailsEligibilityAuditDto.setIneligibility(loanDetailsResponse.getIneligible());
        loanDetailsEligibilityAuditDto.setSource(ObjectUtils.isEmpty(loanDetailsResponse.getSource()) ? "REQUEST" : loanDetailsResponse.getSource());
        if (!ObjectUtils.isEmpty(loanDetailsResponse.getEligibility())) {
            loanDetailsEligibilityAuditDto.setOfferAmt(loanDetailsResponse.getEligibility().getLoanAmount());
            loanDetailsEligibilityAuditDto.setOfferPrimaryKey(loanDetailsResponse.getEligibility().getUniqueKey());
            try {
                loanDetailsEligibilityAuditDto.setEligibility(objectMapper.writeValueAsString(loanDetailsResponse.getEligibility()));
            } catch (JsonProcessingException e) {
                log.info("parsing exception in loan details auditing {}",e.getMessage());
            }
        }
        if (!ObjectUtils.isEmpty(loanDetailsResponse.getLoanApplication())
                && "rejected".equalsIgnoreCase(loanDetailsResponse.getLoanApplication().getApplicationStatus())) {
            loanDetailsEligibilityAuditDto.setReapplyTimeline(loanDetailsResponse.getLoanApplication().getReapplyTime());
            loanDetailsEligibilityAuditDto.setRejectionReason(loanDetailsResponse.getLoanApplication().getRejectReason());
        }
        return loanDetailsEligibilityAuditDto;
    }

    @Override
    public String getEntityName() {
        return "lending_eligibility";
    }
}
