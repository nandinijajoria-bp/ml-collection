package com.bharatpe.lending.lendingplatform.lms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoanDetailsResponse {

    private String bpLoanId;
    private Date date;
    private LoanSummary loanSummary;

    @Data
    @Builder
    public static class LoanSummary {
        private int loanAmount;
        private int disbursedAmount;
        private BigDecimal pendingPrincipal;
        private BigDecimal pendingInterest;
        private int pendingInstalmentCount;
        private BigDecimal pendingInstalmentAmount;
        private BigDecimal overduePrincipal;
        private BigDecimal overdueInterest;
        private int overdueInstalmentCount;
        private BigDecimal overdueInstalmentAmount;
        private BigDecimal overdueOtherCharges;
        private BigDecimal overdueBouncingCharges;
        private BigDecimal overduePenalInterest;
        private int excessPayable;
        private int excessReceivable;
        private int bouncingCount;
        private int totalDpd;
        private int interestDpd;
        private int principalDpd;
        private int otherChargesDpd;
        private String dpdSummary;
        private Date loanEndDate;
        private int paidInstalmentCount;
        private BigDecimal paidPrincipalAmount;
        private BigDecimal paidInterestAmount;
        private BigDecimal totalPaidAmount;
        private BigDecimal paidPenalCharges;
        private BigDecimal otherPaidCharges;
        private BigDecimal instalmentAmount;
        private String loanCurrentStatus;


    }
}