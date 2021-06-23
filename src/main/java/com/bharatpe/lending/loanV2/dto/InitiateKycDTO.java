package com.bharatpe.lending.loanV2.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
public class InitiateKycDTO {
    private String referenceId;
    private String panNumber;
    private String merchantId;
    private String callBackUrl;
}
