package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Data
public class AadhaarAddressResponseDTO {
    private String address;
    private String city;
    private String pincode;
    private String state;
    private String name;
    private String dob;
    private String gender;
    private String aadharNumber;
}
