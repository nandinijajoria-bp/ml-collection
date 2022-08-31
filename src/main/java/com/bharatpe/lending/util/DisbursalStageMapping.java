package com.bharatpe.lending.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DisbursalStageMapping {

    static Map<Pair<String,String>, String> disbursalStages = new HashMap(){{
        put(Pair.of("LIQUILOANS_NBFC", "CREATED"),"PENDING");
        put(Pair.of("LIQUILOANS_NBFC", "PENDING"),"PENDING");
        put(Pair.of("LIQUILOANS_NBFC", "APPROVED"),"PENDING");
        put(Pair.of("LIQUILOANS_NBFC", "REJECTED"),"FAILED");
        put(Pair.of("LIQUILOANS_NBFC", "READY_TO_DISBURSED"),"PROCESSING");
        put(Pair.of("LIQUILOANS_NBFC", "DISBURSED"),"DISBURSED");
        put(Pair.of("LDC", "APPROVED"),"PENDING");
        put(Pair.of("LDC", "DISBURSEMENT_INITIATED"),"PROCESSING");
        put(Pair.of("LDC", "DISBURSEMENT_COMPLETED"),"DISBURSED");
        put(Pair.of("LDC", "DISBURSEMENT_FAILED"),"FAILED");
        put(Pair.of("LDC", "DISBURSEMENT_FAILED_AFTER_COMPLETION"),"FAILED");
    }};

    public static String getDisbursedStage(String lender, String stage) {
        return DisbursalStageMapping.disbursalStages.getOrDefault(Pair.of(lender,stage),"UNKNOWN");
    }
}
