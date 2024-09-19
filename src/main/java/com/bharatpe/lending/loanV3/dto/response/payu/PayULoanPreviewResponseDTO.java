package com.bharatpe.lending.loanV3.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayULoanPreviewResponseDTO {

    private LoanTermsData loanTermsData;

    private Double loanTermInDays;

    private Double totalPrincipalDisbursed;

    private Double totalPrincipalExpected;

    private Double totalPrincipalPaid;

    private Double totalInterestCharged;

    private Double totalFeeChargesCharged;

    private Double totalPenaltyChargesCharged;

    private Double totalRepaymentExpected;

    private Double totalOutstanding;

    private Double brokenPeriodInterest;

    private Double netDisbursalAmount;

    private List<String> expectedMaturityDate;

    private Double chargesDueAtTimeOfDisbursement;

    private List<Periods> periods;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoanTermsData {

        private Double principal;

        private Double numberOfRepayments;

        private Double repayEvery;

        private Double interestRate;

        private Double calulatedIrr;

        private Double calculatedApr;

        private Double calculatedEmiAmount;

        private Double principalGrace;

        private Double interestPaymentGrace;

        private Double interestFreeGrace;

        private Double brokenPeriodInterest;

        private PeriodFrequencyType periodFrequencyType;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class PeriodFrequencyType{

            private Integer id;

            private String code;

            private String value;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Periods{

        private Double period;

        private List<String> fromDate;

        private List<String> dueDate;

        private Double daysInPeriod;

        private Double principalOriginalDue;

        private Double principalDue;

        private Double principalOutstanding;

        private Double principalLoanBalanceOutstanding;

        private Double interestOriginalDue;

        private Double interestDue;

        private Double interestOutstanding;

        private Double feeChargesDue;

        private Double penaltyChargesDue;

        private Double totalOriginalDueForPeriod;

        private Double totalDueForPeriod;

        private Double totalPaidForPeriod;

        private Double totalOutstandingForPeriod;

        private Double totalOverdue;

        private Double totalActualCostOfLoanForPeriod;

        private Double totalInstallmentAmountForPeriod;

        private String recalculatedInterestComponent;

        private LoanRepaymentPeriodComputationDetails loanRepaymentPeriodComputationDetails;

        private Double principalDisbursed;

        private Double feeChargesOutstanding;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class LoanRepaymentPeriodComputationDetails {
            private Map<String, String> calculatedOnAmountMap;
        }
    }
}
