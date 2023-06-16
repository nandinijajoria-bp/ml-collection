package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.enums.EligibilityIframeState;
import lombok.Data;

@Data
public class EligibilityIframeResponseDTO {
    private EligibilityIframeState state;
    private Double offerAmount;
    private Double offerInterestRate;
    private Double loanAmount;
    private Double interestRate;
    private String title;
    private String subTitle;
    private String buttonText;
    private String alertIcon;
    private String alertText;
    private String bannerImg;
    private String deeplink;
}
