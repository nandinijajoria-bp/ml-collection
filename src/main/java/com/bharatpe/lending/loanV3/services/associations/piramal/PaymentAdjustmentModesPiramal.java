package com.bharatpe.lending.loanV3.services.associations.piramal;

public enum PaymentAdjustmentModesPiramal {

    SETTLEMENT("IMPS"),FP("IMPS"), UPI("IMPS"), DC("IMPS"),
    NB("IMPS"), BP_BT("DIRECT_DEBIT"), EXCEPTION("NEFT"),
    SCHEME1("NEFT"), BHARATPE_NACH("RTGS"), TOPUP("NEFT");

    String adjustedModeEquivalent;

    PaymentAdjustmentModesPiramal(String adjustedModeEquivalent) {
        this.adjustedModeEquivalent = adjustedModeEquivalent;
    }

    public String getAdjustedModeEquivalent() {
        return adjustedModeEquivalent;
    }
}