package com.bharatpe.lending.dto;

import lombok.Data;
import lombok.ToString;

import java.util.Date;
import java.util.List;

@ToString
@Data
public class PgPaymentCallbackDTO {
    private Double orderAmount;
    private Double paymentAmount;
    private String paymentRefId;
    private String beneficiaryName;
    private String paymentStatus;
    private String currency;
    private String orderId;
    private String paymentURI;
    private String redirectURI;
    private String checkoutType;
    private List<Payments> payments;

    @ToString
    public static class Payments{
        private Double amount;
        private String mode;
        private String status;
        private Date completedAt;
        private String refId;
        private String pgReferenceId;
        private Double finalAmount;
        private String breakupType;
        private String finalGateway;
        private String accountType;
        private String pgOrderId;
        private String terminalOrderId;

        public String getPgOrderId() {
            return pgOrderId;
        }

        public void setPgOrderId(String pgOrderId) {
            this.pgOrderId = pgOrderId;
        }

        public String getTerminalOrderId() {
            return terminalOrderId;
        }

        public void setTerminalOrderId(String terminalOrderId) {
            this.terminalOrderId = terminalOrderId;
        }

        public String getAccountType() {
            return accountType;
        }

        public void setAccountType(String accountType) {
            this.accountType = accountType;
        }

        public String getPgReferenceId() {
            return pgReferenceId;
        }

        public void setPgReferenceId(String pgReferenceId) {
            this.pgReferenceId = pgReferenceId;
        }

        public Double getFinalAmount() {
            return finalAmount;
        }

        public void setFinalAmount(Double finalAmount) {
            this.finalAmount = finalAmount;
        }

        public String getBreakupType() {
            return breakupType;
        }

        public void setBreakupType(String breakupType) {
            this.breakupType = breakupType;
        }

        public String getFinalGateway() {
            return finalGateway;
        }

        public void setFinalGateway(String finalGateway) {
            this.finalGateway = finalGateway;
        }

        public Double getAmount() {
            return amount;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Date getCompletedAt() {
            return completedAt;
        }

        public void setCompletedAt(Date completedAt) {
            this.completedAt = completedAt;
        }

        public String getRefId() {
            return refId;
        }

        public void setRefId(String refId) {
            this.refId = refId;
        }
    }

    public String getCheckoutType() {
        return checkoutType;
    }

    public void setCheckoutType(String checkoutType) {
        this.checkoutType = checkoutType;
    }

    public Double getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(Double orderAmount) {
        this.orderAmount = orderAmount;
    }

    public Double getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(Double paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public String getPaymentRefId() {
        return paymentRefId;
    }

    public void setPaymentRefId(String paymentRefId) {
        this.paymentRefId = paymentRefId;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPaymentURI() {
        return paymentURI;
    }

    public void setPaymentURI(String paymentURI) {
        this.paymentURI = paymentURI;
    }

    public String getRedirectURI() {
        return redirectURI;
    }

    public void setRedirectURI(String redirectURI) {
        this.redirectURI = redirectURI;
    }

    public List<Payments> getPayments() {
        return payments;
    }

    public void setPayments(List<Payments> payments) {
        this.payments = payments;
    }
}
