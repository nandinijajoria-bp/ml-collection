package com.bharatpe.lending.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EmiLoanStatus {
    EXPIRED("expired"),
    REJECTED("rejected"),
    CLOSED("closed");
    private final String status;

}
