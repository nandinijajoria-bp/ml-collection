package com.bharatpe.lending.dto;

public class InstantNotificationDto {
	
	private Long merchantId;
	private Long applicationId;
	private String messageCategory;
	private String message;
	
	public Long getMerchantId() {
		return merchantId;
	}
	public void setMerchantId(Long merchantId) {
		this.merchantId = merchantId;
	}
	public Long getApplicationId() {
		return applicationId;
	}
	public void setApplicationId(Long applicationId) {
		this.applicationId = applicationId;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public String getMessageCategory() {
		return messageCategory;
	}
	public void setMessageCategory(String messageCategory) {
		this.messageCategory = messageCategory;
	}
	@Override
	public String toString() {
		return "AppliedApplicationNotificationDto [merchantId=" + merchantId + ", applicationId=" + applicationId
				+ ", messageCategory=" + messageCategory + ", message=" + message + "]";
	}
	
}
