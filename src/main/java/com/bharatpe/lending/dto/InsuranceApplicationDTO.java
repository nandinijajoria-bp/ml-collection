package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsuranceApplicationDTO {
    private Double coveredAmount;
    private Double premiumAmount;
    private String insuranceType;
    private String partner;
    private String status;
    private String currView;
    private String policyId;
}
