package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Gst3bSessionRequestDTO {
    String gstin;
    String username;
    String requestId;
    String otp;
    String base64;
    String fileName;
}
