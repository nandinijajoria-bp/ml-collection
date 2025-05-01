package com.bharatpe.lending.lendingplatform.lms.enums;

import lombok.Getter;

@Getter
public enum DebitType {
    FA("Fixed Amount"),
    LA("Loan Amount");

    private final String description;

    DebitType(String description) {
        this.description = description;
    }
}