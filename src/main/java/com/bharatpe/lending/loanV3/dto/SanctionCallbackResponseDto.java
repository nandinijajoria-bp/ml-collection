package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class SanctionCallbackResponseDto {
    Boolean success;
    String applicationId;
    String productName;
    String lender;
    CallbackData data;


    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Data {
        String accountId;
        String accountState;
        String kycMode;
        Double approvedOfferLimit;
        Date accountCreationDate;
        String dealNo;
        String dealId;
    }

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
//    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class CallbackData {
        Data data;
        ErrorPayload errorPayload;
        String responseStatus;
    }

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorPayload {
        private String code;
        private String description;
        private String errorType;

    }
}

