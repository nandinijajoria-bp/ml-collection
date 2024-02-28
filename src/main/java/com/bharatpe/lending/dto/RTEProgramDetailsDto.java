package com.bharatpe.lending.dto;

import com.bharatpe.lending.enums.KycStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class RTEProgramDetailsDto {
    private KycStatus kycStatus;
    private MileStoneEligibilityResponseDto routeToEligibilityData;
    private Boolean loanEligibility;
    private double loanAmount;
    private String ineligible;
}
