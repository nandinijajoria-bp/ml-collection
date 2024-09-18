package com.bharatpe.lending.loanV3.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayURpsResponseDTO {

    private Currency currency;

    private Integer loanTermInDays;
    private Number totalPrincipalDisbursed;
    private Number totalPrincipalExpected;
    private Number totalPrincipalPaid;
    private Number totalInterestCharged;
    private Number totalFeeChargesCharged;
    private Number totalPenaltyChargesCharged;
    private Number totalWaived;
    private Number totalWrittenOff;
    private Number totalRepaymentExpected;
    private Number totalRepayment;
    private Number totalPaidInAdvance;
    private Number totalPaidLate;
    private Number totalOutstanding;
    private Number totalAdvancePayment;
    private List<Periods> periods;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Periods {
        private List<Integer>dueDate;
        private Number principalDisbursed;
        private Number principalLoanBalanceOutstanding;
        private Number feeChargesDue;
        private Number feeChargesPaid;
        private Number totalOriginalDueForPeriod;
        private Number totalDueForPeriod;
        private Number totalPaidForPeriod;
        private Number totalActualCostOfLoanForPeriod;
        private Boolean recalculatedInterestComponent;
        private Integer period;
        private List<Integer>fromDate;
        private Boolean complete;
        private Integer daysInPeriod;
        private Number principalOriginalDue;
        private Number principalDue;
        private Number principalPaid;
        private Number principalWrittenOff;
        private Number principalOutstanding;
        private Number interestOriginalDue;
        private Number interestDue;
        private Number interestPaid;
        private Number interestWaived;
        private Number interestWrittenOff;
        private Number interestOutstanding;
        private Number feeChargesWaived;
        private Number feeChargesWrittenOff;
        private Number feeChargesOutstanding;
        private Number penaltyChargesDue;
        private Number penaltyChargesPaid;
        private Number penaltyChargesWaived;
        private Number penaltyChargesWrittenOff;
        private Number penaltyChargesOutstanding;
        private Number totalPaidInAdvanceForPeriod;
        private Number totalPaidLateForPeriod;
        private Number totalWaivedForPeriod;
        private Number totalWrittenOffForPeriod;
        private Number totalOutstandingForPeriod;
        private Number totalInstallmentAmountForPeriod;
        private Number advancePaymentAmount;
        private List<Integer>obligationsMetOnDate;
        private Number totalOverdue;
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Currency {

        private String code;
        private String name;
        private Integer decimalPlaces;
        private Integer inMultiplesOf;
        private String nameCode;
        private String displayLabel;
    }
}
