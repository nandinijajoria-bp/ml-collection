package com.bharatpe.lending.loanV3.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@NoArgsConstructor
@Configuration
@ConfigurationProperties(prefix = OxyzoConfig.PREFIX)
public class OxyzoConfig {

    public static final String PREFIX = "oxyzo";

    private String customerType = "SELF_EMPLOYED";
    private String paymentFrequency = "DAILY";
    private String loanType = "TERM_LOAN";
    private String residentBifurcation = "INDIAN";
    private String productType = "PERSONAL_LOAN";
    private String callbackUrl = "https://api-nbfc-qa.bharatpe.co.in/lending/oxyzo/v1/callback/applyLoan";
    private String successErrorCode = "00";
    private Integer rolloutPercentage = 1;
    private Double maxIrr = 39D;
    private String corporateName = "Oxyzo Financial Services Limited";
    private String businessAddress = "#101, Vipul Agora Mall, MG Road, Gurugram – 122001";
    private String contactName = "Mr. Abhishek Goyal";
    private String contactEmail = "getsupport@oxyzo.in";
    private String contactNumber = "+91-7353013499";
    private String grievanceTIme = "Monday to Friday from 10:00 a.m to 7:00 p.m";
    private String foreclosureTopic = "oxyzo-foreclose-loan";


}
