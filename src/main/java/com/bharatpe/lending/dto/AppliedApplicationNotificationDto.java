package com.bharatpe.lending.dto;

public class AppliedApplicationNotificationDto {
	
	private Long merchantId;
	private Long applicationId;
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
	@Override
	public String toString() {
		return "AppliedApplicationNotificationDto [merchantId=" + merchantId + ", applicationId=" + applicationId
				+ ", message=" + message + "]";
	}
	
}
