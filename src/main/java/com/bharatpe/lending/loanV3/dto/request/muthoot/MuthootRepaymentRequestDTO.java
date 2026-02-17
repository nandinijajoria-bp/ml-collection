package com.bharatpe.lending.loanV3.dto.request.muthoot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MuthootRepaymentRequestDTO {
    private String customerID;
    private String program;
    private String loanAccountNumber;
    private Double amount;
    private String repaymentID;
    private String purpose;
    private String remark;
    private String lmsPostingTime;
    private String realisationTime;
    private TransactionDetails transactionDetails;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionDetails {
        private String transactionTime;
        private String pgTransactionID;
        private String pgOrderID;
        private String utrNumber;
        private String mode;
        private String subMode;
    }
}
