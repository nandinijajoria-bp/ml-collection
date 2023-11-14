package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class DEPinCode {
    public String geohash12;
    public String geohash6;
    public String geohash5;
    public int pincode;
    public double lat;
    public double lon;
    @JsonIgnoreProperties(ignoreUnknown = true)
    public String city;
    @JsonIgnoreProperties(ignoreUnknown = true)

    public String state;

}
