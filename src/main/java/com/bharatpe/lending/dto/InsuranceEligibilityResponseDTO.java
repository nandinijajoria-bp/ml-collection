package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsuranceEligibilityResponseDTO {
    private Boolean success;
    private ResponseData data;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseData {
        private Boolean eligible;
        private Long customerId;
        private List<InsuranceEligibilityDTO> eligibleInsurances;
        private List<InsuranceApplicationDTO> activeApplications;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InsuranceEligibilityDTO {
        private Double coveredAmount;
        private Double premiumAmount;
        private Integer validityYears;
        private String insuranceType;
        private String partner;
    }
}
