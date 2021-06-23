package com.bharatpe.lending.dto;

import com.bharatpe.lending.constant.CreditConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class InitiatePaymentRequestDTO {

	private Integer amount;
	private String paymentType;
	private String vpa;
	private CreditConstants.PaymentMode type;
	private CreditConstants.PaymentSource source;
	private Integer advanceEdiCount;

	public Integer getAmount() {
		return amount;
	}

	public void setAmount(Integer amount) {
		this.amount = amount;
	}

	public String getPaymentType() {
		return paymentType;
	}

	public void setPaymentType(String paymentType) {
		this.paymentType = paymentType;
	}

	public String getVpa() {
		return vpa;
	}

	public void setVpa(String vpa) {
		this.vpa = vpa;
	}

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

	public Integer getAdvanceEdiCount() {
		return advanceEdiCount;
	}

	public void setAdvanceEdiCount(Integer advanceEdiCount) {
		this.advanceEdiCount = advanceEdiCount;
	}

	@Override
	public String toString() {
		return "InitiatePaymentRequestDTO [amount=" + amount + ", paymentType=" + paymentType + "]";
	}
}