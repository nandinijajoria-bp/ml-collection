package com.bharatpe.lending.lendingplatform.lms.enums;

import lombok.Getter;

@Getter
public enum StatusId {
    ACTIVE("Z"),
    CANCELLED("C"),
    BLOCKED("B");

    private final String code;

    StatusId(String code) {
        this.code = code;
    }
}