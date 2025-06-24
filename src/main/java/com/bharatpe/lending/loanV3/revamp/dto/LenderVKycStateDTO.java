package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.common.enums.VkycStatus;
import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LenderVKycStateDTO {
    private long applicationId;
    private Boolean vkycCompleted;
    private VkycStatus vKycStatus;
    private Boolean vkycEligible;
    private Boolean dkycEligible;
    private String lender;
    private String rejectReason;
}
