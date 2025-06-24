package com.bharatpe.lending.lendingplatform.nbfc.enums;

public enum PennyDropCallbackStatus {
    PENNY_DROP_PENDING("PENDING"),
    PENNY_DROP_SUCCESS("SUCCESS"),
    PENNY_DROP_FAILED("FAILED"),
    PENNY_DROP_IN_PROGRESS("IN_PROGRESS");

    private final String value;

    PennyDropCallbackStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}