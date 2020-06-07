package com.bharatpe.lending.entity;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "lending_prebook_target")
public class LendingPrebookTarget {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "merchant_id")
    private Long merchantId;

    private String segment;

    @Column(name = "application_id")
    private Long applicationId;

    private Long pincode;

    private Double target;

    @Column(name = "lockdown_end_date")
    private Date lockdownEndDate;

    @Column(name = "target_achieve_date")
    private Date targetAchieveDate;

    @Column(name = "target_achieved")
    private Boolean targetAchieved;

    public LendingPrebookTarget(Long merchantId, String segment, Long applicationId, Long pincode, Double target, Date lockdownEndDate, Date targetAchieveDate) {
        this.merchantId = merchantId;
        this.segment = segment;
        this.applicationId = applicationId;
        this.pincode = pincode;
        this.target = target;
        this.lockdownEndDate = lockdownEndDate;
        this.targetAchieveDate = targetAchieveDate;
    }

    public LendingPrebookTarget() {
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getPincode() {
        return pincode;
    }

    public void setPincode(Long pincode) {
        this.pincode = pincode;
    }

    public Double getTarget() {
        return target;
    }

    public void setTarget(Double target) {
        this.target = target;
    }

    public Date getLockdownEndDate() {
        return lockdownEndDate;
    }

    public void setLockdownEndDate(Date lockdownEndDate) {
        this.lockdownEndDate = lockdownEndDate;
    }

    public Date getTargetAchieveDate() {
        return targetAchieveDate;
    }

    public void setTargetAchieveDate(Date targetAchieveDate) {
        this.targetAchieveDate = targetAchieveDate;
    }

    public Boolean getTargetAchieved() {
        return targetAchieved;
    }

    public void setTargetAchieved(Boolean targetAchieved) {
        this.targetAchieved = targetAchieved;
    }

    @Override
    public String toString() {
        return "LendingPrebookTarget{" +
                "merchantId=" + merchantId +
                ", segment='" + segment + '\'' +
                ", applicationId=" + applicationId +
                ", pincode='" + pincode + '\'' +
                ", target=" + target +
                ", lockdownEndDate=" + lockdownEndDate +
                ", targetAchieveDate=" + targetAchieveDate +
                '}';
    }
}
