package com.bharatpe.lending.constant;

import com.bharatpe.lending.enums.RejectionReason;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class SupportApiConstants {
    public static HashMap<String, RejectionReason> rejectionTypeMap = new HashMap<String, RejectionReason>() {{
        put("High number of loan enquiries in last 6 months", RejectionReason.DEROG);
        put("HIGH_LOAN_ENQUIRIES", RejectionReason.DEROG);
        put("More than 3 Unsecured Loans", RejectionReason.DEROG);
        put("Late repayment (90+ days) in last 24 months", RejectionReason.DEROG);
        put("Late repayment (30+ days) in last 6 months", RejectionReason.DEROG);
        put("DELIVERY_DRIVER_APPS", RejectionReason.DEROG);
        put("Loan default / partial settlement", RejectionReason.DEROG);
        put("Late repayment (60+ days) in last 12 months", RejectionReason.DEROG);
        put("MULTIPLE_LENDING_APPS", RejectionReason.DEROG);
        put("LOW_BBS", RejectionReason.LOW_TRANSACTION);
        put("NTC", RejectionReason.LOW_TRANSACTION);
        put("GLOBAL_LIMIT_BLOCKED", RejectionReason.LOW_TRANSACTION);
        put("HIGH_RISK_SEGMENT", RejectionReason.LOW_TRANSACTION);
        put("LOW_ATS", RejectionReason.LOW_TRANSACTION);
        put("LOW_NFI", RejectionReason.LOW_TRANSACTION);
        put("FRAUD", RejectionReason.LOW_TRANSACTION);
        put("LOW_BP_SCORE", RejectionReason.LOW_TRANSACTION);
        put("OVERDUE", RejectionReason.PERMANENT);
        put("BLOCKED_PANCARD", RejectionReason.PERMANENT);
        put("MAX_DPD_20", RejectionReason.PERMANENT);
        put("ENACH", RejectionReason.CHANGE_BANK_ACCOUNT);
        put("BLOCKED_BANK", RejectionReason.CHANGE_BANK_ACCOUNT);
        put("OGL", RejectionReason.OGL);
    }};

    public static HashMap<String, Integer> experianRejectionReapplyTimelineMap = new HashMap<String, Integer>() {{
        put("High number of loan enquiries in last 6 months", 90);
        put("HIGH_LOAN_ENQUIRIES", 90);
        put("More than 3 Unsecured Loans", 90);
        put("Late repayment (90+ days) in last 24 months", 90);
        put("Late repayment (30+ days) in last 6 months", 90);
        put("DELIVERY_DRIVER_APPS", 90);
        put("Loan default / partial settlement", 90);
        put("Late repayment (60+ days) in last 12 months", 90);
        put("MULTIPLE_LENDING_APPS", 90);
        put("LOW_BBS", 90);
        put("NTC", 60);
        put("HIGH_RISK_SEGMENT", 90);
        put("LOW_ATS", 30);
        put("LOW_NFI", 90);
        put("FRAUD", 7);
        put("LOW_BP_SCORE", 7);
        put("OVERDUE", 180);
    }};

    public static HashMap<String, Integer> cibilRejectionReapplyTimelineMap = new HashMap<String, Integer>() {{
        put("SHOP_PINCODE_DISTANCE", 90);
        put("SHOP_INFERRED_DISTANCE", 90);
        put("PIC_GEO_DISTANCE", 90);
        put("MULTIPLE_PSP_APPS", 90);
        put("INSUFFICIENT CONTACTS", 30);
        put("JUNK_ADDRESS", 30);
        put("JUNK_NAME", 30);
        put("AGE", 30);
        put("Age Reject", 30);
        put("CIBIL_RED", 0);
    }};

    public static HashMap<String, String> getApplicationStageFromLmsStage = new HashMap<String, String>() {{
        put("PENDING_KYC", "PENDING_KYC");
        put("PENDING_KYC_ASSIGNMENT", "PENDING_KYC");
        put("PENDING_KYC_CALL_TO_MERCHANT", "PENDING_KYC");
        put("PENDING_KYC_ESCALATE_ASSIGNMENT", "PENDING_KYC");
        put("PENDING_QC_ASSIGNMENT", "PENDING_QC");
        put("PENDING_QC", "PENDING_QC");
        put("PENDING_QC_CALL_TO_MERCHANT", "PENDING_QC");
        put("QC_REJECTED", "QC_REJECTED");
        put("PENDING_DISBURSAL", "PENDING_DISBURSAL");
        put("DISBURSAL_PROCESSING", "DISBURSAL_PROCESSING");
        put("SEND_TO_NBFC", "SEND_TO_NBFC");
        put("DISBURSED", "DISBURSED");
        put("DISBURSAL_REJECTED", "DISBURSAL_REJECTED");
        put("SYSTEM_REJECTED", "SYSTEM_REJECTED");
    }};

    public static Integer kycRejectionDefaultReapplyTimeline = 7;
    public static Integer qcRejectionDefaultReapplyTimeline = 7;
    public static Integer experianRejectionDefaultReapplyTimeline = 7;
    public static Integer cibilRejectionDefaultReapplyTimeline = 30;

    public static List<String> notEligibleToApplyReasons = Arrays.asList("DUPLICATE PANCARD","FOS_APP");
}
