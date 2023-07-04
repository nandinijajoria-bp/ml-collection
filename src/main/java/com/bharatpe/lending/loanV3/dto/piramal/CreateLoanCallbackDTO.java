package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateLoanCallbackDTO {
    private String loanAccountNumber;
    private String leadId;
    private Double loanAmount;
    private Double totalOutstandingAmount;
    private Double totalOutstandingPrincipal;
    private Double totalRepayAmount;
    private Double totalInterestPayable;
    private String loanStartDate;
    private String maturityDate;
    private Integer loanTenor;
    private Double rateOfInterest;
    private String firstEmiDate;
    private Double firstEmiAmount;
    private Boolean loanCreationSuccessful;
}
