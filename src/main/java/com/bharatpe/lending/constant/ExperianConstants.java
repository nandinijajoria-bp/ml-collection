package com.bharatpe.lending.constant;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface ExperianConstants {

    enum COLOR {
        RED, AMBER, LIGHT_GREEN, DARK_GREEN;
    }

    Boolean LOCKDOWN = false;

    String SHORT_API_URL = "https://consumer.experian.in:8443/ECV-P2/content/enhancedMatch.action";
    String LONG_API_URL = "https://consumer.experian.in:8443/ECV-P2/content/singleAction.action";
    String MASKED_MOBILE_URL = "https://consumer.experian.in:8443/ECV-P2/content/generateMaskedDeliveryData.action";
    String AUTHENTICATE_MOBILE_URL = "https://consumer.experian.in:8443/ECV-P2/content/authenticateDeliveryData.action";
    String REFRESH_API_URL = "https://consumer.experian.in:8443/ECV-P2/content/onDemandRefresh.action";
    String CLIENT_NAME = "BHARATPE_EM";
//    String VOUCHER_CODE = "BharatPebxIYY";
    String VOUCHER_CODE = "BharatPeOVaSv";
    String CREDIT_LINE_CATEGORY = "CREDIT_LINE_CATEGORY";
    String INVALID_PANCARD = "INVALID_PANCARD";
    String LOW_BP_SCORE = "LOW_BP_SCORE";
    String ENACH="ENACH";
    String BUSINESS_CATEGORY="BUSINESS_CATEGORY";
    String FRAUD = "FRAUD";
    String OVERDUE = "OVERDUE";
    String LOW_TPV = "LOW_TPV";
    String VINTAGE = "VINTAGE";
    String CATEGORY_RED = "CATEGORY_RED";
    String OGL = "OGL";
    String NTC = "NTC";
    String TIMEOUT = "TIMEOUT";
    String COVID = "COVID";
    String DEROG_ACCOUNT_STATUS = "Loan default / partial settlement";
    String DEROG_DPD_LAST_3_MONTHS = "Late repayment (5+ days) in last 3 months";
    String DEROG_DPD_LAST_6_MONTHS = "Late repayment (30+ days) in last 6 months";
    String DEROG_DPD_LAST_24_MONTHS = "Late repayment (90+ days) in last 24 months";
    String DEROG_DPD_OLDER_THAN_24_MONTHS = "Late repayment (60+ days) in older than 2 year loans";
    String DEROG_DPD_LAST_12_MONTHS = "Late repayment (60+ days) in last 12 months";
    String DEROG_UNSECURED_LOAN_ENQUIRY = "High unsecured loan enquiries";
    String DEROG_UNSECURED_LOANS = "More than 3 Unsecured Loans";
    String DEROG_MORE_THAN_6_LOAN_ENQUIRY = "High number of loan enquiries in last 6 months";
    String LOW_BBS = "LOW_BBS";
    String LOW_NFI = "LOW_NFI";
    String CAPS_DETAILS = "CAPS_Application_Details";
    String LOW_BBS_VINTAGE = "LOW_BBS_VINTAGE";
    String BLOCKED_PANCARD = "BLOCKED_PANCARD";
    String ACCT_TYPE = "Account_Type";
    String DATE_REPORTED = "Date_Reported";
    String DATE_ADDITION = "DateOfAddition";
    String DATE_CLOSED = "Date_Closed";
    String DPD = "Days_Past_Due";
    String PRODUCT = "Product";
    String DOR = "Date_of_Request";
    String OPEN_DATE = "Open_Date";
    String PROFILE_RESPONSE = "INProfileResponse";
    String ACCT_HISTORY = "CAIS_Account_History";
    String ACCT_DETAILS = "CAIS_Account_DETAILS";
    String ACCT_STATUS = "Account_Status";
    String ACCT = "CAIS_Account";
    String CAPS_SUMMARY = "TotalCAPS_Summary";
    String ACCT_HOLDER_TYPE_CODE = "AccountHoldertypeCode";
    String DATE_FORMAT = "yyyyMMdd";
    String YELLOW = "YELLOW";
    String LOW_ATS = "LOW_ATS";
    String LOW_BUREAU_SCORE = "LOW_BUREAU_SCORE";
    String PAYMENTS_BANK = "PAYMENTS_BANK";
    String HIGH_LOAN_ENQUIRIES = "HIGH_LOAN_ENQUIRIES";
    String NON_CPV_CITY = "NON_CPV_CITY";
    String MULTIPLE_PSP_APPS = "MULTIPLE_PSP_APPS";
    String FOS_APP = "FOS_APP";
    String D2R = "D2R";

    List<String> RED = Arrays.asList("1","2","13","25");
    List<String> AMBER = Arrays.asList("3","4","5","6","14","15","16","17","18","26","27","28","29","37","38","39","40","41");
    List<String> LIGHT_GREEN = Arrays.asList("7","8","9","10","11","19","20","21","22","30","31","33","34","42","45");

    Map<String,String> COLOR_TO_CATEGORY=new HashMap<String,String>() {{
        put("BBS11", "AMBER");
        put("BBS12", "AMBER");
        put("BBS21", "AMBER");
        put("BBS13", "LIGHT_GREEN");
        put("BBS22", "LIGHT_GREEN");
        put("BBS31", "LIGHT_GREEN");
        put("BBS23", "DARK_GREEN");
        put("BBS32", "DARK_GREEN");
        put("BBS33", "DARK_GREEN");
    }};
}
