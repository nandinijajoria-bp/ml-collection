package com.bharatpe.lending.dto;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class CheckRteEligibilityDTO {
    private boolean isRteEligible;
    private boolean isRteEnrolled;
}