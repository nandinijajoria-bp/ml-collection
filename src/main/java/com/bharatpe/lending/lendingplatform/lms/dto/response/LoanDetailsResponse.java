package com.bharatpe.lending.lendingplatform.lms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoanDetailsResponse {

    private String bpLoanId;

//    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date date;

    private LoanSummary loanSummary;

    @Data
    @Builder
    public static class LoanSummary {
        private int loanAmount;
        private int disbursedAmount;
        private int pendingPrincipal;
        private BigDecimal pendingInterest;
        private int pendingInstalmentCount;
        private int pendingInstalmentAmount;
        private int overduePrincipal;
        private BigDecimal overdueInterest;
        private int overdueInstalmentCount;
        private int overdueInstalmentAmount;
        private int overdueOtherCharges;
        private int overdueBouncingCharges;
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
        private int paidPrincipalAmount;
        private int paidInterestAmount;
        private int totalPaidAmount;
        private int paidPenalCharges;
        private int otherPaidCharges;
        private int instalmentAmount;
        private String loanCurrentStatus;
    }
}
