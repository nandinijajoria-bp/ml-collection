package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoanInsuranceDTO {
    private boolean isSelected;
    private List<InsuranceDetails> insurances;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InsuranceDetails {
        private Double insurancePremium;
        private Double sumInsured;
        private String provider;
        private String product;
        private String productLogoUrl;
        private Integer policyTermsInMonths;
    }
}