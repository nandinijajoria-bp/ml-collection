package com.bharatpe.lending.lendingplatform.lms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDetailsResponse {
    @NotNull
    private String bpLoanId;
    private String transactionType;
    private Date timeStamp;
    private String message;
    private List<TransactionDetails> transactionDetails;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TransactionDetails {
        private String transactionStatus;
        @Positive
        private int transactionId;
        private String transactionNo;
        private String transactionMode;
        private String transactionDate;
        private BigDecimal transactionAmount;
        private String transactionSource;
        private String transactionReason;
        private String allocationType;
    }
}
