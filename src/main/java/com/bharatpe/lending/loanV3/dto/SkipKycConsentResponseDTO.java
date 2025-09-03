package com.bharatpe.lending.loanV3.dto;

import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkipKycConsentResponseDTO {
    private String kycMode;
    private LendingViewStates nextPage;
}
