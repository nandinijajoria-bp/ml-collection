package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class ImageProofRequestDto {

    @NotNull(message = "Application ID is required")
    @Min(value = 1, message = "Application ID must be positive")

    @JsonProperty(value = "application_id")
    private String applicationId;

    @JsonProperty(value = "shop_doc_type")
    private String[] shopDocType;

    @JsonProperty(value = "skip_distance_check")
    private Boolean skipDistanceCheck;

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String[] getShopDocType() {
        return shopDocType;
    }

    public void setShopDocType(String[] shopDocType) {
        this.shopDocType = shopDocType;
    }

    public Boolean getSkipDistanceCheck() {
        return skipDistanceCheck;
    }

    public void setSkipDistanceCheck(Boolean skipDistanceCheck) {
        this.skipDistanceCheck = skipDistanceCheck;
    }

    @Override
    public String toString() {
        return "ImageProofRequestDto{" +
                "applicationId=" + applicationId +
                ", shopDocType='" + shopDocType + '\'' +
                ", skipDistanceCheck=" + skipDistanceCheck +
                '}';
    }
}