package com.bharatpe.lending.dto;

import lombok.Data;

import java.util.List;

@Data
public class TopUpOfferRequestDto {

    private Long merchantId;
    private Long applicationId;
    private Double amount;
    private String topupLender;
}