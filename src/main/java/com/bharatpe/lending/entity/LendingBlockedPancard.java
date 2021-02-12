package com.bharatpe.lending.entity;

import javax.persistence.*;

@Entity
@Table(name = "lending_blocked_pancard")
public class LendingBlockedPancard {

    @Id
    private String pancard;

    @Column(name = "merchant_id")
    private Long MerchantId;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "aadhar_number")
    private String aadharNumber;

    public String getPancard() {
        return pancard;
    }

    public void setPancard(String pancard) {
        this.pancard = pancard;
    }

    public Long getMerchantId() {
        return MerchantId;
    }

    public void setMerchantId(Long merchantId) {
        MerchantId = merchantId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAadharNumber() {
        return aadharNumber;
    }

    public void setAadharNumber(String aadharNumber) {
        this.aadharNumber = aadharNumber;
    }
}
