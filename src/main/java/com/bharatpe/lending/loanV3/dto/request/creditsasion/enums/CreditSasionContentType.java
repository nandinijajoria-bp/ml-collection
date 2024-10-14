package com.bharatpe.lending.loanV3.dto.request.creditsasion.enums;

public enum CreditSasionContentType {
    APPLICATION_JSON("application/json"),
    APPLICATION_XML("application/xml"),
    APPLICATION_PDF("application/pdf"),
    IMAGE_PNG("image/png"),
    VIDEO_MP4("video/mp4");

    private final String value;

    CreditSasionContentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}