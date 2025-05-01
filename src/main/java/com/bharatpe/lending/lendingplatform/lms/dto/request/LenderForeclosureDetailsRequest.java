package com.bharatpe.lending.lendingplatform.lms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
public class LenderForeclosureDetailsRequest {
    @NotBlank
    private String bpLoanId;
    @NotBlank
    private String lender;
    @NotBlank
    private String applicationId;
    @NotBlank
    private String leadId;
    private String clientId;
    private String loanAccountNumber;
    @NotNull
    private Date transactionDate;
    private BigDecimal outstandingPrinciple;
    private BigDecimal outstandingInterest;
}
