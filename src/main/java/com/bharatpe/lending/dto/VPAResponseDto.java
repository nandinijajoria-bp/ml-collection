package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VPAResponseDto {

    String paymentVpa;
    Double amount;
    String transactionId;
    String bankReferenceNumber;
    String transactionDate;
    String status;
    String customerName;
    String transactionMessage;
    String orderId;

    public String getPaymentVpa() {
        return paymentVpa;
    }

    public void setPaymentVpa(String paymentVpa) {
        this.paymentVpa = paymentVpa;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
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

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public String toString() {
        return "VPAResponseDto{" +
                "paymentVpa='" + paymentVpa + '\'' +
                ", amount=" + amount +
                ", transactionId='" + transactionId + '\'' +
                ", bankReferenceNumber='" + bankReferenceNumber + '\'' +
                ", transactionDate='" + transactionDate + '\'' +
                ", status='" + status + '\'' +
                ", customerName='" + customerName + '\'' +
                ", transactionMessage='" + transactionMessage + '\'' +
                ", orderId='" + orderId + '\'' +
                '}';
    }
}
