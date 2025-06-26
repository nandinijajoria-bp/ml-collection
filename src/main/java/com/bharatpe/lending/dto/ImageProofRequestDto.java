package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class ImageProofRequestDto {

    @NotNull(message = "Application ID is required")
    @Min(value = 1, message = "Application ID must be positive")
    @JsonProperty(value = "application_id")
    private String applicationId;

    @JsonProperty(value = "shop_doc_type")
    private String[] shopDocType;

    @JsonProperty(value = "skip_distance_check")
    private Boolean skipDistanceCheck;
}