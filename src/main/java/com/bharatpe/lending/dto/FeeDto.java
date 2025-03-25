package com.bharatpe.lending.dto;

import lombok.Data;

@Data
public class FeeDto {
    private String feeType;
    private double feeAmount; // This includes GST
    private double paidAmount;
    private double waiverAmount;
}
