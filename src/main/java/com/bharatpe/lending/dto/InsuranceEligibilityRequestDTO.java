package com.bharatpe.lending.dto;

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
public class InsuranceEligibilityRequestDTO {
    private Long customerId;
    private Double amount;
    private Integer tenure;
    private Long pinCode;
    private String businessCategory;
    private String businessSubCategory;
}
