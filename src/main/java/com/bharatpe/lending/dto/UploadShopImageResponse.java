package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadShopImageResponse {
    private UploadShopImageResponseData data;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UploadShopImageResponseData {
        private boolean success;
        private String error;
    }
}
