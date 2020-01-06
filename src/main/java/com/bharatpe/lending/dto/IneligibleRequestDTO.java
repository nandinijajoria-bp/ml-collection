package com.bharatpe.lending.dto;

import java.io.Serializable;

public class IneligibleRequestDTO implements Serializable {

    private Integer requestedLoanAmount;
    private String panCard;

    public Integer getRequestedLoanAmount() {
        return requestedLoanAmount;
    }

    public void setRequestedLoanAmount(Integer requestedLoanAmount) {
        this.requestedLoanAmount = requestedLoanAmount;
    }

    public String getPanCard() {
        return panCard;
    }

    public void setPanCard(String panCard) {
        this.panCard = panCard;
    }
}
