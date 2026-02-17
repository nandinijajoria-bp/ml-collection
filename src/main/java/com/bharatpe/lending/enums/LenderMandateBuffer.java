package com.bharatpe.lending.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LenderMandateBuffer {
    ABFL(12 * 30 * 24 * 60 * 60 * 1000L),
    DEFAULT(5 * 365 * 24 * 60 * 60 * 1000L);

    private final long buffer;
}
