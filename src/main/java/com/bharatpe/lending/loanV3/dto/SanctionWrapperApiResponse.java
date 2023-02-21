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
public class SanctionWrapperApiResponse {
    Long applicationId;
    String lender;
    Boolean success;
    String productName;
    Data data;


    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class SanctionResponseData {
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
    public static class ErrorPayload {
        private String code;
        private String description;
        private String errorType;

    }

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Data {
        SanctionResponseData data;
        ErrorPayload errorPayload;
        String responseStatus;
    }
}
