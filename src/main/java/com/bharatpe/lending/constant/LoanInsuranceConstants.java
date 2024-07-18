package com.bharatpe.lending.constant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = LoanInsuranceConstants.PREFIX)
public class LoanInsuranceConstants {

    public static final String PREFIX = "com.bharatpe.lmsbackend.constant.loan.insurance";
    public String careBenefits = "https://drive.google.com/file/d/1-jSdiwUACM4tmzORXjt2VW-IF2hP370K/view?usp=sharing";

    public String careInsuranceEmail = "customerfirst@careinsurance.com,claimcentre.partners@careinsurance.com";
    public String careInsuranceMobile = "18002004488,8860402452";

    public Map<String,String> insuranceContactDetails = new HashMap<String, String>() {{
        put("email_id",careInsuranceEmail);
        put("mobile_no",careInsuranceMobile);
    }};
}
