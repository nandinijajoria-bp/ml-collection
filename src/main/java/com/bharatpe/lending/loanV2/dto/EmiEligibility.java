package com.bharatpe.lending.loanV2.dto;

import lombok.Data;

@Data
public class EmiEligibility {
    private Double emiLoanAmount;
    private Boolean emiRejected;
    private String rejectReason;
    private Integer emiEligibleIn;
}
