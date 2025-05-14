package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayUChargesRequestDto {
    private String applicationId;
    private String productName;
    private String lender;
    private Payload payload;
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Payload {
        @JsonProperty("application-id")
        private String applicationId;
        private Double amount;
        private String loanId;
        private String requestId;
        private String chargeDate;
        private String chargeType;
    }
}
