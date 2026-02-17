package com.bharatpe.lending.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmiServiceResponse {
    private boolean success;
    private String status_message;
    private String timestamp;
    private Result result;
}