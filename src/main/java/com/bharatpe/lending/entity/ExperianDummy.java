package com.bharatpe.lending.entity;

import com.bharatpe.common.entities.BaseEntity;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.ExperianAuditTrail;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "experian_dummy")
public class ExperianDummy extends BaseEntity {

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "ip")
    private String ip;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "response")
    private String response;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "email")
    private String email;

    @Column(name = "rejected")
    private Boolean rejected = false;

    @Column(name = "reason")
    private String reason;

    @Column(name = "requested_loan_amount")
    private Integer requestedLoanAmount;

    @Column(name = "pancard_number")
    private String pancardNumber;

    @Column(name = "tnc")
    private Boolean tnc = true;

    @Column(name = "bp_score")
    private Double bpScore;

    @Column(name = "experian_score")
    private Double experianScore;

    @Column(name = "category")
    private String category;

    @Column(name = "color")
    private String color;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Transient
    private boolean noExperian = false;

    @Transient
    private List<String> maskedMobiles;

    private boolean skip = false;

    private Integer pincode;

    @Column(name = "rejected_date")
    private Date rejectedDate;

    @Column(name = "eligible_amount")
    private Double eligibleAmount;

    @Column(name = "eligible_tenure")
    private String eligibleTenure;

    @Column(name = "loan_type")
    private String loanType;

    public ExperianDummy(Long merchantId, String ip, Double latitude, Double longitude, Integer requestedLoanAmount, String pancardNumber, Double bpScore, Integer retryCount, Integer pincode) {
        this.merchantId = merchantId;
        this.ip = ip;
        this.latitude = latitude;
        this.longitude = longitude;
        this.requestedLoanAmount = requestedLoanAmount;
        this.pancardNumber = pancardNumber;
        this.bpScore = bpScore;
        this.rejected = false;
        this.tnc = true;
        this.retryCount = retryCount;
        this.pincode = pincode;
    }

    public ExperianDummy() {
    }

    @PrePersist
    void prePersist() {
        setCreatedAt(new Date());
        setUpdatedAt(new Date());
    }

    @PreUpdate
    void preUpdate() {
        setUpdatedAt(new Date());
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public List<String> getMaskedMobiles() {
        return maskedMobiles;
    }

    public void setMaskedMobiles(List<String> maskedMobiles) {
        this.maskedMobiles = maskedMobiles;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public Double getExperianScore() {
        return experianScore;
    }

    public void setExperianScore(Double experianScore) {
        this.experianScore = experianScore;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getRejected() {
        return rejected;
    }

    public void setRejected(Boolean rejected) {
        this.rejected = rejected;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getRequestedLoanAmount() {
        return requestedLoanAmount;
    }

    public void setRequestedLoanAmount(Integer requestedLoanAmount) {
        this.requestedLoanAmount = requestedLoanAmount;
    }

    public String getPancardNumber() {
        return pancardNumber;
    }

    public void setPancardNumber(String pancardNumber) {
        this.pancardNumber = pancardNumber;
    }

    public Boolean getTnc() {
        return tnc;
    }

    public void setTnc(Boolean tnc) {
        this.tnc = tnc;
    }

    public Double getBpScore() {
        return bpScore;
    }

    public void setBpScore(Double bpScore) {
        this.bpScore = bpScore;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public boolean isNoExperian() {
        return noExperian;
    }

    public void setNoExperian(boolean noExperian) {
        this.noExperian = noExperian;
    }

    public Integer getPincode() {
        return pincode;
    }

    public void setPincode(Integer pincode) {
        this.pincode = pincode;
    }

    public Date getRejectedDate() {
        return rejectedDate;
    }

    public void setRejectedDate(Date rejectedDate) {
        this.rejectedDate = rejectedDate;
    }

    public Double getEligibleAmount() {
        return eligibleAmount;
    }

    public void setEligibleAmount(Double eligibleAmount) {
        this.eligibleAmount = eligibleAmount;
    }

    public String getEligibleTenure() {
        return eligibleTenure;
    }

    public void setEligibleTenure(String eligibleTenure) {
        this.eligibleTenure = eligibleTenure;
    }

    public String getLoanType() {
        return loanType;
    }

    public void setLoanType(String loanType) {
        this.loanType = loanType;
    }

    @Override
    public String toString() {
        return "Experian{" +
                "merchantId=" + merchantId +
                ", ip='" + ip + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", response='" + response + '\'' +
                ", merchantName='" + merchantName + '\'' +
                ", email='" + email + '\'' +
                ", rejected=" + rejected +
                ", reason='" + reason + '\'' +
                ", requestedLoanAmount=" + requestedLoanAmount +
                ", pancardNumber='" + pancardNumber + '\'' +
                ", tnc=" + tnc +
                ", bpScore=" + bpScore +
                ", experianScore=" + experianScore +
                ", category='" + category + '\'' +
                ", color='" + color + '\'' +
                ", retryCount=" + retryCount +
                ", noExperian=" + noExperian +
                ", pincode=" + pincode +
                '}';
    }

    public static ExperianDummy createObject(Experian experian) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ExperianDummy experianDummy = objectMapper.convertValue(experian, ExperianDummy.class);
        experianDummy.setId(null);
        return experianDummy;
    }
}
