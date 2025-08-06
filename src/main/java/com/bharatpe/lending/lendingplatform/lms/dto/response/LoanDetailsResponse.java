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


        private static int safeBigDecimalToInt(BigDecimal value) {
            if (value == null) {
                return 0;
            }
            if (value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                return Integer.MAX_VALUE;
            }
            if (value.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0) {
                return Integer.MIN_VALUE;
            }
            return value.intValue();
        }

        public int getPendingPrincipalAsInt() {
            return safeBigDecimalToInt(pendingPrincipal);
        }
        public int getPendingInstalmentAmountAsInt() {
            return safeBigDecimalToInt(pendingInstalmentAmount);
        }
        public int getOverduePrincipalAsInt() {
            return safeBigDecimalToInt(overduePrincipal);
        }
        public int getOverdueInstalmentAmountAsInt() {
            return safeBigDecimalToInt(overdueInstalmentAmount);
        }
        public int getOverdueOtherChargesAsInt() {
            return safeBigDecimalToInt(overdueOtherCharges);
        }
        public int getOverdueBouncingChargesAsInt() {
            return safeBigDecimalToInt(overdueBouncingCharges);
        }
        public int getPaidPrincipalAmountAsInt() {
            return safeBigDecimalToInt(paidPrincipalAmount);
        }
        public int getPaidInterestAmountAsInt() {
            return safeBigDecimalToInt(paidInterestAmount);
        }
        public int getTotalPaidAmountAsInt() {
            return safeBigDecimalToInt(totalPaidAmount);
        }
        public int getPaidPenalChargesAsInt() {
            return safeBigDecimalToInt(paidPenalCharges);
        }
        public int getOtherPaidChargesAsInt() {
            return safeBigDecimalToInt(otherPaidCharges);
        }
        public int getInstalmentAmountAsInt() {
            return safeBigDecimalToInt(instalmentAmount);
        }
    }
}