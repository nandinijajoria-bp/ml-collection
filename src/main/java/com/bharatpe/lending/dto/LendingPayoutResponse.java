package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ToString
@AllArgsConstructor
public class LendingPayoutResponse {

    private boolean success;
    private String statusCode;
    private String message;
    private Data data;

    @Getter
    @Setter
    @NoArgsConstructor
    @ToString
    @AllArgsConstructor
    public static class Data {
        private String transactionStatus;
        private String bankReferenceNo;
        private Long transactionId;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private Date transactionTimestamp;
    }
}
