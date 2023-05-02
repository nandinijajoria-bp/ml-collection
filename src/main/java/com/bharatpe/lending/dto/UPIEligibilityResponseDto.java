package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class UPIEligibilityResponseDto {

    private Boolean upiEligibility;

}
