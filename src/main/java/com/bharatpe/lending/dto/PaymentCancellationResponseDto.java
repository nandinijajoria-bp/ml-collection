package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaymentCancellationResponseDto {

	private Boolean status=true;
	private String message;
	private Boolean paymentCancelled=false;
	private String paymentStatus;
	
	public PaymentCancellationResponseDto(Boolean status, String message, Boolean paymentCancelled,
			String paymentStatus) {
		super();
		this.status = status;
		this.message = message;
		this.paymentCancelled = paymentCancelled;
		this.paymentStatus = paymentStatus;
	}
	public Boolean getStatus() {
		return status;
	}
	public void setStatus(Boolean status) {
		this.status = status;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public Boolean getPaymentCancelled() {
		return paymentCancelled;
	}
	public void setPaymentCancelled(Boolean paymentCancelled) {
		this.paymentCancelled = paymentCancelled;
	}
	public String getPaymentStatus() {
		return paymentStatus;
	}
	public void setPaymentStatus(String paymentStatus) {
		this.paymentStatus = paymentStatus;
	}
	
}
