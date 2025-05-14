package com.bharatpe.lending.lendingplatform.nbfc.enums;

public enum StatusCode {
    SUCCESS("SUCCESS","200");


    private String status;
    private String value;

    StatusCode(String status,String value) {
        this.value = value;
        this.status = status;
    }

    public String getValue() {
        return value;
    }

    public String getStatus() {
        return status;
    }
}
