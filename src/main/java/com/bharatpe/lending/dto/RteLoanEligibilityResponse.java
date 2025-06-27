package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RteLoanEligibilityResponse {
    private Boolean rteEligibility;
    private Boolean loanEligibility;
}
