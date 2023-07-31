package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class PANPINStateDTO {
    private boolean success;
    private String errorString;
    private boolean hasExperian;
    private String pancard;
    private String pincode;
    private String merchantName;
}
