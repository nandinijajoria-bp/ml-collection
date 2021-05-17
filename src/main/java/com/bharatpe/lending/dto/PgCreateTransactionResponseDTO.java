package com.bharatpe.lending.dto;

public class PgCreateTransactionResponseDTO {
    private String statusCode;
    private String message;
    private Data data;

    public class Data{
        private Double paymentAmount;
        private String orderId;
        private String paymentURI;
        private String paymentURIDeeplink;

        public Double getPaymentAmount() {
            return paymentAmount;
        }

        public void setPaymentAmount(Double paymentAmount) {
            this.paymentAmount = paymentAmount;
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

        public String getPaymentURIDeeplink() {
            return paymentURIDeeplink;
        }

        public void setPaymentURIDeeplink(String paymentURIDeeplink) {
            this.paymentURIDeeplink = paymentURIDeeplink;
        }
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }
}
