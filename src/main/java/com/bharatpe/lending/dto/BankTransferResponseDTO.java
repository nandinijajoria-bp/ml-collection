package com.bharatpe.lending.dto;

import java.io.Serializable;

public class BankTransferResponseDTO implements Serializable {
    private String paymentStatus;
    private String bankReferenceNumber;
    private String accountNumber;
    private String beneficiaryName;
    private String ifsc;
    private Long payoutId;
    private String orderId;

    public BankTransferResponseDTO(String paymentStatus, String bankReferenceNumber, String accountNumber, String beneficiaryName, String ifsc, Long payoutId, String orderId) {
        this.paymentStatus = paymentStatus;
        this.bankReferenceNumber = bankReferenceNumber;
        this.accountNumber = accountNumber;
        this.beneficiaryName = beneficiaryName;
        this.ifsc = ifsc;
        this.payoutId = payoutId;
        this.orderId = orderId;
    }

    public BankTransferResponseDTO() {
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getBankReferenceNumber() {
        return bankReferenceNumber;
    }

    public void setBankReferenceNumber(String bankReferenceNumber) {
        this.bankReferenceNumber = bankReferenceNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public String getIfsc() {
        return ifsc;
    }

    public void setIfsc(String ifsc) {
        this.ifsc = ifsc;
    }

    public Long getPayoutId() {
        return payoutId;
    }

    public void setPayoutId(Long payoutId) {
        this.payoutId = payoutId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public String toString() {
        return "BankTransferResponseDTO{" +
                "paymentStatus='" + paymentStatus + '\'' +
                ", bankReferenceNumber='" + bankReferenceNumber + '\'' +
                ", accountNumber='" + accountNumber + '\'' +
                ", beneficiaryName='" + beneficiaryName + '\'' +
                ", ifsc='" + ifsc + '\'' +
                ", payoutId=" + payoutId +
                ", orderId=" + orderId +
                '}';
    }
}
