package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class DrawdownCallbackResponseDto {
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
        @JsonProperty("AccountID")
        String accountId;
        @JsonProperty("UTRNo")
        String utrNo;
        @JsonProperty("LAN")
        String lan;
        @JsonProperty("Amount")
        Double amount;
        @JsonProperty("DisbursalDate")
        String disbursalDate;
    }

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class CallbackData {
        Integer status;
        Boolean isRetryable;
        String message;
        Data data;
    }
}
