package com.bharatpe.lending.dto;

import java.io.Serializable;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

public class IneligibleRequestDTO implements Serializable {

    private String panCard;

    private boolean skip;

    private Integer pincode;
    
    private String loanSource;

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

	public String getLoanSource() {
		return loanSource;
	}

	public void setLoanSource(String loanSource) {
		this.loanSource = loanSource;
	}

	@Override
	public String toString() {
		return "IneligibleRequestDTO [panCard=" + panCard + ", skip=" + skip + ", pincode=" + pincode + ", loanSource="
				+ loanSource + "]";
	}
}
