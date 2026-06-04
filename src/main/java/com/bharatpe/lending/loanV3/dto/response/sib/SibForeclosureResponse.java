package com.bharatpe.lending.loanV3.dto.response.sib;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SibForeclosureResponse {
    private String status;
    private String message;
    private SibForeclosureData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SibForeclosureData {
        @JsonProperty("foreclosure_amount")
        private Double foreclosureAmount;

        @JsonProperty("account_status")
        private String accountStatus;

        @JsonProperty("closing_balance")
        private Double closingBalance;

        @JsonProperty("api_request_time")
        private String apiRequestTime;
    }
}
