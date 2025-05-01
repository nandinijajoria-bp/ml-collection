package com.bharatpe.lending.lendingplatform.lms.enums;

import lombok.Getter;

@Getter
public enum MandateType {
    NACH("NACH"),
    ENACH("ENACH"),
    AUTOPAYUPI("AUTOPAYUPI");

    private final String code;

    MandateType(String code) {
        this.code = code;
    }
}