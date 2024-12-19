package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PiramalGetForeclosureResponseDTO {

    private Boolean active;
    private ForeClosureReport foreClosureReport;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ForeClosureReport {
        private String loanAccountNumber;
        private Double loanAmount;
        private String disbursalDate;
        private String chrgTillDate;
        private Double totalOutstandingPrincipal;
        private Double latePayCharges;
        private Double foreClosureFees;
        private Double principalAmount;
        private Double interestAmount;
        private Integer pendingInstallments;
        private Double refund;
        private Double lppRefund;
        private Double totalWaiver;
        private Double foreclosureAmount;
        private Integer advInsts;
        private Double otherRefunds;
        private Double totalRefunds;
        private Double netReceivableNoRefund;
        private Double manualAdviceAmount;
        private Double latePayInterestAmt;
        private String total;
        private Double gstOnForeClosFees;
        private Double foreClosFeesExGST;
        private Double chargesIncGST;
        private Double totalPrincipalDue;
        private Double totalPrincipalPaid;
        private Double totalPrincipalWaived;
        private Double totalInterestDue;
        private Double totalInterestPaid;
        private Double totalInterestWaived;
        private Double totBounceDue;
        private Double totBouncePaid;
        private Double totBounceWaived;
        private Double totPenaltyDue;
        private Double totPenaltyPaid;
        private Double totPenaltyWaived;
        private Double totRcvDue;
        private Double totRcvPaid;
        private Double totRcvWaived;
        private Double totPayableDue;
        private Double totPayablePaid;
        private Double currentDPD;
        private Double totExcessBal;
        private Double lppExcessBal;
        private Double cheqBncCharges;
        private String loanStatus;
        private String calDate;
        private Double totalDues;
        private Double instForTheMonth;
        private Double pendingAmountToPostToLMS;
    }
}
