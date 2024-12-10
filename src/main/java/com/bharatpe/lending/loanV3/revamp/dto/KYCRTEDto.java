package com.bharatpe.lending.loanV3.revamp.dto;


import com.bharatpe.lending.enums.KycStatus;
import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KYCRTEDto {
    private KycStatus kycStatus;
    private String deepLink;
    private Boolean showKycPage;
    private Long merchantId;

    public static KYCRTEDto from(KYCStateDTO kycStateDTO)
    {
        return KYCRTEDto.builder().
                kycStatus(kycStateDTO.getKycStatus())
                .deepLink(kycStateDTO.getDeeplink())
                .showKycPage(kycStateDTO.getShowKycPage())
                .build();
    }
}
