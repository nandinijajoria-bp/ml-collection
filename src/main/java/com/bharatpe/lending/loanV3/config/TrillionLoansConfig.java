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

}