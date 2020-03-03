package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ENachIntitiationResponseDTO {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Data data;

    private boolean response = true;

    private String message = "success";

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Data {

        private String deep_link;

        private String merchantIdentifier = "L517110";

        private Long transactionIdentifier;

        private Long transactionReferenceNumber;

        private String schemeCode = "First";

        private String bankCode;

        private Double loanAmount;

        private String loanStartDate;

        private Long applicationId;

        private String accountNumber;

        private String beneficiaryName;

        private String ifscCode;

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

        public String getIfscCode() {
            return ifscCode;
        }

        public void setIfscCode(String ifscCode) {
            this.ifscCode = ifscCode;
        }

        public String getDeep_link() {
            return deep_link;
        }

        public void setDeep_link(String deep_link) {
            this.deep_link = deep_link;
        }

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
            return "Data{" +
                    "deep_link='" + deep_link + '\'' +
                    ", merchantIdentifier='" + merchantIdentifier + '\'' +
                    ", transactionIdentifier=" + transactionIdentifier +
                    ", transactionReferenceNumber=" + transactionReferenceNumber +
                    ", schemeCode='" + schemeCode + '\'' +
                    ", bankCode='" + bankCode + '\'' +
                    ", loanAmount=" + loanAmount +
                    ", loanStartDate='" + loanStartDate + '\'' +
                    ", applicationId=" + applicationId +
                    ", accountNumber='" + accountNumber + '\'' +
                    ", beneficiaryName='" + beneficiaryName + '\'' +
                    ", ifscCode='" + ifscCode + '\'' +
                    '}';
        }
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public boolean getResponse() {
        return response;
    }

    public void setResponse(boolean response) {
        this.response = response;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "ENachIntitiationResponseDTO{" +
                "data=" + data +
                ", response='" + response + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
