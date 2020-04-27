package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PartnerDetailsRequestDTO {

    private String partner;

    private String name;

    private String mobile;

    private String email;

    private String restaurantName;

    private String legalBusinessName;

    private String partnerOnboardingDate;

    public String getPartner() {
        return partner;
    }

    public void setPartner(String partner) {
        this.partner = partner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }

    public String getLegalBusinessName() {
        return legalBusinessName;
    }

    public void setLegalBusinessName(String legalBusinessName) {
        this.legalBusinessName = legalBusinessName;
    }

    public String getPartnerOnboardingDate() {
        return partnerOnboardingDate;
    }

    public void setPartnerOnboardingDate(String partnerOnboardingDate) {
        this.partnerOnboardingDate = partnerOnboardingDate;
    }

    @Override
    public String toString() {
        return "PartnerDetailsRequestDTO{" +
                "partner='" + partner + '\'' +
                ", name='" + name + '\'' +
                ", mobile='" + mobile + '\'' +
                ", email='" + email + '\'' +
                ", restaurantName='" + restaurantName + '\'' +
                ", legalBusinessName='" + legalBusinessName + '\'' +
                ", partnerOnboardingDate='" + partnerOnboardingDate + '\'' +
                '}';
    }
}
