package com.bharatpe.lending.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class LendingPayoutResponse {

    private boolean success;
    private String statusCode;
    private String message;
    private Data data;

    @Getter
    @Setter
    @NoArgsConstructor
    @ToString
    public static class Data {
        private String transactionStatus;
        private String bankReferenceNo;
        private Long transactionId;
        private Date transactionTimestamp;
    }
}
