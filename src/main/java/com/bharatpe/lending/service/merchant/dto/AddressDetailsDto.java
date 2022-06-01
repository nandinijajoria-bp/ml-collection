package com.bharatpe.lending.service.merchant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class AddressDetailsDto {

    Double latitude;
    Double longitude;
    String address;
    String add2;
    String landmark;
    String pinCode;
    String city;
    String state;
    String type;

}
