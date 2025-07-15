package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UdyamFetchTriggerResponse {
    private Long merchantId;
    private String message;
    private boolean status;
}
