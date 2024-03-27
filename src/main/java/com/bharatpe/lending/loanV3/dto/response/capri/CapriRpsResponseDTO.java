package com.bharatpe.lending.loanV3.dto.response.capri;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapriRpsResponseDTO {
     Integer id;
     String accountNo;
     Status status;
     Integer loanProductId;
     String loanProductName;
     Boolean isLoanProductLinkedToFloatingRate;
     LoanType loanType;
     Currency currency;
     Double principal;
     Double approvedPrincipal;
     Double proposedPrincipal;
     Double brokenPeriodInterest;
     Integer numberOfRepayments;
     Integer repaymentEvery;
     Boolean isFloatingInterestRate;
     Integer numberOfPaidRepayments;
     Integer numberOfDueRepayments;
     Integer transactionProcessingStrategyId;
     String transactionProcessingStrategyName;
     String transactionProcessingStrategyCode;
     Boolean isCancellationAllowed;
     Timeline timeline;
     RepaymentSchedule repaymentSchedule;
     Double feeChargesAtDisbursementCharged;
     Boolean multiDisburseLoan;
     Boolean canDisburse;
     List<Object> emiAmountVariations;
     Boolean isTopup;
     Boolean isInterestRecalculationEnabled;
     Boolean isVariableInstallmentsAllowed;
     BrokenPeriodMethodType brokenPeriodMethodType;
     Boolean considerFutureDisbursmentsInSchedule;
     Boolean considerAllDisbursementsInSchedule;
     Boolean isLocked;
     Boolean deferPaymentsForHalfTheLoanTerm;
     Boolean isClientVerified;
     Boolean allowsDisbursementToGroupBankAccounts;
     Boolean isDpConfigured;
     Boolean brokenPeriodInterestCollectAtDisbursement;
     Boolean isUpfrontInterestEnabled;
     List<Object> eventBasedCharges;
     Integer actualNumberOfRepayments;
     List<Object> anchors;
     String productType;
     Double currentInterestRate;
     LoanAdditionalDetailsData loanAdditionalDetailsData;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdditionalInterestComputationType{
         Integer id;
         String code;
         String value;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BrokenPeriodMethodType{
         Integer id;
         String code;
         String value;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Currency{
         String code;
         String name;
         Integer decimalPlaces;
         Integer inMultiplesOf;
         String nameCode;
         String displayLabel;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanAdditionalDetailsData{
         Integer id;
         Integer loanId;
         Boolean isFldg;
         AdditionalInterestComputationType additionalInterestComputationType;
         Integer courseOnInterestPayment;
         Integer additionalGraceOnInterestPayment;
         Integer courseOnPrincipalPayment;
         Integer additionalGraceOnPrincipalPayment;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanType{
         Integer id;
         String code;
         String value;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Period{
         List<Integer> dueDate;
         Double principalDisbursed;
         Double principalLoanBalanceOutstanding;
         Double feeChargesDue;
         Double feeChargesPaid;
         Double totalOriginalDueForPeriod;
         Double totalDueForPeriod;
         Double totalPaidForPeriod;
         Double totalActualCostOfLoanForPeriod;
         Boolean recalculatedInterestComponent;
         Integer period;
         List<Integer> fromDate;
         Boolean complete;
         Integer daysInPeriod;
         Double principalOriginalDue;
         Double principalDue;
         Integer principalPaid;
         Integer principalWrittenOff;
         Double principalOutstanding;
         Double interestOriginalDue;
         Double interestDue;
         Integer interestPaid;
         Integer interestWaived;
         Integer interestWrittenOff;
         Double interestOutstanding;
         Integer feeChargesWaived;
         Integer feeChargesWrittenOff;
         Integer feeChargesOutstanding;
         Integer penaltyChargesDue;
         Integer penaltyChargesPaid;
         Integer penaltyChargesWaived;
         Integer penaltyChargesWrittenOff;
         Integer penaltyChargesOutstanding;
         Integer totalPaidInAdvanceForPeriod;
         Integer totalPaidLateForPeriod;
         Integer totalWaivedForPeriod;
         Integer totalWrittenOffForPeriod;
         Double totalOutstandingForPeriod;
         Double totalOverdue;
         Double totalInstallmentAmountForPeriod;
         Integer advancePaymentAmount;
         Double interestAdjustedDueToGrace;
         Double interestAccruable;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepaymentSchedule{
         Currency currency;
         Integer loanTermInDays;
         Double totalPrincipalDisbursed;
         Double totalPrincipalExpected;
         Double totalPrincipalPaid;
         Double totalInterestCharged;
         Double totalFeeChargesCharged;
         Double totalPenaltyChargesCharged;
         Double totalWaived;
         Double totalWrittenOff;
         Double totalRepaymentExpected;
         Double totalRepayment;
         Double totalPaidInAdvance;
         Double totalPaidLate;
         Double totalOutstanding;
         Double totalAdvancePayment;
         List<Period> periods;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Status{
         Integer id;
         String code;
         String value;
         Boolean pendingApproval;
         Boolean waitingForDisbursal;
         Boolean active;
         Boolean closedObligationsMet;
         Boolean closedWrittenOff;
         Boolean closedRescheduled;
         Boolean closed;
         Boolean overpaid;
         Boolean transferInProgress;
         Boolean transferOnHold;
         Boolean underTransfer;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Timeline{ 
        List<Integer> expectedDisbursementDate;
        List<Integer> actualDisbursementDate;
    }

}
