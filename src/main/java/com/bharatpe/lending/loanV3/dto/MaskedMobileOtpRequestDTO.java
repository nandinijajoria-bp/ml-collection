package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MaskedMobileOtpRequestDTO {
    private Long merchantId;
    private String mobile;
    private String bureauMobile;
    private String source;
    private String otp;
    private String uuid;
}
