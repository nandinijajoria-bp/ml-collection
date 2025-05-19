package com.bharatpe.lending.lendingplatform.lms.enums;

import lombok.Getter;

@Getter
public enum TransactionTypes {
    PAYABLE("Payable", "P"),
    RECEIVABLE("Receivable", "R");

    private final String description;
    private final String code;

    TransactionTypes(String description, String code) {
        this.description = description;
        this.code = code;
    }

    public static TransactionTypes fromCode(String code) {
        for (TransactionTypes type : TransactionTypes.values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
}