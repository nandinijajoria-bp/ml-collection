package com.bharatpe.lending.lendingplatform.lms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAsynchronousRequest {

    @NotBlank
    private String bpLoanId;

    @NotBlank
    private String applicationId;

    @NotNull
    private String lender;

    @NotBlank
    private String customerId;

    @Positive
    private BigDecimal amount;

    @NotBlank
    private String paymentMode;

    @NotBlank
    private String depositBankAccount;

    @NotBlank
    private String depositBankIfsc;

    @NotBlank
    private String issuingBankAccount;

    @NotBlank
    private String issuingBankIfsc;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date date;

    private String fundInRemarks;

    @NotBlank
    private String paymentStatus;

    @NotBlank
    private String transactionReferenceNo;

    private String lenderLoanAccountNumber;

    private String leadId;

    private String clientId;

    private boolean isEligibleForForeclosure;

    private BigDecimal foreclosureCharges;
}
