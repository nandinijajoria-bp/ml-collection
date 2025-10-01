package com.bharatpe.lending.lendingplatform.lms.util;

import java.math.BigDecimal;

public class ConversionUtil {

    private ConversionUtil() {
        // prevent instantiation
    }

    public static double safeBigDecimalToDouble(BigDecimal value) {
        if (value == null) {
            return 0.0;
        }
        if (value.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) > 0) {
            return Double.MAX_VALUE;
        }
        if (value.compareTo(BigDecimal.valueOf(-Double.MAX_VALUE)) < 0) {
            return -Double.MAX_VALUE;
        }
        return value.doubleValue();
    }

    public static int safeBigDecimalToInt(BigDecimal value) {
        if (value == null)
            return 0;

        if (value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0)
            return Integer.MAX_VALUE;

        if (value.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0)
            return Integer.MIN_VALUE;

        return value.intValue();
    }
}
