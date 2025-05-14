package com.bharatpe.lending.lendingplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = LendingPlatformConfiguration.PREFIX)
public class LendingPlatformConfiguration {
    public static final String PREFIX = "com.bharatpe.platform";

    private String baseUrl = "https://lending-connector.bharatpe.co.in";
    private String authHost = "https://lending-connector.bharatpe.co.in";
    private String tokenUsername = "admin";
    private String tokenPassword = "admin";


    private String redisTokenKey = "platform-token";
    private String redisTokenLock = "platform-token-lock";

    private long tokenExpiryInMinutes = 55;
    private long tokenUpdateThresholdInMinutes = 5;

    //API paths
    private String tokenGenerationPath = "/auth/v1/login";
    private String createLeadUrl = "/lender/v1/create_lead";
    private String breUrl = "/lender/v1/bre";
    private String sanctionLoanUrl = "/lender/v1/sanction_loan";
    private String disburseLoanUrl = "/lender/v1/disburse_loan";
    private String performKycUrl = "/lender/v1/kyc";
    private String uploadKycDocumentUrl = "/lender/v1/upload_kyc_document";
    private String updateLeadUrl = "/lender/v1/update_lead";
    private String uploadLoanDocumentUrl = "/lender/v1/upload_loan_document";
    private String signLoanDocumentUrl = "/lender/v1/loan_document_digi_sign";
    private String registerNachUrl = "/lender/v1/register_nach";
    private String eligibilityUrl = "/underwriting/v1/eligibility";
    private String bureauConsentUrl = "/underwriting/v1/bureau_consent";


    public String getTokenGenerationPath() {
        return baseUrl + tokenGenerationPath;
    }

    public String getCreateLeadUrl() {
        return baseUrl + createLeadUrl;
    }

    public String getBreUrl() {
        return baseUrl + breUrl;
    }

    public String getSanctionLoanUrl() {
        return baseUrl + sanctionLoanUrl;
    }

    public String getDisburseLoanUrl() {
        return baseUrl + disburseLoanUrl;
    }

    public String getPerformKycUrl() {
        return baseUrl + performKycUrl;
    }

    public String getUploadKycDocumentUrl() {
        return baseUrl + uploadKycDocumentUrl;
    }

    public String getUpdateLeadUrl() {
        return baseUrl + updateLeadUrl;
    }

    public String getUploadLoanDocumentUrl() {
        return baseUrl + uploadLoanDocumentUrl;
    }

    public String getSignLoanDocumentUrl() {
        return baseUrl + signLoanDocumentUrl;
    }

    public String getRegisterNachUrl() {
        return baseUrl + registerNachUrl;
    }

    public String getEligibilityUrl() {
        return baseUrl + eligibilityUrl;
    }

    public String getBureauConsentUrl() {
        return baseUrl + bureauConsentUrl;
    }


}
