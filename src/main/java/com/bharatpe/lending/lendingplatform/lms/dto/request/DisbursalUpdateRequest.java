package com.bharatpe.lending.lendingplatform.lms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DisbursalUpdateRequest {

    @NotBlank
    private String bpLoanId;

    @NotBlank
    private String externalLmsId;

    @Positive
    private double loanAmount;

    @NotNull
    private Date disbursalDate;

    @Positive
    private double roi;

    @Positive
    private double interestRate;

    @NotBlank
    private String paymentSource;

    @NotBlank
    private String paymentId;

    @NotBlank
    private String paymentMode;

    @Positive
    private double transactionAmount;

    @NotNull
    private Date transactionDate;

    @NotBlank
    private String transactionId;

    @NotBlank
    private String remarks;

    @Positive
    private int entityId;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date date;

    @Positive
    private double issueingBankAccount;

    @Positive
    private int issuingBankId;

    @NotBlank
    private String issuingBankIfsc;

    @Positive
    private double depositBankAccount;

    @Positive
    private int depositBankId;

    @NotBlank
    private String depositBankIfsc;

    @NotBlank
    private String isDeletedFlag;

    @NotBlank
    private String startEdiFlag;

    @Positive
    private int loanDisbursalId;
}
