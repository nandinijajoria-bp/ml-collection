package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LendingAuditTrailDTO {
    private Long id;
    private Long applicationId;
    private Long merchantId;
    private String shopNumber;
    private String streetAddress;
    private String area;
    private String landmark;
    private String pincode;
    private String city;
    private String state;
    private String businessName;
    private String screen;
    private Date createdAt;
    private Date updatedAt;
    private String source; // LMS/Lending Journey
    private String remarks;
}