package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CreditSpendVerifyResponseDTO {

    private boolean success = true;
    private String message;
    private Long transactionId;
    private Double amount;
    private Date transferTime;
    private String bankReferenceNo;
    private Double availableLimit;
    private String status;
    private String narrationHeading;
    private String narration1;
    private String narration2;
    private String narration3;
    private String deeplink;
    private String icon;

    public CreditSpendVerifyResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public CreditSpendVerifyResponseDTO(Long transactionId, Double amount, Date transferTime, String status) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.transferTime = transferTime;
        this.status = status;
    }

    public CreditSpendVerifyResponseDTO() {
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

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Date getTransferTime() {
        return transferTime;
    }

    public void setTransferTime(Date transferTime) {
        this.transferTime = transferTime;
    }

    public String getBankReferenceNo() {
        return bankReferenceNo;
    }

    public void setBankReferenceNo(String bankReferenceNo) {
        this.bankReferenceNo = bankReferenceNo;
    }

    public Double getAvailableLimit() {
        return availableLimit;
    }

    public void setAvailableLimit(Double availableLimit) {
        this.availableLimit = availableLimit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNarrationHeading() {
        return narrationHeading;
    }

    public void setNarrationHeading(String narrationHeading) {
        this.narrationHeading = narrationHeading;
    }

    public String getNarration1() {
        return narration1;
    }

    public void setNarration1(String narration1) {
        this.narration1 = narration1;
    }

    public String getNarration2() {
        return narration2;
    }

    public void setNarration2(String narration2) {
        this.narration2 = narration2;
    }

    public String getNarration3() {
        return narration3;
    }

    public void setNarration3(String narration3) {
        this.narration3 = narration3;
    }

    public String getDeeplink() {
        return deeplink;
    }

    public void setDeeplink(String deeplink) {
        this.deeplink = deeplink;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    @Override
    public String toString() {
        return "CreditSpendVerifyResponseDTO{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", transactionId=" + transactionId +
                ", amount=" + amount +
                ", transferTime=" + transferTime +
                ", bankReferenceNo='" + bankReferenceNo + '\'' +
                ", availableLimit=" + availableLimit +
                ", status=" + status +
                '}';
    }
}
