package com.bharatpe.lending.lendingplatform.lms.enums;

import lombok.Getter;

@Getter
public enum DueType {
    P("Payable", "PAYABLE"),
    R("Receivable", "RECEIVABLE");

    private final String description;
    private final String code;

    DueType(String description, String code) {
        this.description = description;
        this.code = code;
    }
}