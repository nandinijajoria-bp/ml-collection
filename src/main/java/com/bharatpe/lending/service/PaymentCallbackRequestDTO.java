package com.bharatpe.lending.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaymentCallbackRequestDTO {

	private Double amount;
	private String paymentType;
	private String orderId;
	private String paymentVpa;
	private String transactionId;
    private String bankReferenceNumber;
    private String transactionDate;
    private String status;
    private String customerName;
    private String transactionMessage;

	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public String getPaymentType() {
		return paymentType;
	}

	public void setPaymentType(String paymentType) {
		this.paymentType = paymentType;
	}

	public String getPaymentVpa() {
		return paymentVpa;
	}

	public void setPaymentVpa(String paymentVpa) {
		this.paymentVpa = paymentVpa;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public String getBankReferenceNumber() {
		return bankReferenceNumber;
	}

	public void setBankReferenceNumber(String bankReferenceNumber) {
		this.bankReferenceNumber = bankReferenceNumber;
	}

	public String getTransactionDate() {
		return transactionDate;
	}

	public void setTransactionDate(String transactionDate) {
		this.transactionDate = transactionDate;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public String getTransactionMessage() {
		return transactionMessage;
	}

	public void setTransactionMessage(String transactionMessage) {
		this.transactionMessage = transactionMessage;
	}

	@Override
	public String toString() {
		return "PaymentCallbackRequestDTO [amount=" + amount + ", orderId=" + orderId + ", paymentVpa=" + paymentVpa
				+ ", transactionId=" + transactionId + ", bankReferenceNumber=" + bankReferenceNumber
				+ ", transactionDate=" + transactionDate + ", status=" + status + ", customerName=" + customerName
				+ ", transactionMessage=" + transactionMessage + ", paymentType=" + paymentType + "]";
	}
}