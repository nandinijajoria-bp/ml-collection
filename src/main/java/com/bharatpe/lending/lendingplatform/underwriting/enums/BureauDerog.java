package com.bharatpe.lending.lendingplatform.underwriting.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum BureauDerog {
    HIGH(0), MED(1), LOW(2), NO(2),THIN(3);
    final int value;
}
