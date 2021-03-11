package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
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

    private String entityType;

    private String experience;

    private String salary;

    private Boolean hasGST;

    private String gstNumber;

    private String businessCategory;

    private String shopType;

    @JsonProperty("offer_type")
    private String offerType;

    @JsonProperty("alternative_contact")
    private AlternateContact alternativeContact;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AlternateContact {

        private String name;
        private String phoneNumber;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }
    }

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

    public String getOfferType() {
        return offerType;
    }

    public void setOfferType(String offerType) {
        this.offerType = offerType;
    }

    public AlternateContact getAlternativeContact() {
        return alternativeContact;
    }

    public void setAlternativeContact(AlternateContact alternativeContact) {
        this.alternativeContact = alternativeContact;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public String getSalary() {
        return salary;
    }

    public void setSalary(String salary) {
        this.salary = salary;
    }

    public Boolean getHasGST() {
        return hasGST;
    }

    public void setHasGST(Boolean hasGST) {
        this.hasGST = hasGST;
    }

    public String getGstNumber() {
        return gstNumber;
    }

    public void setGstNumber(String gstNumber) {
        this.gstNumber = gstNumber;
    }

    public String getBusinessCategory() {
        return businessCategory;
    }

    public void setBusinessCategory(String businessCategory) {
        this.businessCategory = businessCategory;
    }

    public String getShopType() {
        return shopType;
    }

    public void setShopType(String shopType) {
        this.shopType = shopType;
    }

    @Override
    public String toString() {
        return "LendingApplicationRequest{" +
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
                ", offerType='" + offerType + '\'' +
                '}';
    }
}
