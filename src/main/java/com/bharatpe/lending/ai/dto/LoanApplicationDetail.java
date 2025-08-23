package com.bharatpe.lending.ai.dto;

import com.bharatpe.lending.ai.enums.LoanApplicationStatus;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanApplicationDetail {
    private Long applicationId;
    private LoanApplicationStatus status;
    private Double amount;
    private Integer tenureInMonths;
    private Double monthlyInterestRate;
    private Double totalPayableAmount;
    private Double ediAmount;
    private LendingViewStates stage;
}
