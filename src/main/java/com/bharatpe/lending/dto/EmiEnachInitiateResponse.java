package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmiEnachInitiateResponse {
    private boolean success;
    private String httpStatus;
    private String statusMessage;
    private String requestId;
    private String timeStamp;
    private ENachIntitiationResponseDTO result;
}
