package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ENachIntitiationResponseDTO {

    private String merchantIdentifier = "T517110";

    private Long transactionIdentifier;

    private Long transactionReferenceNumber;

    private String schemeCode = "First";

    private String bankCode;

    private Double loanAmount;

    private String loanStartDate;

    private Long applicationId;

    public String getMerchantIdentifier() {
        return merchantIdentifier;
    }

    public void setMerchantIdentifier(String merchantIdentifier) {
        this.merchantIdentifier = merchantIdentifier;
    }

    public Long getTransactionIdentifier() {
        return transactionIdentifier;
    }

    public void setTransactionIdentifier(Long transactionIdentifier) {
        this.transactionIdentifier = transactionIdentifier;
    }

    public Long getTransactionReferenceNumber() {
        return transactionReferenceNumber;
    }

    public void setTransactionReferenceNumber(Long transactionReferenceNumber) {
        this.transactionReferenceNumber = transactionReferenceNumber;
    }

    public String getSchemeCode() {
        return schemeCode;
    }

    public void setSchemeCode(String schemeCode) {
        this.schemeCode = schemeCode;
    }

    public String getBankCode() {
        return bankCode;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }

    public Double getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(Double loanAmount) {
        this.loanAmount = loanAmount;
    }

    public String getLoanStartDate() {
        return loanStartDate;
    }

    public void setLoanStartDate(String loanStartDate) {
        this.loanStartDate = loanStartDate;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    @Override
    public String toString() {
        return "ENachIntitiationResponseDTO{" +
                "merchantIdentifier='" + merchantIdentifier + '\'' +
                ", transactionIdentifier=" + transactionIdentifier +
                ", transactionReferenceNumber=" + transactionReferenceNumber +
                ", schemeCode='" + schemeCode + '\'' +
                ", bankCode='" + bankCode + '\'' +
                ", loanAmount=" + loanAmount +
                ", loanStartDate='" + loanStartDate + '\'' +
                ", applicationId=" + applicationId +
                '}';
    }
}
