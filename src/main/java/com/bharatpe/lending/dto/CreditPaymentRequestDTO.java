package com.bharatpe.lending.dto;

import com.bharatpe.lending.constant.CreditConstants;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CreditPaymentRequestDTO {
    private CreditConstants.PaymentMode type;
    private CreditConstants.PaymentSource source;
    private Double amount;
    private String otp;
    private String vpa;

    public CreditConstants.PaymentMode getType() {
        return type;
    }

    public void setType(CreditConstants.PaymentMode type) {
        this.type = type;
    }

    public CreditConstants.PaymentSource getSource() {
        return source;
    }

    public void setSource(CreditConstants.PaymentSource source) {
        this.source = source;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

	public String getVpa() {
		return vpa;
	}

	public void setVpa(String vpa) {
		this.vpa = vpa;
	}
    
}
