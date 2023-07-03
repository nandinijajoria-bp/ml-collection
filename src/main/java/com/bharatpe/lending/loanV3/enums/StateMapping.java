package com.bharatpe.lending.loanV3.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum StateMapping {
    AN (Arrays.asList("ANDAMAN & NICOBAR", "ANDAMAN AND NICOBAR")),
    AP (Arrays.asList("ANDHRA PRADESH", "ANDHRA_PRADESH")),
    AR (Collections.singletonList("ARUNACHAL PRADESH")),
    AS (Collections.singletonList("ASSAM")),
    BR (Collections.singletonList("BIHAR")),
    CH (Collections.singletonList("CHANDIGARH")),
    CT (Collections.singletonList("CHHATTISGARH")),
    DD (Collections.singletonList("DAMAN & DIU")),
    DL (Collections.singletonList("DELHI")),
    GA (Collections.singletonList("GOA")),
    GJ (Collections.singletonList("GUJARAT")),
    HP (Collections.singletonList("HIMACHAL PRADESH")),
    HR (Collections.singletonList("HARYANA")),
    JH (Collections.singletonList("JHARKHAND")),
    JK (Arrays.asList("JAMMU & KASHMIR", "JAMMU AND KASHMIR")),
    KA (Collections.singletonList("KARNATAKA")),
    KL (Collections.singletonList("KERALA")),
    LD (Arrays.asList("LAKSHADEEP", "LAKSHADWEEP")),
    ME (Collections.singletonList("MEGHALAYA")),
    MH (Collections.singletonList("MAHARASHTRA")),
    MI (Collections.singletonList("MIZORAM")),
    MN (Collections.singletonList("MANIPUR")),
    MP (Arrays.asList("MADHYA PRADESH", "MADHYA_PRADESH")),
    NL (Collections.singletonList("NAGALAND")),
    OR (Arrays.asList("ODISHA", "ORISSA")),
    PB (Collections.singletonList("PUNJAB")),
    RJ (Collections.singletonList("RAJASTHAN")),
    SK (Collections.singletonList("SIKKIM")),
    TN (Collections.singletonList("TAMILNADU")),
    TR (Collections.singletonList("TRIPURA")),
    TS (Collections.singletonList("TELANGANA")),
    UP (Collections.singletonList("UTTAR PRADESH")),
    UK (Collections.singletonList("UTTARAKHAND")),
    WB (Collections.singletonList("WEST BENGAL")),
    PY (Arrays.asList("PUDUCHERRY", "PONDICHERRY")),
    LA (Collections.singletonList("LADAKH"));

    private List<String> stateMap;

    public List<String> getStateMap() {
        return stateMap;
    }

    StateMapping(List<String> stateMap) {
        this.stateMap = stateMap;
    }

    public static StateMapping getStateEnum (String name) {
        for (StateMapping stateName: StateMapping.values()) {
            if (stateName.getStateMap().contains(name.trim().toUpperCase())) {
                return stateName;
            }
        }
        return null;
    }
}









