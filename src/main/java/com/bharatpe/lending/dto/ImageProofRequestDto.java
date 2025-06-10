package com.bharatpe.lending.dto;


import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class ImageProofRequestDto {

    @NotNull(message = "Application ID is required")
    @Min(value = 1, message = "Application ID must be positive")
    private Long applicationId;

    private String[] shopDocType;

    private Boolean skipDistanceCheck;

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
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