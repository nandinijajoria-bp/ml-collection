package com.bharatpe.lending.enums;

import lombok.Getter;

public enum RejectionReason {
    DEROG("DEROG"),
    LOW_TRANSACTION("LOW_TRANSACTION"),
    PERMANENT("PERMANENT"),
    CHANGE_BANK_ACCOUNT("CHANGE_BANK_ACCOUNT"),
    OGL("OGL");

    @Getter
    private final String reason;

    RejectionReason(String reason) {
        this.reason = reason;
    }
}
