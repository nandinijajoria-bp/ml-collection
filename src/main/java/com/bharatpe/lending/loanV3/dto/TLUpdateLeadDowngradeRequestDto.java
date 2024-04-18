package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLUpdateLeadDowngradeRequestDto {
    private String leadId;
    private String loanAmountRequested;
    private Long tenure;
    private String rateOfInterest;
}
