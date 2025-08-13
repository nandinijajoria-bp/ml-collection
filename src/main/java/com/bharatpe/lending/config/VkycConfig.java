package com.bharatpe.lending.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@NoArgsConstructor
@Configuration
@ConfigurationProperties(prefix = VkycConfig.PREFIX)
public class VkycConfig {

    public static final String PREFIX = "vkyc";

    private String enabledLenders = "";
    private String dkycEligibleLenders = "PAYU";
    private Integer aadhaarExpiryTatInHours = 72;
    private Integer maxRetryCount = 3;
    private String redirectUrl = "https://easy-loans-v2.bharatpe.co/_kyc_callback.html";
    private Integer rolloutPercentage = 0;
    private String checkVkycEligibilityUrl = "/api/v1/vkyc/check-vkyc-eligibility";
    private String initiateVkycUrl = "/api/v1/vkyc//init-session";
    private String vkycStatusUrl = "/api/v1/vkyc/status-check";
    private String skipVkycUrl = "/api/v1/vkyc/skip-vkyc";
    private Integer minAppVersion = 7295;
    private Integer maxRetryCountForCs = 4;


    // New integration related configs
    private String retryExhaustFlowEnabledLenders = "PAYU,CREDITSAISON";
    private String eligibilityCheckLenders = "PAYU,CREDITSAISON";
    private Integer aadhaarExpiryTatInHousForCreditSaison = 24; // 24 hours for Credit Saison
    private Integer minAppVersionForCs = 7298;

}
