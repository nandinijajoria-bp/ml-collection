package com.bharatpe.lending.loanV3.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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

}