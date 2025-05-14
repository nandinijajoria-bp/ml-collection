package com.bharatpe.lending.lendingplatform.lms.enums;

import lombok.Getter;

@Getter
public enum DebitFrequency {
    M("Monthly"),
    Q("Quarterly"),
    D("Daily");

    private final String description;

    DebitFrequency(String description) {
        this.description = description;
    }
}