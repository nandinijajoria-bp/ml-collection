package com.bharatpe.lending.loanV3.dto.piramal;

import com.bharatpe.lending.loanV3.enums.piramal.FeeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PiramalGetLoanResponseDto {
    private String loanAccountNumber;

    private String leadId;

    private Long loanAmount;

    private Long totalOutstandingAmount;

    private Long totalOutstandingPrincipal;

    private Long totalOutstandingInterest;

    private Long totalRepayAmount;

    private Long totalInterestPayable;

    private Date loanStartDate;

    private Date maturityDate;

    private Long firstDisbursementDate;

    private int loanTenor;

    private String repaymentFrequency;

    private Long rateOfInterest;

    private List<Fee> feeList;

    private Date firstEmiDate;

    private Long firstEmiAmount;

    private Long nextRepaymentDate;

    private Long nextRepaymentAmount;

    private Boolean active;

    private Long totalPaidAmount;

    private Long totalPaidPrincipal;

    private Long totalPaidInterest;

    private List<LoanSchedule> repaymentSchedule;

    private List<DisbursementData> disbursementData;

    private List<OverDueCharge> overDueCharges;

    private OverDueSummary overDueSummary;

    private Long accruedInterest;

    private Long advancePaymentAmount;

    @Data
    private static class LoanSchedule{

        private Long scheduledDate;

        private Long scheduledPrincipal;

        private Long scheduledInterest;

        private Long scheduledTotal;

        private Long endBalance;

    }

    @Data
    private static class DisbursementData{

        private String paymentMode;

        private String disbursementParty;

        private Long disbursementDate;

        private Long disbursementAmount;

        private String disbursementStatus;

        private String accountNumber;

        private String accountHolderName;

        private String ifscCode;

        private String remarks;

        private String bankAccountVerified;

        private Long emiAmount;

        private Long advanceEmiAmount;

        private Long numberOfAdvanceEmi;

        private Long preEmi;

        private String paymentProvider;

        private String disbursementName;

        private String disbursementPriority;

        private String phoneNumber;

        private String balanceTransferLoanAccountNumber;


        private String disbursementEnabled;

        private Long disbursementBankId;

        private Long disbursementId;

        private Long utr;

    }

    @Data
    private static class OverDueCharge{

        private Long overDueDate;

        private Long overDueAmount;

        private Long overDueCharge;

        private Long overDueChargePaid;

        private Long overDueInterest;

        private Long overDueInterestPaid;

        private String chargeType;

    }

    @Data
    private static class OverDueSummary{

        private Long overDueAmount;

        private Long overDuePrincipal;

        private Long overDueInterest;

        private Long overDueEMICount;

        private Long overDueChargesTotal;

        private Long overDuePenaltyInterest;

        private Long overDueAmountTotal;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fee {
        private Double feeAmount;

        private Double feeAmountIncludingGst;

        private Double feePercentage;

        private Boolean inclGST;

        private FeeType feeType;

        private String pennantCode;

        private Double paidAmount;

        private Double waiverAmount;

        private Double feeBalance;
    }
}
