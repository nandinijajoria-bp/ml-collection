package com.bharatpe.lending.lendingplatform.lms.enums;

import lombok.Getter;

@Getter
public enum FundInStatus {
    ACTIVE("A", "Active"),
    DEPOSIT("U", "Deposit"),
    CREDITED("Z", "Credited"),
    BOUNCE("B", "Bounce"),
    CANCELLED("C", "Cancelled");

    private final String code;
    private final String description;

    FundInStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    // Method to get Enum by description
    public static FundInStatus fromDescription(String description) {
        for (FundInStatus status : FundInStatus.values()) {
            if (status.getDescription().equalsIgnoreCase(description)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid fund-in status description: " + description);
    }
}