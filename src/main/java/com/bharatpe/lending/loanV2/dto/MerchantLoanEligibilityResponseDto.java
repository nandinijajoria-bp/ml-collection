package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantLoanEligibilityResponseDto {

    private Double eligibleLimit;
    private Double loanAmount;
    private Boolean isActive;
    private String applicationStatus;

}
