package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Date;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class IneligibleAPIResponseDto {
	
	private Boolean success=true;
	private String message="";
	private Date registrationDate;
	private String paymentCount;
	private String paymentAmount;
	private Boolean newMerchant=false;
	private Boolean amountSucces;
	private Boolean countSucces;
	private Boolean enach=false;
	
	public IneligibleAPIResponseDto() {
		super();
	}
	public IneligibleAPIResponseDto(Boolean success, String message) {
		super();
		this.success = success;
		this.message = message;
	}
	public Boolean getSuccess() {
		return success;
	}
	public void setSuccess(Boolean success) {
		this.success = success;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public Date getRegistrationDate() {
		return registrationDate;
	}
	public void setRegistrationDate(Date registrationDate) {
		this.registrationDate = registrationDate;
	}
	public String getPaymentCount() {
		return paymentCount;
	}
	public void setPaymentCount(String paymentCount) {
		this.paymentCount = paymentCount;
	}
	public String getPaymentAmount() {
		return paymentAmount;
	}
	public void setPaymentAmount(String paymentAmount) {
		this.paymentAmount = paymentAmount;
	}
	public Boolean getNewMerchant() {
		return newMerchant;
	}
	public void setNewMerchant(Boolean newMerchant) {
		this.newMerchant = newMerchant;
	}
	public Boolean getAmountSucces() {
		return amountSucces;
	}
	public void setAmountSucces(Boolean amountSucces) {
		this.amountSucces = amountSucces;
	}
	public Boolean getCountSucces() {
		return countSucces;
	}
	public void setCountSucces(Boolean countSucces) {
		this.countSucces = countSucces;
	}
	public Boolean getEnach() {
		return enach;
	}
	public void setEnach(Boolean enach) {
		this.enach = enach;
	}
}
