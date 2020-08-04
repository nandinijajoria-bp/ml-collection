package com.bharatpe.lending.constant;

import java.util.Arrays;
import java.util.List;

public interface ExperianConstants {

    enum COLOR {
        RED, AMBER, LIGHT_GREEN, DARK_GREEN;
    }

    Boolean LOCKDOWN = false;

    String SHORT_API_URL = "https://consumer.experian.in:8443/ECV-P2/content/enhancedMatch.action";
    String LONG_API_URL = "https://consumer.experian.in:8443/ECV-P2/content/singleAction.action";
    String MASKED_MOBILE_URL = "https://consumer.experian.in:8443/ECV-P2/content/generateMaskedDeliveryData.action";
    String AUTHENTICATE_MOBILE_URL = "https://consumer.experian.in:8443/ECV-P2/content/authenticateDeliveryData.action";
    String CLIENT_NAME = "BHARATPE_EM";
    String VOUCHER_CODE = "BharatPe214K2";
    String CREDIT_LINE_CATEGORY = "CREDIT_LINE_CATEGORY";
    String INVALID_PANCARD = "INVALID_PANCARD";
    String LOW_BP_SCORE = "LOW_BP_SCORE";
    String FRAUD = "FRAUD";
    String OVERDUE = "OVERDUE";
    String LOW_TPV = "LOW_TPV";
    String VINTAGE = "VINTAGE";
    String CATEGORY_RED = "CATEGORY_RED";
    String OGL = "OGL";
    String DEROG_ACCOUNT_STATUS = "Loan default / partial settlement";
    String DEROG_DPD_LAST_3_MONTHS = "Late repayment (5+ days) in last 3 months";
    String DEROG_DPD_LAST_6_MONTHS = "Late repayment (30+ days) in last 6 months";
    String DEROG_DPD_LAST_24_MONTHS = "Late repayment (90+ days) in last 24 months";
    String DEROG_DPD_OLDER_THAN_24_MONTHS = "Late repayment (60+ days) in older than 2 year loans";
    String DEROG_DPD_LAST_12_MONTHS = "Late repayment (60+ days) in last 12 months";
    String DEROG_UNSECURED_LOAN_ENQUIRY = "High unsecured loan enquiries";
    String DEROG_UNSECURED_LOANS = "More than 3 Unsecured Loans";
    String DEROG_MORE_THAN_6_LOAN_ENQUIRY = "High number of loan enquiries in last 6 months";
    
    List<String> RED = Arrays.asList("1","2","13","25");
    List<String> AMBER = Arrays.asList("3","4","5","6","14","15","16","17","18","26","27","28","29","37","38","39","40","41");
    List<String> LIGHT_GREEN = Arrays.asList("7","8","9","10","11","19","20","21","22","30","31","33","34","42","45");
}
