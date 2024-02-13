package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLRpsResponseDto {
    private Long id;
    private String accountNo;
    private LoanStatus status;
    private Long loanProductId;
    private String loanProductName;
    private Boolean isLoanProductLinkedToFloatingRate;
    private LoanType loanType;
    private CurrencyDTO currency;
    private Double principal;
    private Double approvedPrincipal;
    private Double proposedPrincipal;
    private Double brokenPeriodInterest;
    private Integer numberOfRepayments;
    private Integer repaymentEvery;
    private Boolean isFloatingInterestRate;
    private Integer numberOfPaidRepayments;
    private Integer numberOfDueRepayments;
    private Long transactionProcessingStrategyId;
    private String transactionProcessingStrategyName;
    private String transactionProcessingStrategyCode;
    private Boolean isCancellationAllowed;
    private Timeline timeline;
    private RepaymentSchedule repaymentSchedule;
    private OriginalSchedule originalSchedule;
    private Double feeChargesAtDisbursementCharged;
    private Boolean multiDisburseLoan;
    private Boolean canDisburse;
    private List<Object> emiAmountVariations;
    private Boolean isTopup;
    private Boolean isInterestRecalculationEnabled;
    private Boolean isVariableInstallmentsAllowed;
    private BrokenPeriodMethodType brokenPeriodMethodType;
    private Boolean considerFutureDisbursmentsInSchedule;
    private Boolean considerAllDisbursementsInSchedule;
    private Boolean isLocked;
    private Boolean deferPaymentsForHalfTheLoanTerm;
    private Boolean isClientVerified;
    private Boolean allowsDisbursementToGroupBankAccounts;
    private Boolean isDpConfigured;
    private Boolean brokenPeriodInterestCollectAtDisbursement;
    private Boolean isUpfrontInterestEnabled;
    private List<ScheduleHistoryData> scheduleHistoryDataList;
    private List<Object> eventBasedCharges;
    private Integer actualNumberOfRepayments;
    private List<Object> anchors;
    private String productType;
    private Double currentInterestRate;
    private LoanAdditionalDetailsData loanAdditionalDetailsData;


    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanStatus {
        private Long id;
        private String code;
        private String value;
        private Boolean pendingApproval;
        private Boolean waitingForDisbursal;
        private Boolean active;
        private Boolean closedObligationsMet;
        private Boolean closedWrittenOff;
        private Boolean closedRescheduled;
        private Boolean closed;
        private Boolean overpaid;
        private Boolean transferInProgress;
        private Boolean transferOnHold;
        private Boolean underTransfer;

    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanType {
        private Long id;
        private String code;
        private String value;

    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CurrencyDTO {
        private String code;
        private String name;
        private Integer decimalPlaces;
        private Integer inMultiplesOf;
        private String nameCode;
        private String displayLabel;

    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Timeline {
        private List<Integer> expectedDisbursementDate;
        private List<Integer> actualDisbursementDate;

    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepaymentSchedule {
        private CurrencyDTO currency;
        private Integer loanTermInDays;
        private Double totalPrincipalDisbursed;
        private Double totalPrincipalExpected;
        private Double totalPrincipalPaid;
        private Double totalInterestCharged;
        private Double totalFeeChargesCharged;
        private Double totalPenaltyChargesCharged;
        private Double totalWaived;
        private Double totalWrittenOff;
        private Double totalRepaymentExpected;
        private Double totalRepayment;
        private Double totalPaidInAdvance;
        private Double totalPaidLate;
        private Double totalOutstanding;
        private Double totalAdvancePayment;
        private List<Period> periods;
        private LoanScheduleHistoryData loanScheduleHistoryData;

    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OriginalSchedule {
        private CurrencyDTO currency;
        private Integer loanTermInDays;
        private Double totalPrincipalDisbursed;
        private Double totalPrincipalExpected;
        private Double totalPrincipalPaid;
        private Double totalInterestCharged;
        private Double totalFeeChargesCharged;
        private Double totalPenaltyChargesCharged;
        private Double totalWaived;
        private Double totalWrittenOff;
        private Double totalRepaymentExpected;
        private Double totalRepayment;
        private Double totalPaidInAdvance;
        private Double totalPaidLate;
        private Double totalOutstanding;
        private Double totalAdvancePayment;
        private List<Period> periods;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Period {
        private Integer period;
        private List<Integer> fromDate;
        private List<Integer> dueDate;
        private Boolean complete;
        private Integer daysInPeriod;
        private Double principalOriginalDue;
        private Double principalDue;
        private Double principalPaid;
        private Double principalWrittenOff;
        private Double principalOutstanding;
        private Double principalDisbursed;
        private Double principalLoanBalanceOutstanding;
        private Double interestOriginalDue;
        private Double interestDue;
        private Double interestPaid;
        private Double interestWaived;
        private Double interestWrittenOff;
        private Double interestOutstanding;
        private Double feeChargesDue;
        private Double feeChargesPaid;
        private Double feeChargesWaived;
        private Double feeChargesWrittenOff;
        private Double feeChargesOutstanding;
        private Double penaltyChargesDue;
        private Double penaltyChargesPaid;
        private Double penaltyChargesWaived;
        private Double penaltyChargesWrittenOff;
        private Double penaltyChargesOutstanding;
        private Double totalOriginalDueForPeriod;
        private Double totalDueForPeriod;
        private Double totalPaidForPeriod;
        private Double totalPaidInAdvanceForPeriod;
        private Double totalPaidLateForPeriod;
        private Double totalWaivedForPeriod;
        private Double totalWrittenOffForPeriod;
        private Double totalOutstandingForPeriod;
        private Double totalActualCostOfLoanForPeriod;
        private Double totalInstallmentAmountForPeriod;
        private Boolean recalculatedInterestComponent;
        private Double adjustedInterestAmountDue;
        private Double advancePaymentAmount;
        private Double interestAdjustedDueToGrace;
        private Double interestAccruable;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanScheduleHistoryData {
        private Integer historyVersion;
        private List<Integer> createdDate;

        // Constructors, getters, and setters
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BrokenPeriodMethodType {
        private Long id;
        private String code;
        private String value;

    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ScheduleHistoryData {
        private Integer historyVersion;
        private List<Integer> createdDate;

    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanAdditionalDetailsData {
        private Long id;
        private Long loanId;
        private Boolean isFldg;
        private AdditionalInterestComputationType additionalInterestComputationType;
        private Integer courseOnInterestPayment;
        private Integer additionalGraceOnInterestPayment;
        private Integer courseOnPrincipalPayment;
        private Integer additionalGraceOnPrincipalPayment;

    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdditionalInterestComputationType {
        private Long id;
        private String code;
        private String value;

    }
}
