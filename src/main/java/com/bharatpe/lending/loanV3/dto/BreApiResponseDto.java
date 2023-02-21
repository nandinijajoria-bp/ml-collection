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
public class BreApiResponseDto {
    Long applicationId;
    String lender;
    String productName;
    Boolean success;
    Data data;


    @lombok.Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Data {

        String responseStatus;
        ErrorPayload error;

        @lombok.Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ErrorPayload {
             String code;
             String description;
             String errorType;
        }
    }
}
