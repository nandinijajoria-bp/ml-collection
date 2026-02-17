package com.bharatpe.lending.dto.underwriting.read;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class LendingPaymentScheduleReadDTO {

    private Long merchantId;
    private Long merchantStoreId;
    private String loanType;
    private LendingApplicationReadDTO loanApplication;
    private Double loanAmount;
    private Double ediAmount;
    private Date startDate;
    private Integer ediCount;
    private Double interestOnlyEdiAmount;
    private Date interestOnlyStartDate;
    private Integer interestOnlyEdiCount;
    private Integer remainingInterestOnlyEdiCount;
    private Double overdueIntrestRate;
    private Integer overdueEdiCount;
    private Double overdueAmount;
    private Double incentiveAmount;
    private Integer ediRemainingCount;
    private Double dueAmount;
    private Double paidAmount;
    private Double totalCashbackAmount;
    private Double totalPenaltyAmount;
    private Date nextEdiDate;
    private String status;
    private String offDay;
    private Double totalPayableAmount;
    private String mobile;
    private String nbfc;
    private Date closingDate;
    private Date tentativeClosingDate;
    private String loanConstruct;
    private Double interest;
    private Double otherCharges;
    private Double duePrinciple;
    private Double dueInterest;
    private Double dueOtherCharges;
    private Double duePenalty;
    private Double paidPrinciple;
    private Double paidInterest;
    private Double paidOtherCharges;
    private Double paidPenalty;
    private String lenderDisbursalNotify;
    private Long id;
    private Date createdAt;
    private Date updatedAt;
    private Integer disbursalSettlementId;
    private Boolean creditLoan;
    private Long tlDetailsId;
    private Double adjustedDueAmount;
    private Double adjustedPaidAmount;
    private String settlementStatus;
    private String lmsSource;

}
