package com.bharatpe.lending.entity;

import javax.persistence.*;

@Entity
@Table(name = "ogl_loans")
public class OglLoans {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "merchant_id")
    private Long merchantId;

    private String color;

    private String category;

    @Column(name = "interest_rate")
    private Double interestRate;

    private Double amount;

    @Column(name = "external_loan_id")
    private String externalLoanId;

    private Integer edi;

    private Integer repayment;


    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(Double interestRate) {
        this.interestRate = interestRate;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getExternalLoanId() {
        return externalLoanId;
    }

    public void setExternalLoanId(String externalLoanId) {
        this.externalLoanId = externalLoanId;
    }

    public Integer getEdi() {
        return edi;
    }

    public void setEdi(Integer edi) {
        this.edi = edi;
    }

    public Integer getRepayment() {
        return repayment;
    }

    public void setRepayment(Integer repayment) {
        this.repayment = repayment;
    }

    @Override
    public String toString() {
        return "OglLoans{" +
                "merchantId=" + merchantId +
                ", color='" + color + '\'' +
                ", category='" + category + '\'' +
                ", interestRate=" + interestRate +
                ", amount=" + amount +
                ", externalLoanId='" + externalLoanId + '\'' +
                ", edi=" + edi +
                ", repayment=" + repayment +
                '}';
    }
}
