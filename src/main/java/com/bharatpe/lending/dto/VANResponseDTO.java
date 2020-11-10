package com.bharatpe.lending.dto;

public class VANResponseDTO {

    private String responseCode;
    private String responseMessage;
    private String status;
    private String accountNumber;
    private String ifsc;

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

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getIfsc() {
        return ifsc;
    }

    public void setIfsc(String ifsc) {
        this.ifsc = ifsc;
    }

    @Override
    public String toString() {
        return "VANResponseDTO{" +
                "responseCode='" + responseCode + '\'' +
                ", responseMessage='" + responseMessage + '\'' +
                ", status='" + status + '\'' +
                ", accountNumber='" + accountNumber + '\'' +
                ", ifsc='" + ifsc + '\'' +
                '}';
    }
}
