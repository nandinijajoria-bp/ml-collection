package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShopDetailsStateDTO {
    private boolean isDummyMerchant;
    private String businessName;
    private String businessCategory;
    private String businessSubCategory;
    private String pincode;
    private Long applicationId;
    private String applicationStatus;
    private String resubmitReason;
    private Boolean resubmitDone;
    private boolean resubmitState = false;
}
