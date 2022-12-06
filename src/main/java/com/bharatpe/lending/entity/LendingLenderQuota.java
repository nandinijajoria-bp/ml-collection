package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Table(name = "lending_lender_quota")
@NoArgsConstructor
@AllArgsConstructor
public class LendingLenderQuota extends BaseEntity {

    @Column(name = "lender")
    private String lender;

    @Column(name = "total_weekly_amount")
    private Double totalWeeklyAmount;

    @Column(name = "remaining_balance")
    private Double remainingBalance;

    @Column(name = "assigned_amount")
    private Double assignedAmount;

    @Column(name = "edi_model")
    private String ediModel;

    public String getLender() {
        return lender;
    }

    public void setLender(String lender) {
        this.lender = lender;
    }

    public Double getTotalWeeklyAmount() {
        return totalWeeklyAmount;
    }

    public void setTotalWeeklyAmount(Double totalWeeklyLimit) {
        this.totalWeeklyAmount = totalWeeklyLimit;
    }

    public Double getRemainingBalance() {
        return remainingBalance;
    }

    public void setRemainingBalance(Double remainingBalance) {
        this.remainingBalance = remainingBalance;
    }

    public Double getAssignedAmount() {
        return assignedAmount;
    }

    public void setAssignedAmount(Double assignedAmount) {
        this.assignedAmount = assignedAmount;
    }

    public String getEdiModel() {
        return ediModel;
    }

    public void setEdiModel(String ediModel) {
        this.ediModel = ediModel;
    }

    @Override
    public String toString() {
        return "LenderDisbursalLimits{" +
                "lender='" + lender + '\'' +
                ", totalWeeklyLimit=" + totalWeeklyAmount +
                ", remainingBalance=" + remainingBalance +
                ", assignedAmount=" + assignedAmount +
                ", ediModel=" + ediModel +
                '}';
    }
}
