package com.bharatpe.lending.loanV2.dto;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class InitiateKycRequest {
    private Long applicationId;
    private boolean reapply = false;
    private String wroute;
}
