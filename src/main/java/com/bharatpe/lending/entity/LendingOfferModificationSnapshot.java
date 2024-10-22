package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table (name = "lending_offer_modification_snapshot")
public class LendingOfferModificationSnapshot extends BaseEntity {

    @Column(name = "application_id")
    private Long applicationId;
    @Column(name = "processing_fee")
    private Double proceeingFee;
    @Column(name = "loan_amount")
    private Double loanAmount;
    @Column(name = "payable_days")
    private Long payableDays;
    @Column(name = "repayment_amount")
    private Double repaymentAmount;
    @Column(name = "disbursal_amount")
    private Double disbursalAmount;
    @Column(name = "edi_amount")
    private Double ediAmount;

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Double getProceeingFee() {
        return proceeingFee;
    }

    public void setProceeingFee(Double proceeingFee) {
        this.proceeingFee = proceeingFee;
    }

    public Double getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(Double loanAmount) {
        this.loanAmount = loanAmount;
    }

    public Long getPayableDays() {
        return payableDays;
    }

    public void setPayableDays(Long payableDays) {
        this.payableDays = payableDays;
    }

    public Double getRepaymentAmount() {
        return repaymentAmount;
    }

    public void setRepaymentAmount(Double repaymentAmount) {
        this.repaymentAmount = repaymentAmount;
    }

    public Double getDisbursalAmount() {
        return disbursalAmount;
    }

    public void setDisbursalAmount(Double disbursalAmount) {
        this.disbursalAmount = disbursalAmount;
    }

    public Double getEdiAmount() {
        return ediAmount;
    }

    public void setEdiAmount(Double ediAmount) {
        this.ediAmount = ediAmount;
    }
}
