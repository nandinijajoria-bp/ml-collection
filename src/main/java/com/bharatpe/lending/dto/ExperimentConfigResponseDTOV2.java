package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ExperimentConfigResponseDTOV2 {
    @JsonProperty("variation_id")
    private String variationId;

    private boolean eligibility;

    @JsonProperty("delay_config_fetch")
    private boolean delayConfigFetch;
}