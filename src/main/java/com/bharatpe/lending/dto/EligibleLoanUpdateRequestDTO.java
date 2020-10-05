package com.bharatpe.lending.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EligibleLoanUpdateRequestDTO implements Serializable {

    private String category;

    private Double amount;

    private Integer edi;

    @JsonProperty("io_edi")
    private Integer ioEdi;

    @JsonProperty("io_edi_days")
    private Integer ioEdiDays;

    @JsonProperty("edi_free_days")
    private Integer ediFreeDays;

    private Integer repayment;

    public EligibleLoanUpdateRequestDTO() {
    }

    public EligibleLoanUpdateRequestDTO(String category, Double amount, Integer edi, Integer ioEdi, Integer ioEdiDays,
            Integer ediFreeDays, Integer repayment) {
        this.category = category;
        this.amount = amount;
        this.edi = edi;
        this.ioEdi = ioEdi;
        this.ioEdiDays = ioEdiDays;
        this.ediFreeDays = ediFreeDays;
        this.repayment = repayment;
    }

    public String getCategory() {
        return this.category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getAmount() {
        return this.amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Integer getEdi() {
        return this.edi;
    }

    public void setEdi(Integer edi) {
        this.edi = edi;
    }

    public Integer getIoEdi() {
        return this.ioEdi;
    }

    public void setIoEdi(Integer ioEdi) {
        this.ioEdi = ioEdi;
    }

    public Integer getIoEdiDays() {
        return this.ioEdiDays;
    }

    public void setIoEdiDays(Integer ioEdiDays) {
        this.ioEdiDays = ioEdiDays;
    }

    public Integer getEdiFreeDays() {
        return this.ediFreeDays;
    }

    public void setEdiFreeDays(Integer ediFreeDays) {
        this.ediFreeDays = ediFreeDays;
    }

    public Integer getRepayment() {
        return this.repayment;
    }

    public void setRepayment(Integer repayment) {
        this.repayment = repayment;
    }

    @Override
    public String toString() {
        return "EligibleLoanUpdateRequestDTO{" + " category='" + getCategory() + "'" + ", amount='" + getAmount() + "'"
                + ", edi='" + getEdi() + "'" + ", ioEdi='" + getIoEdi() + "'" + ", ioEdiDays='" + getIoEdiDays() + "'"
                + ", ediFreeDays='" + getEdiFreeDays() + "'" + ", repayment='" + getRepayment() + "'" + "}";
    }
}