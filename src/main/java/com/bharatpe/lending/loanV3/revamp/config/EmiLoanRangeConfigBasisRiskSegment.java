package com.bharatpe.lending.loanV3.revamp.config;


import com.bharatpe.lending.common.enums.RiskSegment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmiLoanRangeConfigBasisRiskSegment {
    public static final Map<String, List<Integer>> LOAN_RANGES = new HashMap<>();

    static {
        LOAN_RANGES.put(RiskSegment.REGULAR_ETC.name(), Arrays.asList(1_000_000, 1_500_000));
        LOAN_RANGES.put(RiskSegment.REPEAT.name(), Arrays.asList(1_000_000, 1_500_000));
        LOAN_RANGES.put(RiskSegment.REGULAR_NTC.name(), Arrays.asList(300_000, 500_000));
    }
}
