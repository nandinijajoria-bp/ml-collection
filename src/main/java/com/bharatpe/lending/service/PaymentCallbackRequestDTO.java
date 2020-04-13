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
	private Double amount;
	private String bankReferenceNo;
	private String orderId;

	public Long getMerchantId() {
		return merchantId;
	}

	public void setMerchantId(Long merchantId) {
		this.merchantId = merchantId;
	}

	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

	public String getBankReferenceNo() {
		return bankReferenceNo;
	}

	public void setBankReferenceNo(String bankReferenceNo) {
		this.bankReferenceNo = bankReferenceNo;
	}
	
	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	@Override
	public String toString() {
		return "PaymentCallbackRequestDTO [merchantId=" + merchantId + ", amount=" + amount + ", bankReferenceNo="
				+ bankReferenceNo + ", orderId=" + orderId + "]";
	}

}
