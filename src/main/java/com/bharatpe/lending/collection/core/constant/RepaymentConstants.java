package com.bharatpe.lending.collection.core.constant;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.bharatpe.lending.enums.Lender.OXYZO;
import static com.bharatpe.lending.enums.Lender.SMFG;


public class RepaymentConstants {
    public static final Set<String> NON_DECIMAL_SUPPORTED_LENDER = new HashSet<String>() {{
        add(SMFG.name());
    }};

    public static final Set<String> POST_ADJUSTMENT_PAYIN_RECEIPT_POSTING_LENDER = new HashSet<>(Arrays.asList(OXYZO.name()));

}
