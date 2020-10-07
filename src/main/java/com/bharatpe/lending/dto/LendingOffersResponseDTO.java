package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LendingOffersResponseDTO {

    @JsonInclude(JsonInclude.Include.NON_NULL)

    private boolean success = true;

    private String message = "success";

    private Double offerAmount = 0.0;

    private Boolean activeLoan = false;

    private Integer tenure;

    private String applicationStatus;

    private Double tpv;

    public LendingOffersResponseDTO() {
    }

    public LendingOffersResponseDTO(boolean success, String message, Double offerAmount, Boolean activeLoan,
            Integer tenure, String applicationStatus) {
        this.success = success;
        this.message = message;
        this.offerAmount = offerAmount;
        this.activeLoan = activeLoan;
        this.tenure = tenure;
        this.applicationStatus = applicationStatus;
    }

    public boolean isSuccess() {
        return this.success;
    }

    public boolean getSuccess() {
        return this.success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Double getOfferAmount() {
        return this.offerAmount;
    }

    public void setOfferAmount(Double offerAmount) {
        this.offerAmount = offerAmount;
    }

    public Boolean isActiveLoan() {
        return this.activeLoan;
    }

    public Boolean getActiveLoan() {
        return this.activeLoan;
    }

    public void setActiveLoan(Boolean activeLoan) {
        this.activeLoan = activeLoan;
    }

    public Integer getTenure() {
        return this.tenure;
    }

    public void setTenure(Integer tenure) {
        this.tenure = tenure;
    }

    public String getApplicationStatus() {
        return this.applicationStatus;
    }

    public void setApplicationStatus(String applicationStatus) {
        this.applicationStatus = applicationStatus;
    }

    public Double getTpv() {
        return tpv;
    }

    public void setTpv(Double tpv) {
        this.tpv = tpv;
    }

    @Override
    public String toString() {
        return "LendingOffersResponseDTO{" + " success='" + isSuccess() + "'" + ", message='" + getMessage() + "'"
                + ", offerAmount='" + getOfferAmount() + "'" + ", activeLoan='" + isActiveLoan() + "'" + ", tenure='"
                + getTenure() + "'" + ", applicationStatus='" + getApplicationStatus() + "'" + "}";
    }
}
