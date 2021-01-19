package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreditScoreRequestDto {

    @JsonProperty("pan_number")
    private String panNumber;

    @JsonProperty("pin_code")
    private Integer pinCode;

    private boolean skip;

    public String getPanNumber() {
        return panNumber;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber;
    }

    public Integer getPinCode() {
        return pinCode;
    }

    public void setPinCode(Integer pinCode) {
        this.pinCode = pinCode;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }
}
