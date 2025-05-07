package com.bharatpe.lending.lendingplatform.lms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoanForeclosureRequest {
    @NotBlank
    private String bpLoanId;
    private Long applicationId;
    private String lender;
    private String leadId;
    private String clientId;
    private String loanAccountNumber;
    @NotNull
    private Date date;
    private Date transactionDate;
    private String externalLmsId;
    private BigDecimal foreclosureAmount;
    private String transactionNumber;
    private String paymentMode;
}
