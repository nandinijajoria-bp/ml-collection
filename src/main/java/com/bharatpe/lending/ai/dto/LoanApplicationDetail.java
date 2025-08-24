package com.bharatpe.lending.ai.dto;

import com.bharatpe.lending.ai.enums.LoanApplicationStatus;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
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
