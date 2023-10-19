package com.bharatpe.lending.dto;


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
    public String city;
    public String state;

}
