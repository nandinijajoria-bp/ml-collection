package com.bharatpe.lending.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IneligibleResponseDTO implements Serializable {

    @JsonProperty("max_loan_amt")
    private int maxLoanAmt;

    @JsonProperty("min_loan_amt")
    private int minLoanAmt = 10000;

    @JsonProperty("requested_loan_amt")
    private Integer requestedLoanAmt;

    private boolean isLoanRequested;

    private boolean eligible;

    @JsonProperty("transaction_count_details")
    private Map<String, Object> transactionCountDetails;

    @JsonProperty("transaction_amt_details")
    private Map<String, Object> transactionAmtDetails;

    @JsonProperty("loan_details")
    private Map<String, Object> loanDetails;

    public IneligibleResponseDTO(int previousLoanCount) {
        if (previousLoanCount == 0) {
            this.isLoanRequested = false;
            this.maxLoanAmt = 250000;
        } else {
            this.isLoanRequested = true;
            this.maxLoanAmt = 500000;
        }
    }

    public IneligibleResponseDTO() {
    }

    public int getMaxLoanAmt() {
        return maxLoanAmt;
    }

    public void setMaxLoanAmt(int maxLoanAmt) {
        this.maxLoanAmt = maxLoanAmt;
    }

    public int getMinLoanAmt() {
        return minLoanAmt;
    }

    public void setMinLoanAmt(int minLoanAmt) {
        this.minLoanAmt = minLoanAmt;
    }

    public Integer getRequestedLoanAmt() {
        return requestedLoanAmt;
    }

    public void setRequestedLoanAmt(Integer requestedLoanAmt) {
        this.requestedLoanAmt = requestedLoanAmt;
    }

    public boolean isLoanRequested() {
        return isLoanRequested;
    }

    public void setLoanRequested(boolean loanRequested) {
        isLoanRequested = loanRequested;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public Map<String, Object> getTransactionCountDetails() {
        return transactionCountDetails;
    }

    public void setTransactionCountDetails(Map<String, Object> transactionCountDetails) {
        this.transactionCountDetails = transactionCountDetails;
    }

    public Map<String, Object> getTransactionAmtDetails() {
        return transactionAmtDetails;
    }

    public void setTransactionAmtDetails(Map<String, Object> transactionAmtDetails) {
        this.transactionAmtDetails = transactionAmtDetails;
    }

    public Map<String, Object> getLoanDetails() {
        return loanDetails;
    }

    public void setLoanDetails(Map<String, Object> loanDetails) {
        this.loanDetails = loanDetails;
    }

    @Override
    public String toString() {
        return "IneligibleResponseDTO{" +
                "maxLoanAmt=" + maxLoanAmt +
                ", minLoanAmt=" + minLoanAmt +
                ", requestedLoanAmt=" + requestedLoanAmt +
                ", isLoanRequested=" + isLoanRequested +
                ", eligible=" + eligible +
                ", transactionCountDetails=" + transactionCountDetails +
                ", transactionAmtDetails=" + transactionAmtDetails +
                ", loanDetails=" + loanDetails +
                '}';
    }
}
