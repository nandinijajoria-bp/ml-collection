package com.bharatpe.lending.loanV2.dto;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

@Data
public class EligibilityIframeConsumptionDTO {
    @NotNull
    private String iframeBanner;

    @NotNull
    private String client;
}
