package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PreBookResponseDTO {

    private String bankName;
    private String accountNumber;
    private Double loanAmount;
    private Double edi;
    private String deepLink;
    private boolean success = true;
    private String message;

    public PreBookResponseDTO(String bankName, String accountNumber, Double loanAmount, Double edi, String deepLink) {
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.loanAmount = loanAmount;
        this.edi = edi;
        this.deepLink = deepLink;
    }

    public PreBookResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public PreBookResponseDTO() {
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public Double getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(Double loanAmount) {
        this.loanAmount = loanAmount;
    }

    public Double getEdi() {
        return edi;
    }

    public void setEdi(Double edi) {
        this.edi = edi;
    }

    public String getDeepLink() {
        return deepLink;
    }

    public void setDeepLink(String deepLink) {
        this.deepLink = deepLink;
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

    @Override
    public String toString() {
        return "PreBookResponseDTO{" +
                "bankName='" + bankName + '\'' +
                ", accountNumber='" + accountNumber + '\'' +
                ", loanAmount=" + loanAmount +
                ", edi=" + edi +
                ", deepLink='" + deepLink + '\'' +
                ", success=" + success +
                ", message='" + message + '\'' +
                '}';
    }
}
