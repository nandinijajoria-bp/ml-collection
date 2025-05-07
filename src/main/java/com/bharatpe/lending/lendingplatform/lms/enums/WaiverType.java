package com.bharatpe.lending.lendingplatform.lms.enums;

import lombok.Getter;

@Getter
public enum WaiverType {
    WAIVER("W"),
    SETTLEMENT("S");

    private final String code;

    WaiverType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}