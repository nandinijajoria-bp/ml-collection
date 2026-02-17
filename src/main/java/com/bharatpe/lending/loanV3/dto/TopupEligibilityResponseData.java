package com.bharatpe.lending.loanV3.dto;

import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TopupEligibilityResponseData {
    private boolean success = true;
    private List<LoanEligibilityDTO> eligibility;
    private String message = "success";
    private Boolean topup = false;
    private String rejectionReason;
    private Boolean isRejected;
    private String topupLender;
    private Double minimumAllowedAmount;
    private Double maximumAllowedAmount;
    private List<String> tenures;
    private boolean isTopupV2FlowEnabled = false;
    private boolean showTopupSlider = false;
}
