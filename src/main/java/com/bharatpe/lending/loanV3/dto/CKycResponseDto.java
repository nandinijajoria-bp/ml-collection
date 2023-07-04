package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CKycResponseDto {
    String selfieBase64;
    String poAXml;
    String address;
    String pincode;
    String state;
    String city;
    String name;
    String gender;
    String panNumber;
    String aadharNumber;
    String mobile;
    String dob;
    String firstName;
    String middleName;
    String lastName;
    String email;
    String poaString;
    String selfieString;
}
