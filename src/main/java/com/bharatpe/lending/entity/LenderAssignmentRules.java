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
@Table(name = "lender_assignment_rules")
@NoArgsConstructor
@AllArgsConstructor
public class LenderAssignmentRules extends BaseEntity {

    @Column(name = "lender")
    private String lender;

    @Column(name = "loan_type")
    private String loanType;

    @Column(name = "tenure")
    private Integer tenure;

    @Column(name = "min_bureau_score")
    private Double minBureauScore;

    @Column(name = "max_bureau_score")
    private Double maxBureauScore;

    @Column(name = "min_amount")
    private String minAmount;

    @Column(name = "max_amount")
    private String maxAmount;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "is_active")
    private Boolean isActive;

    public String getLender() {
        return lender;
    }

    public void setLender(String lender) {
        this.lender = lender;
    }

    public String getLoanType() {
        return loanType;
    }

    public void setLoanType(String loanType) {
        this.loanType = loanType;
    }

    public Integer getTenure() {
        return tenure;
    }

    public void setTenure(Integer tenure) {
        this.tenure = tenure;
    }

    public Double getMinBureauScore() {
        return minBureauScore;
    }

    public void setMinBureauScore(Double minBureauScore) {
        this.minBureauScore = minBureauScore;
    }

    public Double getMaxBureauScore() {
        return maxBureauScore;
    }

    public void setMaxBureauScore(Double maxBureauScore) {
        this.maxBureauScore = maxBureauScore;
    }

    public String getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(String minAmount) {
        this.minAmount = minAmount;
    }

    public String getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(String maxAmount) {
        this.maxAmount = maxAmount;
    }

    public Boolean getDefault() {
        return isDefault;
    }

    public void setDefault(Boolean aDefault) {
        isDefault = aDefault;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    @Override
    public String toString() {
        return "LenderAssignmentRules{" +
                "lender='" + lender + '\'' +
                ", loanType='" + loanType + '\'' +
                ", tenure=" + tenure +
                ", minBureauScore=" + minBureauScore +
                ", maxBureauScore=" + maxBureauScore +
                ", minAmount='" + minAmount + '\'' +
                ", maxAmount='" + maxAmount + '\'' +
                ", isDefault=" + isDefault +
                ", isActive=" + isActive +
                '}';
    }
}
