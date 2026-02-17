package com.bharatpe.lending.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Request DTO for underwriting post-processing logic
 */
@Data
public class UnderwritingPostProcessingRequest {

    @NotNull
    private Long merchantId;

    @NotNull
    private GlobalLimitResponse globalLimitResponse;

}