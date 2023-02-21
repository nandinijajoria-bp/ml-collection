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
public class BreCallbackResponseDto {
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
        String asyncId;
        String abflApplicationId;
        Double loanAmount;
        String roi;
        String tenure;
        String cccId;
        String riskFlag;
        String pepFlag;
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
