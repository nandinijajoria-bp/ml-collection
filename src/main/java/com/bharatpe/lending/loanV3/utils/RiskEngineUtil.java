package com.bharatpe.lending.loanV3.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RiskEngineUtil {

    public static String loanRiskMapping(String riskSegment) {
        switch (riskSegment) {
            case "REPEAT":
                return "REPEAT";
            case "TOPUP":
                return "TOPUP";
            case "NTB_PURE":
            case "NTB_ETB_2":
            case "NTB_ETB_1":
                return "LowTransactor_ETC_Fresh";
            case "REGULAR_NTC":
                return "HighTransactor_NTC";
            case "REGULAR_ETC":
                return "HighTransactor_ETC_Fresh";
        }
        return null;
    }
}
