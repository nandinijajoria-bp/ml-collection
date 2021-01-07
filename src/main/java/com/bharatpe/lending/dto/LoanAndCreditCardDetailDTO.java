package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class LoanAndCreditCardDetailDTO {

    @JsonProperty("credit_card")
    private List<CreditCardDetail> creditCardDetail;

    @JsonProperty("loan_detail")
    private List<LoanDetail> loanDetail;

    public List<CreditCardDetail> getCreditCardDetail() {
        return creditCardDetail;
    }

    public void setCreditCardDetail(List<CreditCardDetail> creditCardDetail) {
        this.creditCardDetail = creditCardDetail;
    }

    @JsonProperty("experian_number")
    private String experianNumber;

    public String getExperianNumber() {
        return experianNumber;
    }

    public void setExperianNumber(String experianNumber) {
        this.experianNumber = experianNumber;
    }

    public List<LoanDetail> getLoanDetail() {
        return loanDetail;
    }

    public void setLoanDetail(List<LoanDetail> loanDetail) {
        this.loanDetail = loanDetail;
    }

    public class LoanDetail{
        private String bankName;
        private boolean status;
        private String accountNumber;
        private String currentBalance;
        private String tenure;
        private String rateOfInterest;
        private Integer sanctionedAmount;

        public String getBankName() {
            return bankName;
        }

        public void setBankName(String bankName) {
            this.bankName = bankName;
        }

        public boolean isStatus() {
            return status;
        }

        public void setStatus(boolean status) {
            this.status = status;
        }

        public String getAccountNumber() {
            return accountNumber;
        }

        public void setAccountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
        }

        public String getCurrentBalance() {
            return currentBalance;
        }

        public void setCurrentBalance(String currentBalance) {
            this.currentBalance = currentBalance;
        }

        public String getTenure() {
            return tenure;
        }

        public void setTenure(String tenure) {
            this.tenure = tenure;
        }

        public String getRateOfInterest() {
            return rateOfInterest;
        }

        public void setRateOfInterest(String rateOfInterest) {
            this.rateOfInterest = rateOfInterest;
        }

        public Integer getSanctionedAmount() {
            return sanctionedAmount;
        }

        public void setSanctionedAmount(int sanctionedAmount) {
            this.sanctionedAmount = sanctionedAmount;
        }
    }

    public class CreditCardDetail{
        private String bankName;
        private boolean status;
        private String creditCardNumber;
        private Integer cardLimit;
        private Integer balance;

        public Integer getCardLimit() {
            return cardLimit;
        }

        public void setCardLimit(Integer cardLimit) {
            this.cardLimit = cardLimit;
        }

        public Integer getBalance() {
            return balance;
        }

        public void setBalance(Integer balance) {
            this.balance = balance;
        }

        public String getBankName() {
            return bankName;
        }

        public void setBankName(String bankName) {
            this.bankName = bankName;
        }

        public boolean isStatus() {
            return status;
        }

        public void setStatus(boolean status) {
            this.status = status;
        }

        public String getCreditCardNumber() {
            return creditCardNumber;
        }

        public void setCreditCardNumber(String creditCardNumber) {
            this.creditCardNumber = creditCardNumber;
        }
    }
}
