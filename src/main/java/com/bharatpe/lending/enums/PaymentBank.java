package com.bharatpe.lending.enums;

public enum PaymentBank {

    AIRTEL_PAYMENTS_BANK("AIRTEL PAYMENTS BANK"),
    PAYTM_PAYMENTS_BANK("PAYTM PAYMENTS BANK"),
    INDIA_POST_PAYMENTS_BANK("INDIA POST PAYMENTS BANK"),
    ;

    String val;

    PaymentBank(String val) {
        this.val = val;
    }

    public String getVal() {
        return val;
    }

}

