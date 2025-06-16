package com.bharatpe.lending.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PaymentBank {

    AIRTEL_PAYMENTS_BANK("AIRTEL PAYMENTS BANK"),
    PAYTM_PAYMENTS_BANK("PAYTM PAYMENTS BANK"),
    INDIA_POST_PAYMENTS_BANK("INDIA POST PAYMENTS BANK"),
    IDBI_BANK("IDBI BANK"),
    AXIS_BANK("AXIS BANK");

    private String val;
}

