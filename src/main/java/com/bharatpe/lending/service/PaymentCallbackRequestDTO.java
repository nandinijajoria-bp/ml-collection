package com.bharatpe.lending.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaymentCallbackRequestDTO {

	private Long merchantId;
	private Integer amount;
	private String bankReferenceNo;

	public Long getMerchantId() {
		return merchantId;
	}

	public void setMerchantId(Long merchantId) {
		this.merchantId = merchantId;
	}

	public Integer getAmount() {
		return amount;
	}

	public void setAmount(Integer amount) {
		this.amount = amount;
	}

	public String getBankReferenceNo() {
		return bankReferenceNo;
	}

	public void setBankReferenceNo(String bankReferenceNo) {
		this.bankReferenceNo = bankReferenceNo;
	}

	@Override
	public String toString() {
		return "PaymentCallbackRequestDTO [merchantId=" + merchantId + ", amount=" + amount + ", bankReferenceNo="
				+ bankReferenceNo + "]";
	}
}
