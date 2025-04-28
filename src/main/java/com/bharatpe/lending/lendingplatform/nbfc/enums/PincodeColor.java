package com.bharatpe.lending.lendingplatform.nbfc.enums;

public enum PincodeColor {
    RED("RED"),
    YELLOW("YELLOW"),
    GREEN("GREEN"),
    DARK_GREEN("DARK GREEN"),
    LIGHT_GREEN("LIGHT GREEN");

    String val;

    PincodeColor(String val) {
        this.val = val;
    }

    public String getValue() {
        return val;
    }

    public static String getValByType(String pincodeColor){
        try {
            return PincodeColor.valueOf(pincodeColor).getValue();
        } catch (Exception e){
            return null; // i.e. if not exits
        }
    }
}
