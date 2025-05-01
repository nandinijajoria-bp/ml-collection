package com.bharatpe.lending.lendingplatform.lms.enums;

import lombok.Getter;

@Getter
public enum TransactionType {
    O("OUTGOING"),
    R("RECEIVABLE");

    private final String code;

    TransactionType(String code) {
        this.code = code;
    }
}