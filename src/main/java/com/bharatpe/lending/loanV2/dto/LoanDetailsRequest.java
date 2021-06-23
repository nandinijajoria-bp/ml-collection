package com.bharatpe.lending.loanV2.dto;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class LoanDetailsRequest {
    private String pancard;
    private String pincode;
    private boolean isIOS = false;
}
