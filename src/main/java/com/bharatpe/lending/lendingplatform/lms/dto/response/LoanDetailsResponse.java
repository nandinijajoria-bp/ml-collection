package com.bharatpe.lending.lendingplatform.lms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

import static com.bharatpe.lending.lendingplatform.lms.util.ConversionUtil.safeBigDecimalToDouble;
import static com.bharatpe.lending.lendingplatform.lms.util.ConversionUtil.safeBigDecimalToInt;

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

        public BigDecimal calculateDuePenalty() {
            return (this.overdueBouncingCharges != null ? this.overdueBouncingCharges : BigDecimal.ZERO)
                    .add(this.overduePenalInterest != null ? this.overduePenalInterest : BigDecimal.ZERO);
        }

        public double calculateDuePenaltyAsDouble() {
            return calculateDuePenaltyAsDouble(false);
        }

        public double calculateDuePenaltyAsDouble(boolean ceil) {
            if(!ceil) {
                return safeBigDecimalToDouble(calculateDuePenalty());
            }
            return Math.ceil(safeBigDecimalToDouble(calculateDuePenalty()));
        }

        public double calculateDuePenaltyAsInt() {
            return safeBigDecimalToInt(calculateDuePenalty());
        }

    }
}