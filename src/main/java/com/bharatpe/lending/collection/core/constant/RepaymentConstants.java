package com.bharatpe.lending.collection.core.constant;

import java.util.HashSet;
import java.util.Set;

import static com.bharatpe.lending.enums.Lender.SMFG;


public class RepaymentConstants {
    public static final Set<String> NON_DECIMAL_SUPPORTED_LENDER = new HashSet<String>() {{
        add(SMFG.name());
    }};
}
