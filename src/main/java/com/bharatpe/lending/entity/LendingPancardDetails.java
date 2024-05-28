package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name="lending_pancard")
public class LendingPancardDetails extends BaseEntity {

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "pancard_number")
    private String pancardNumber;

    @Column(name = "name")
    private String name;

    @Column(name = "gst_number")
    private String gstNumber;

    @Column(name = "response")
    private String response;

    @Column(name="version")
    public String version;

    @Column(name = "aadhaar_seeding_status")
    public String aadhaarSeedingStatus;

    public LendingPancardDetails(Long merchantId, String pancardNumber, String name, String response) {
        this.merchantId = merchantId;
        this.pancardNumber = pancardNumber;
        this.name = name;
        this.response = response;
    }

    public LendingPancardDetails(Long merchantId, String pancardNumber, String name, String response, String version, String aadhaarSeedingStatus) {
        this.merchantId = merchantId;
        this.pancardNumber = pancardNumber;
        this.name = name;
        this.response = response;
        this.version = version;
        this.aadhaarSeedingStatus = aadhaarSeedingStatus;
    }

    public LendingPancardDetails(Long merchantId, String pancardNumber, String name) {
        this.merchantId = merchantId;
        this.pancardNumber = pancardNumber;
        this.name = name;
    }

    public LendingPancardDetails() {
    }

    public Long LendingPancardDetails() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getPancardNumber() {
        return pancardNumber;
    }

    public void setPancardNumber(String pancardNumber) {
        this.pancardNumber = pancardNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getGstNumber() {
        return gstNumber;
    }

    public void setGstNumber(String gstNumber) {
        this.gstNumber = gstNumber;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAadhaarSeedingStatus() {
        return aadhaarSeedingStatus;
    }

    public void setAadhaarSeedingStatus(String aadhaarSeedingStatus) {
        this.aadhaarSeedingStatus = aadhaarSeedingStatus;
    }

    @Override
    public String toString() {
        return "LendingPancardDetails{" +
                "merchantId=" + merchantId +
                ", pancardNumber='" + pancardNumber + '\'' +
                ", name='" + name + '\'' +
                ", gstNumber='" + gstNumber + '\'' +
                ", response='" + response + '\'' +
                ", version='" + version + '\'' +
                ", aadhaarSeedingStatus='" + aadhaarSeedingStatus + '\'' +
                '}';
    }
}
