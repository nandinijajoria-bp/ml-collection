package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class KycCallbackResponseDto {

    Boolean success;
    String applicationId;
    String productName;
    String lender;
    Data data;

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Data {
        String transactionId;
        String kycStatus;
        String kycType;
        String asyncId;
        String failureType;
        Boolean isRetryable;
        String message;
    }

//    @lombok.Data
//    @NoArgsConstructor
//    @AllArgsConstructor
//    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
//    public static class CallBackResponseData {
//        Integer status;
//        Long applicationId;
//        Data data;
//    }
}
