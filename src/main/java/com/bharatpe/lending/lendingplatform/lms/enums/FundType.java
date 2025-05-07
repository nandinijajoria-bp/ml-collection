package com.bharatpe.lending.lendingplatform.lms.enums;

import lombok.Getter;

@Getter
public enum FundType {
    RECURRING("R"),
    ONE_TIME("P");

    private final String code;

    FundType(String code) {
        this.code = code;
    }
}