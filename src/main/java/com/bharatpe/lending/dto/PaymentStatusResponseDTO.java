package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaymentStatusResponseDTO {

    private boolean success = true;
    private String message;
    private String paymentStatus;
    private String orderId;
    private Double amount;
    private String referenceNumber;
    private Date transferTime;

    public PaymentStatusResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public PaymentStatusResponseDTO(String paymentStatus, String orderId, Double amount, String referenceNumber, Date transferTime) {
        this.paymentStatus = paymentStatus;
        this.orderId = orderId;
        this.amount = amount;
        this.referenceNumber = referenceNumber;
        this.transferTime = transferTime;
    }

    public PaymentStatusResponseDTO() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public Date getTransferTime() {
        return transferTime;
    }

    public void setTransferTime(Date transferTime) {
        this.transferTime = transferTime;
    }

    @Override
    public String toString() {
        return "PaymentStatusResponseDTO{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", paymentStatus='" + paymentStatus + '\'' +
                ", orderId='" + orderId + '\'' +
                ", amount=" + amount +
                ", referenceNumber='" + referenceNumber + '\'' +
                '}';
    }
}
