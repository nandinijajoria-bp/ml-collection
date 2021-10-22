package com.bharatpe.lending.constant;

import java.util.HashMap;
import java.util.Map;

public class SupportApiConstants {
    public static final Map<String, String> rejectionTypeMap = new HashMap<>() {{
        put("High number of loan enquiries in last 6 months", "DEROG");
        put("HIGH_LOAN_ENQUIRIES", "DEROG");
        put("More than 3 Unsecured Loans", "DEROG");
        put("Late repayment (90+ days) in last 24 months", "DEROG");
        put("Late repayment (30+ days) in last 6 months", "DEROG");
        put("DELIVERY_DRIVER_APPS", "DEROG");
        put("Loan default / partial settlement", "DEROG");
        put("Late repayment (60+ days) in last 12 months", "DEROG");
        put("MULTIPLE_LENDING_APPS", "DEROG");
        put("LOW_BBS", "LOW_TRANSACTION");
        put("NTC", "LOW_TRANSACTION");
        put("GLOBAL_LIMIT_BLOCKED", "LOW_TRANSACTION");
        put("HIGH_RISK_SEGMENT", "LOW_TRANSACTION");
        put("LOW_ATS", "LOW_TRANSACTION");
        put("LOW_NFI", "LOW_TRANSACTION");
        put("FRAUD", "LOW_TRANSACTION");
        put("LOW_BP_SCORE", "LOW_TRANSACTION");
        put("OVERDUE", "PERMANENT");
        put("BLOCKED_PANCARD", "PERMANENT");
        put("MAX_DPD_20", "PERMANENT");
        put("ENACH", "CHANGE_BANK_ACCOUNT");
        put("BLOCKED_BANK", "CHANGE_BANK_ACCOUNT");
        put("OGL", "OGL");
    }};
}
