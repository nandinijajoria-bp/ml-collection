package com.bharatpe.lending.dto;

import com.bharatpe.lending.enums.EnachProvider;
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

    private boolean success;

    private String message = "success";

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Data {

        private String mid;

        private String currency = "INR";

        private String pennyAmount = "1.0";

        private String transactionMerchantInitiated = "Y";

        private String paymentInstructionAction = "Y";

        private String paymentInstructionType = "M";

        private String payFrequency = "ADHO";

        private String accountType = "Saving";

        private String deep_link;

        private String merchantIdentifier = "L517110";

        private String transactionIdentifier;

        private Long transactionReferenceNumber;

        private String schemeCode = "First";

        private String bankCode;

        private Double loanAmount;

        private String loanStartDate;

        private Long applicationId;

        private String accountNumber;

        private String beneficiaryName;

        private String ifscCode;

        private String mandate_id;

        private String customer_identifier;

        private String lender;

        private EnachProvider enachProvider;

        private String accessToken;

        private String accessTokenExpiryDate;

        public Data(String transactionIdentifier, Long transactionReferenceNumber, String bankCode, Double loanAmount, String loanStartDate, Long applicationId, String accountNumber, String beneficiaryName, String ifscCode, String mid, String lender, EnachProvider enachProvider) {
            this.transactionIdentifier = transactionIdentifier;
            this.transactionReferenceNumber = transactionReferenceNumber;
            this.bankCode = bankCode;
            this.loanAmount = loanAmount;
            this.loanStartDate = loanStartDate;
            this.applicationId = applicationId;
            this.accountNumber = accountNumber;
            this.beneficiaryName = beneficiaryName;
            this.ifscCode = ifscCode;
            this.mid = mid;
            this.lender = lender;
            this.enachProvider = enachProvider;

        }

        public String getAccountType() {
            return accountType;
        }

        public void setAccountType(String accountType) {
            this.accountType = accountType;
        }

        public String getLender() {
            return lender;
        }

        public void setLender(String lender) {
            this.lender = lender;
        }

        public EnachProvider getEnachProvider() {
            return enachProvider;
        }

        public void setEnachProvider(EnachProvider enachProvider) {
            this.enachProvider = enachProvider;
        }

        public String getMandate_id() {
            return mandate_id;
        }



        public void setMandate_id(String mandate_id) {
            this.mandate_id = mandate_id;
        }



        public String getCustomer_identifier() {
            return customer_identifier;
        }



        public void setCustomer_identifier(String customer_identifier) {
            this.customer_identifier = customer_identifier;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getAccessTokenExpiryDate() {
            return accessTokenExpiryDate;
        }

        public void setAccessTokenExpiryDate(String accessTokenExpiryDate) {
            this.accessTokenExpiryDate = accessTokenExpiryDate;
        }



        public Data() {
        }

        public String getMid() {
            return mid;
        }

        public void setMid(String mid) {
            this.mid = mid;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getPennyAmount() {
            return pennyAmount;
        }

        public void setPennyAmount(String pennyAmount) {
            this.pennyAmount = pennyAmount;
        }

        public String getTransactionMerchantInitiated() {
            return transactionMerchantInitiated;
        }

        public void setTransactionMerchantInitiated(String transactionMerchantInitiated) {
            this.transactionMerchantInitiated = transactionMerchantInitiated;
        }

        public String getPaymentInstructionAction() {
            return paymentInstructionAction;
        }

        public void setPaymentInstructionAction(String paymentInstructionAction) {
            this.paymentInstructionAction = paymentInstructionAction;
        }

        public String getPaymentInstructionType() {
            return paymentInstructionType;
        }

        public void setPaymentInstructionType(String paymentInstructionType) {
            this.paymentInstructionType = paymentInstructionType;
        }

        public String getPayFrequency() {
            return payFrequency;
        }

        public void setPayFrequency(String payFrequency) {
            this.payFrequency = payFrequency;
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

        public String getTransactionIdentifier() {
            return transactionIdentifier;
        }

        public void setTransactionIdentifier(String transactionIdentifier) {
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
            return "Data [mid=" + mid + ", currency=" + currency + ", pennyAmount=" + pennyAmount
                    + ", transactionMerchantInitiated=" + transactionMerchantInitiated + ", paymentInstructionAction="
                    + paymentInstructionAction + ", paymentInstructionType=" + paymentInstructionType
                    + ", payFrequency=" + payFrequency + ", accountType=" + accountType + ", deep_link=" + deep_link
                    + ", merchantIdentifier=" + merchantIdentifier + ", transactionIdentifier=" + transactionIdentifier
                    + ", transactionReferenceNumber=" + transactionReferenceNumber + ", schemeCode=" + schemeCode
                    + ", bankCode=" + bankCode + ", loanAmount=" + loanAmount + ", loanStartDate=" + loanStartDate
                    + ", applicationId=" + applicationId + ", accountNumber=" + accountNumber + ", beneficiaryName="
                    + beneficiaryName + ", ifscCode=" + ifscCode + ", mandate_id=" + mandate_id
                    + ", customer_identifier=" + customer_identifier + ", lender=" + lender
                    + ", accessToken=" + accessToken + ", accessTokenExpiryDate=" + accessTokenExpiryDate +"]";
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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
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
