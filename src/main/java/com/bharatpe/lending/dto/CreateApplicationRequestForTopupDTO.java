package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateApplicationRequestForTopupDTO {

    @JsonProperty("eligible_loan_id")
    private Long eligibleLoanId;

    @JsonProperty("lat")
    private String latitude;

    @JsonProperty("lon")
    private String longitude;

    private String ip;
    public Long getEligibleLoanId() {
        return eligibleLoanId;
    }

    public void setEligibleLoanId(Long eligibleLoanId) {
        this.eligibleLoanId = eligibleLoanId;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @Override
    public String toString() {
        return "CreateApplicationRequestForTopupDTO{" +
                "eligibleLoanId=" + eligibleLoanId +
                ", latitude='" + latitude + '\'' +
                ", longitude='" + longitude + '\'' +
                ", ip='" + ip + '\'' +
                '}';
    }
}
