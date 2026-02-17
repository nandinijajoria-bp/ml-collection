package com.bharatpe.lending.enums;

import java.util.Optional;

public enum EmiApplicationStatus {

    DRAFT, PENDING_VERIFICATION, REJECTED, APPROVED, SEND_TO_NBFC, DELETED, CANCELLED, EXPIRED, CLOSED;

    public static Optional<EmiApplicationStatus> fromString(String status) {
        try {
            return Optional.of(EmiApplicationStatus.valueOf(status.toUpperCase()));
        } catch (Exception e) {
            return Optional.empty(); // return empty if not found
        }
    }
}
