package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;

public class CreditScoreResponseDto {
    @JsonProperty("pan_number")
    private  String panNumber;

    private String bureau;

    @JsonProperty("pin_code")
    private  Integer pinCode;

    @JsonProperty("credit_date")
    private Date creditDate;

    private Double score;

    private String message;

    @JsonProperty("application_pending")
    private boolean applicationPending;

    @JsonProperty("active_loan")
    private boolean activeLoan;

    @JsonProperty("pan_name")
    private String panName;

    @JsonProperty("no_experian")
    private boolean noExperian;

    private boolean timeout;

    @JsonProperty("masked_mobile")
    private List<String> maskedMobiles;

    private List<LoanEligibilityDTO> eligibility;

    public String getPanNumber() {
        return panNumber;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber;
    }

    public String getBureau() {
        return bureau;
    }

    public void setBureau(String bureau) {
        this.bureau = bureau;
    }

    public Date getCreditDate() {
        return creditDate;
    }

    public void setCreditDate(Date creditDate) {
        this.creditDate = creditDate;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getPanName() {
        return panName;
    }

    public void setPanName(String panName) {
        this.panName = panName;
    }

    public List<LoanEligibilityDTO> getEligibility() {
        return eligibility;
    }

    public void setEligibility(List<LoanEligibilityDTO> eligibility) {
        this.eligibility = eligibility;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isNoExperian() {
        return noExperian;
    }

    public void setNoExperian(boolean noExperian) {
        this.noExperian = noExperian;
    }

    public boolean isApplicationPending() {
        return applicationPending;
    }

    public void setApplicationPending(boolean applicationPending) {
        this.applicationPending = applicationPending;
    }

    public boolean isActiveLoan() {
        return activeLoan;
    }

    public void setActiveLoan(boolean activeLoan) {
        this.activeLoan = activeLoan;
    }

    public List<String> getMaskedMobiles() {
        return maskedMobiles;
    }

    public void setMaskedMobiles(List<String> maskedMobiles) {
        this.maskedMobiles = maskedMobiles;
    }

    public boolean isTimeout() {
        return timeout;
    }

    public void setTimeout(boolean timeout) {
        this.timeout = timeout;
    }

    public Integer getPinCode() {
        return pinCode;
    }

    public void setPinCode(Integer pinCode) {
        this.pinCode = pinCode;
    }
}
