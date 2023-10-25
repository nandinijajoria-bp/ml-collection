package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.enums.KycStatus;
import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KYCStateDTO {
    private KycStatus kycStatus;
    private String deeplink;
    private Boolean showKycPage;
    private Boolean lenderAssc;
    private boolean isTopup;
    private boolean isFreshKyc;
}