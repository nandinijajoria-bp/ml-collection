package com.bharatpe.lending.dto;

import com.bharatpe.lending.loanV2.dto.UnderwritingDocEligibilityDTO;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class FosMerchantEligibilityDto {
    String eligibility;
    Long merchantId;
    Integer priority;
    String offerType;
    String loanType;
    String reason;
    int nachTask;
    UpgradeLoanOfferEligibility upgradeLoanOfferEligibility;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UpgradeLoanOfferEligibility {
        Boolean eligibility;
        Boolean BsActive;
        Boolean AaActive;
        Boolean gst3bActive;
    }
}
