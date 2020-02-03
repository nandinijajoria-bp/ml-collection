package com.bharatpe.lending.dto;

import java.io.Serializable;

public class IneligibleRequestDTO implements Serializable {

    private String panCard;

    public String getPanCard() {
        return panCard;
    }

    public void setPanCard(String panCard) {
        this.panCard = panCard;
    }
}
