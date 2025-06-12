package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import com.bharatpe.lending.dto.CommonResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class ApplicationDetails {
    @NotBlank
    private String applicationId;
    @NotBlank
    private String customerId;
    @NotNull(message = "Price cannot be null")
    private BigDecimal loanAmount;
    @NotNull(message = "Price cannot be null")
    private BigDecimal disbursalAmount;
    @NotNull(message = "Price cannot be null")
    private BigDecimal processingFee;
    @NotNull(message = "Price cannot be null")
    private BigDecimal edi;
    @NotNull(message = "Price cannot be null")
    private BigDecimal monthlyInterest;
    @NotNull(message = "Price cannot be null")
    private BigDecimal annualInterest;
    @Min(value = 1, message = "Tenure cannot be less than 1")
    private int tenureInMonths;
    @Min(value = 1, message = "Tenure cannot be less than 1")
    private int tenureInDays;
    @NotBlank
    private String lender;
    private String bpLoanId;
    private String accountType;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date loanDisbursalDate;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date loanClosureDate;
    private String loanType;
    private String loanDisbursalReason;
    private String ckycStatus;
    private String category;
    @NotBlank
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date agreementAt;
    private String clientId;
    private String loanAccountNumber; //nbfcId
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date loanCreationTimestamp;
    private String txnId;
    private String accountId;
    private String loanId;
    private String leadId;
    private String agreementOtp;
    private String pennyDropAccountNumber;
    private String breStatus;
    private String dealId;
    private String dealNo;
    private String smbId;
    private String offerId;
    private String merchantCategory;
    private CommonResponse ediScheduleResponse;
}
