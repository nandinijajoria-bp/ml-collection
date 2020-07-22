package com.bharatpe.lending.dto;

public class UpiPaymentCancellationResponseDto {
	
	private String responseCode;
	private String responseMessage;
	private String status;
	private String mid;
	private String bharatpeTxnId;
	private String paymentStatus;
	private String createdTimestamp;
	private String amount;
	private String orderId;
	private String upiString;
	private String paymentLink;
	
	public String getResponseCode() {
		return responseCode;
	}
	public void setResponseCode(String responseCode) {
		this.responseCode = responseCode;
	}
	public String getResponseMessage() {
		return responseMessage;
	}
	public void setResponseMessage(String responseMessage) {
		this.responseMessage = responseMessage;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public String getMid() {
		return mid;
	}
	public void setMid(String mid) {
		this.mid = mid;
	}
	public String getBharatpeTxnId() {
		return bharatpeTxnId;
	}
	public void setBharatpeTxnId(String bharatpeTxnId) {
		this.bharatpeTxnId = bharatpeTxnId;
	}
	public String getPaymentStatus() {
		return paymentStatus;
	}
	public void setPaymentStatus(String paymentStatus) {
		this.paymentStatus = paymentStatus;
	}
	public String getCreatedTimestamp() {
		return createdTimestamp;
	}
	public void setCreatedTimestamp(String createdTimestamp) {
		this.createdTimestamp = createdTimestamp;
	}
	public String getAmount() {
		return amount;
	}
	public void setAmount(String amount) {
		this.amount = amount;
	}
	public String getOrderId() {
		return orderId;
	}
	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}
	public String getUpiString() {
		return upiString;
	}
	public void setUpiString(String upiString) {
		this.upiString = upiString;
	}
	public String getPaymentLink() {
		return paymentLink;
	}
	public void setPaymentLink(String paymentLink) {
		this.paymentLink = paymentLink;
	}
	@Override
	public String toString() {
		return "UpiPaymentCancellationResponseDto [responseCode=" + responseCode + ", responseMessage="
				+ responseMessage + ", status=" + status + ", mid=" + mid + ", bharatpeTxnId=" + bharatpeTxnId
				+ ", paymentStatus=" + paymentStatus + ", createdTimestamp=" + createdTimestamp + ", amount=" + amount
				+ ", orderId=" + orderId + ", upiString=" + upiString + ", paymentLink=" + paymentLink + "]";
	}
	
}
