package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShopPicturesStateDTO {
    private boolean isDummyMerchant;
    private Long applicationId;
    private Boolean resubmitState = false;
    private String lender;
    private Boolean lenderKycPipe;
    private Boolean lenderAssc;
    private Long merchantId;
}
