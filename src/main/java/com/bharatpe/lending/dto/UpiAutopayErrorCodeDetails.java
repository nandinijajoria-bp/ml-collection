package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpiAutopayErrorCodeDetails {
    private String errorCode;
    private String errorMessage;
    private int retryCount;
    private String displayMessage;
}
