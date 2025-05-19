package com.bharatpe.lending.lendingplatform.nbfc.enums;

import lombok.Getter;

@Getter
public enum BRERiskDecision {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED");

    private final String value;

    BRERiskDecision(String value) {
        this.value = value;
    }

}