package com.bharatpe.lending.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Builder
@ToString
@Data
public class ApplicationDataResponseDTO {
    private Long merchantId;
    private Long applicationId;
    private String loanEligibility;
    private String applicationViewState;
    private String applicationStatus;
    private String loanSegment;
    private Boolean callRequired;


}
