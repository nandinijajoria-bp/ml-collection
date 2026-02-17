package com.bharatpe.lending.loanV3.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = TrillionLoansConfig.PREFIX)
public class TrillionLoansConfig {
    public static final String PREFIX = "trillionloans";

    private Boolean trillionEkycPhaseRollout = false;

    private String loanSegments;
    private String riskSegments;
    private String loanTypes;
    private String pinCodeColors;
    private String riskGroups;

    private double minAmount;
    private double maxAmount;

    private String eKycStatusCheck = "EKYC_STATUS_CHECK";

    private Integer eKycRetryCount = 3;
    private Integer foreclosureDetailsTimeoutThreshold = 20000;

    private List<String> merchantSummaryFieldsToRemove = Arrays.asList("chargeback_flag", "abfl_mca_score");
    private List<String> deTpvDataFieldsToRemove = Arrays.asList("fos_app_cnt", "driver_app_cnt");
    private List<String> merchantBehaviourFieldsToRetain = Arrays.asList("mbs_ntc_proba", "mbs_repeat_proba", "mbs_fresh_proba");
    private Integer createClientTimeoutThreshold = 15000;
    private Integer createLeadTimeoutThreshold = 15000;
    private Integer docUploadTimeoutThreshold = 15000;
    private Integer kycTimeoutThreshold = 15000;
    private Integer breTimeoutThreshold = 15000;
    private Integer postConsentTimeoutThreshold = 15000;
    private Integer updateLeadTimeoutThreshold = 20000;
    private Integer kycValidityTimeoutThreshold = 20000;
    private Integer skipKycTimeoutThreshold = 20000;
    private Integer topUpSkipKycRolloutPercent = 0;
    private Integer skipKycRolloutPercent = 0;
    private String kycValiditySuccessMessage = "KYC Details Retrieved";
    private Integer kycValidityRetryCount = 3;
    private Integer skipKycRetryCount = 3;
    private String skipKycConsentKey = "KYC_REUSE_CONFIRM";
    private String skipKycConsentSource = "mobile_app";
    private String skipKycSuccessMessage = "KYC Completed";
    private Boolean kycCtaEnabledForTopup = false;
    private String beneficiaryType = "SELF";
}