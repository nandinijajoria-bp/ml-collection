package com.bharatpe.lending.service.merchant.dto;

import com.bharatpe.common.entities.Merchant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class BasicDetailsDto {

    String name;
    String bussinessName;
    String bussinessCategory;
    String merchantType;
    String mobile;
    String city;
    String state;
    String address;
    Integer zipCode;
    Double latitude;
    Double longitude;
    String panNumber;
    String gstnNo;
    String shopType;
    String beneficiaryName;
    String subCategory;
    String companyType;
    Long id;
    String mid;
    Long merchantUserID;

    @JsonProperty("created_at")
    Date createdAt;
}
