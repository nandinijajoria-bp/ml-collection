package com.bharatpe.lending.dto;

public class ENachIntitiationResponseDTO {

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

    private String merchantIdentifier;

    private Long transactionIdentifier;

    private Long transactionReferenceNumber;

    private String schemeCode;

    private String bankCode;

    private Double loanAmount;

    private String loanStartDate;

    private String responseStatus;

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    private Long applicationId;



    public ENachIntitiationResponseDTO(){
        init();
    }

    private void init(){
          this.merchantIdentifier = "T517110";
          this.schemeCode = "First";
    }
}
