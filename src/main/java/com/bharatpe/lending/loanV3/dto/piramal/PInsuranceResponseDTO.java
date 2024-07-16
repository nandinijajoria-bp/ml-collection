package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PInsuranceResponseDTO {

    @JsonProperty("premiums")
    private List<PremiumDetails> premiums;

    @JsonProperty("errors")
    private List<String> errors;

    @Data
    public static class PremiumDetails {
        @JsonProperty("id")
        private String id;

        @JsonProperty("product")
        private String product;

        @JsonProperty("productType")
        private String productType;

        @JsonProperty("provider")
        private String provider;

        @JsonProperty("productLogoUrl")
        private String productLogoUrl;

        @JsonProperty("premiumAmount")
        private Double premiumAmount;

        @JsonProperty("policyTermInMonths")
        private Integer policyTermInMonths;

        @JsonProperty("sumInsured")
        private Double sumInsured;

        @JsonProperty("ranking")
        private Integer ranking;
    }

    @Data
    public static class Error {
        @JsonProperty("errorCode")
        private String errorCode;

        @JsonProperty("errorDescription")
        private String errorDescription;

        @JsonProperty("httpStatus")
        private HttpStatus httpStatus;
    }
}
