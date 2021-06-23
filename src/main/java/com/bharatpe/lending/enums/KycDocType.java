package com.bharatpe.lending.enums;

public enum KycDocType {

    ENTITY("ENTITY"),
    SHOP_NAME("SHOP_NAME"),
    SHOP_ADDRESS("SHOP_ADDRESS"),
    ACCOUNT_TYPE("ACCOUNT_TYPE"),
    PAN_NO("PAN_NO"),
    PAN_CARD("PAN_CARD"),
    POA("POA"),
    AADHAAR("AADHAAR"),
    VOTERCARD("VOTERCARD"),
    PASSPORT("PASSPORT"),
    DRIVING_LICENCE("DRIVING_LICENCE"),
    SELFIE("SELFIE"),
    BUSINESSPROOF("BUSINESSPROOF"),
    BUSINESS_PROOF("BUSINESS_PROOF"),
    BUSINESS_PAN("BUSINESS_PAN"),
    BUSINESS_DEED("BUSINESS_DEED"),
    COI("COI"),
    AOI("AOI"),
    EKYC("EKYC"),
    GST("GST"),
    OTHER("OTHER"),
    INDIVIDUAL_PAN("INDIVIDUAL_PAN");

    String val;

    KycDocType(String val) {
        this.val = val;
    }

    public String getVal() {
        return val;
    }
}
