package com.bharatpe.lending.dto;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class PgMandateExecutionResponse {

    private String statusCode;
    private String message;
    private PgMandateExecutionData data;

    @Data
    @ToString
    public static class PgMandateExecutionData {
        private String orderId;
        private String status;
        private String reason;
        private String message;
    }
}
