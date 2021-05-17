package com.bharatpe.lending.dto;

public class PgStatusResponse {
    private String statusCode;
    private String message;
    private PgPaymentCallbackDTO data;

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

    public PgPaymentCallbackDTO getData() {
        return data;
    }

    public void setData(PgPaymentCallbackDTO data) {
        this.data = data;
    }
}
