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
        put(Pair.of("LIQUILOANS_NBFC", "READY TO DISBURSED"),"PROCESSING");
        put(Pair.of("LIQUILOANS_NBFC", "DISBURSED"),"DISBURSED");
        put(Pair.of("LDC", "APPROVED"),"PENDING");
        put(Pair.of("LDC", "DISBURSEMENT_INITIATED"),"PROCESSING");
        put(Pair.of("LDC", "DISBURSEMENT_COMPLETED"),"DISBURSED");
        put(Pair.of("LDC", "DISBURSEMENT_FAILED"),"FAILED");
        put(Pair.of("LDC", "DISBURSEMENT_FAILED_AFTER_COMPLETION"),"FAILED");
        put(Pair.of("LIQUILOANS_P2P", "CREATED"),"PENDING");
        put(Pair.of("LIQUILOANS_P2P", "PENDING"),"PENDING");
        put(Pair.of("LIQUILOANS_P2P", "APPROVED"),"PENDING");
        put(Pair.of("LIQUILOANS_P2P", "REJECTED"),"FAILED");
        put(Pair.of("LIQUILOANS_P2P", "READY TO DISBURSED"),"PROCESSING");
        put(Pair.of("LIQUILOANS_P2P", "DISBURSED"),"DISBURSED");
        put(Pair.of("LIQUILOANS_P2P_OF", "CREATED"),"PENDING");
        put(Pair.of("LIQUILOANS_P2P_OF", "PENDING"),"PENDING");
        put(Pair.of("LIQUILOANS_P2P_OF", "APPROVED"),"PENDING");
        put(Pair.of("LIQUILOANS_P2P_OF", "REJECTED"),"FAILED");
        put(Pair.of("LIQUILOANS_P2P_OF", "READY TO DISBURSED"),"PROCESSING");
        put(Pair.of("LIQUILOANS_P2P_OF", "DISBURSED"),"DISBURSED");
        put(Pair.of("MAMTA", "SUCCESS"),"DISBURSED");
        put(Pair.of("MAMTA0", "SUCCESS"),"DISBURSED");
        put(Pair.of("MAMTA1", "SUCCESS"),"DISBURSED");
        put(Pair.of("MAMTA2", "SUCCESS"),"DISBURSED");
        put(Pair.of("MAMTA", "CANCELLED"),"FAILED");
        put(Pair.of("MAMTA0", "CANCELLED"),"FAILED");
        put(Pair.of("MAMTA1", "CANCELLED"),"FAILED");
        put(Pair.of("MAMTA2", "CANCELLED"),"FAILED");
        put(Pair.of("ABFL", "DISBURSED"),"DISBURSED");
    }};

    public static String getDisbursedStage(String lender, String stage) {
        return DisbursalStageMapping.disbursalStages.getOrDefault(Pair.of(lender,stage),"UNKNOWN");
    }
}
