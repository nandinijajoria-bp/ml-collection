package com.bharatpe.lending.lendingplatform.lms.enums;

import lombok.Getter;

@Getter
public enum LoanRetryStatus {
    QUEUE,
    FAILED,
    FAILED_NORETRY,
    CREATED,
    EXHAUSTED,
    UNKNOWN
}