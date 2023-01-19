package com.bharatpe.lending.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EligibleLoanUpdateRequestDTO implements Serializable {

    private String category;

    private Double amount;

    private Integer tenure;

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

    @Override
    public String toString() {
        return "EligibleLoanUpdateRequestDTO{" +
          "category='" + category + '\'' +
          ", amount=" + amount +
          ", tenure=" + tenure +
          ", edi=" + edi +
          ", ioEdi=" + ioEdi +
          ", ioEdiDays=" + ioEdiDays +
          ", ediFreeDays=" + ediFreeDays +
          ", repayment=" + repayment +
          '}';
    }
}