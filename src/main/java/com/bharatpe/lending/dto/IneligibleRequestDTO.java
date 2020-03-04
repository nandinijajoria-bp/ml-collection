package com.bharatpe.lending.dto;

import java.io.Serializable;

public class IneligibleRequestDTO implements Serializable {

    private String panCard;

    private boolean skip;

    private Integer pincode;

    public String getPanCard() {
        return panCard;
    }

    public void setPanCard(String panCard) {
        this.panCard = panCard;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public Integer getPincode() {
        return pincode;
    }

    public void setPincode(Integer pincode) {
        this.pincode = pincode;
    }
}
