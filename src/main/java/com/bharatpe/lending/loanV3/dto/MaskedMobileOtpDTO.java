package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaskedMobileOtpDTO {
    private boolean success;
    private String message;
    private DataDTO data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataDTO {
        private String mobile;
        private String otp;
        @JsonProperty("merchant_id")
        private Long merchantId;
        @JsonProperty("bureau_mobile")
        private String bureauMobile;
        private String source;
        @JsonProperty("otp_sent")
        private boolean otpSent;
        @JsonProperty("otp_verified")
        private boolean otpVerified;
        @JsonProperty("otp_tries_left")
        private int otpTriesLeft;
        @JsonProperty("otp_retry_limit_reached")
        private boolean otpRetryLimitReached;
        @JsonProperty("uuid")
        private String uuid;
    }
}