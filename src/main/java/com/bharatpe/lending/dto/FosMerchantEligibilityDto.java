package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class FosMerchantEligibilityDto {
    String eligibility;
    Long merchantId;
    Integer priority;
    String offerType;
    String loanType;
    String reason;
    int nachTask;
}
