package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LendingApplicationRequestDTO {

    @JsonProperty("application_id")
    private Long applicationId;

    private String category;

    @JsonProperty("mobile_number")
    private String mobile;

    @JsonProperty("business_name")
    private String businessName;

    @JsonProperty("shop_number")
    private String shopNumber;

    @JsonProperty("street_address")
    private String streetAddress;

    private String area;

    private String landmark;

    private Long pincode;

    private String city;

    private String state;

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getShopNumber() {
        return shopNumber;
    }

    public void setShopNumber(String shopNumber) {
        this.shopNumber = shopNumber;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getLandmark() {
        return landmark;
    }

    public void setLandmark(String landmark) {
        this.landmark = landmark;
    }

    public Long getPincode() {
        return pincode;
    }

    public void setPincode(Long pincode) {
        this.pincode = pincode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "LendingApplicationRequestDTO{" +
                "applicationId=" + applicationId +
                ", category='" + category + '\'' +
                ", mobile='" + mobile + '\'' +
                ", businessName='" + businessName + '\'' +
                ", shopNumber='" + shopNumber + '\'' +
                ", streetAddress='" + streetAddress + '\'' +
                ", area='" + area + '\'' +
                ", landmark='" + landmark + '\'' +
                ", pincode='" + pincode + '\'' +
                ", city='" + city + '\'' +
                ", state='" + state + '\'' +
                '}';
    }
}
