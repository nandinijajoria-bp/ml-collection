package com.bharatpe.lending.dto;

import java.io.Serializable;

public class BankTransferResponseDTO implements Serializable {
    private String responseCode;
    private String message;
    private String status;
    private PayoutResponse data;

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public PayoutResponse getData() {
        return data;
    }

    public void setData(PayoutResponse data) {
        this.data = data;
    }

    public static class PayoutResponse {
        private String paymentStatus;
        private String bankReferenceNumber;
        private String accountNumber;
        private String beneficiaryName;
        private String ifsc;
        private Long payoutId;
        private Long orderId;

        public PayoutResponse(String paymentStatus, String bankReferenceNumber, String accountNumber, String beneficiaryName, String ifsc, Long payoutId, Long orderId) {
            this.paymentStatus = paymentStatus;
            this.bankReferenceNumber = bankReferenceNumber;
            this.accountNumber = accountNumber;
            this.beneficiaryName = beneficiaryName;
            this.ifsc = ifsc;
            this.payoutId = payoutId;
            this.orderId = orderId;
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

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }
    }
}
