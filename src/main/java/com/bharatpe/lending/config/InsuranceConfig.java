package com.bharatpe.lending.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = InsuranceConfig.PREFIX)
public class InsuranceConfig {

    public static final String PREFIX = "com.bharatpe.lmsbackend.constant.loan.insurance";

    public String piramalBenefits = "https://d30gqtvesfc1d5.cloudfront.net/hubble/documents/piramal-insurance-know-more-1716731371339.pdf";
    public String payUBenefits = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/PayU____IL_Insurance_Attachment_One_Pager_VF_(1)-1759149611274.pdf";
    public String muthootBenefits = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/ACKO_KNOW_MORE-1766570599854.pdf";
    public String careInsuranceEmail = "customerfirst@careinsurance.com,claimcentre.partners@careinsurance.com";
    public String careInsuranceMobile = "18002004488,8860402452";

    public static final String ICICI_INSURANCE_EMAIL = "customersupport@icicilombard.com";
    public static final String CUSTOMER_CLAIMS_EMAIL_ID = "cx.partnersupport@payufin.com";
    public static final String ICICI_TOLL_FREE = "18002666";
    public static final String ICICI_WHATSAPP = "8860402452";

    public static final String MUTHOOT_INSURANCE_EMAIL = "muthootcare@acko.com";
    public static final String MUTHOOT_TOLL_FREE = "18002662256";

    public final Map<String, String> piramalInsuranceContactDetails = createPiramalInsuranceContactDetails();
    public final Map<String, String> payUInsuranceContactDetails = createPayUInsuranceContactDetails();
    public final Map<String, String> muthootInsuranceContactDetails = createMuthootInsuranceContactDetails();

    private Map<String, String> createPiramalInsuranceContactDetails() {
        Map<String, String> map = new HashMap<>();
        map.put("email_id", careInsuranceEmail);
        map.put("mobile_no", careInsuranceMobile);
        return Collections.unmodifiableMap(map);
    }

    private Map<String, String> createPayUInsuranceContactDetails() {
        Map<String, String> map = new HashMap<>();
        map.put("customer_complaint_email_id", ICICI_INSURANCE_EMAIL);
        map.put("customer_claims_email_id", CUSTOMER_CLAIMS_EMAIL_ID);
        map.put("tollfree", ICICI_TOLL_FREE);
        map.put("whatsapp", ICICI_WHATSAPP);
        return Collections.unmodifiableMap(map);
    }

    private Map<String, String> createMuthootInsuranceContactDetails() {
        Map<String, String> map = new HashMap<>();
        map.put("email_id", MUTHOOT_INSURANCE_EMAIL);
        map.put("mobile_no", MUTHOOT_TOLL_FREE);
        return Collections.unmodifiableMap(map);
    }
}