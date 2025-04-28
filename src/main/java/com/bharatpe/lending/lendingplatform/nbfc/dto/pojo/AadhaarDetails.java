package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import lombok.Builder;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
@Builder
public class AadhaarDetails {
    private String address;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dob;
    private String city;
    private String name;
    private long pincode;
    private String state;
    private String gender;
    private String aadhaarNumber;
    private String careOf;
    private String aadhaarXML;

}