package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoanInsuranceDTO {
    private Double insurancePremium;
    private Double sumInsured;
    private String provider;
    private String product;
    private String productLogoUrl;
    private Integer policyTermsInMonths;
    private Boolean isSelected;
}