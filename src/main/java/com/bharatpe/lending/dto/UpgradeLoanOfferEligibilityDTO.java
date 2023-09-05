package com.bharatpe.lending.dto;

import lombok.Data;

@Data
public class UpgradeLoanOfferEligibilityDTO {
    private Boolean BankStatementEligibility = Boolean.FALSE;
    private Boolean AccountAggregatorEligibility = Boolean.FALSE;
    private Boolean Gst3bEligibility = Boolean.FALSE;
}
