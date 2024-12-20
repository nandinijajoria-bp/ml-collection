package com.bharatpe.lending.loanV3.revamp.constants;

public class LoanDetailsConstant {
    public static boolean API_RESPONSE_FAIL_STATUS = false;

    public static String VERSION_V1 = "v1";
    public static String VERSION_V2 = "v2";
    public static String FUNNEL_VERSION_TAG = "v3";


    // cache Keys
    public static String BP_CLUB_MEMBERSHIP_KEY_PREFIX="BP_CLUB_MEMBERSHIP_";

    public static String CLUB_V2_MEMBERSHIP_KEY_PREFIX="CLUB_V2_MEMBERSHIP_";

    public static String LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX="LENDING_DASHBOARD_DETAILS_V3_";


    public static String LENDING_VERSION_KEY_PREFIX="LENDING_VERSION_V2_";
    public static String LENDING_LOAN_DETAILS_KEY_PREFIX="LENDING_LOAN_DETAILS_";


    // frontend application stages

    public static String APPLICATION_SUBMITTED="Application Submitted";
    public static String REVIEW="Review";
    public static String SEND_TO_NBFC="Send to NBFC";

    public static String DISBURSAL="Disbursal";
    public static String STATUS_PENDING="Pending";
    public static String STATUS_SUCCESS="Success";

    public static String STATUS_FAILED="Failed";

    public static String PIPE_DELIMITER="|";

    public static String PREAPPROVED_FRESH_LOAN_IDENTIFIER = "PRE_APPROVED_PILOT_1_FRESH";

    public static String PREAPPROVED_REPEAT_LOAN_IDENTIFIER = "PRE_APPROVED_PILOT_1_REPEAT";

    public static String F_TPV_PILOT_IDENTIFIER = "F_TPV";

    public static String PREAPPROVED_TOPUP_LOAN_IDENTIFIER = "PRE_APPROVED_PILOT_1_TOPUP";

    public static String DIWALI_BANNER_IDENTIFIER = "REDUCED_INTEREST_RATE";

    public static String MULTIPLE_REPEAT_TOPUP_LOAN_IDENTIFIER = "multiple_repeat_top_up";


    public static String NACH_STATUS_APPROVED = "APPROVED";

    public static String PENDING_APPLICATION_TAT_TEXT = "Please complete your application to get a loan from Bharatpe.";

    public static String TRANSFER_DAYS_TEXT_PREFIX = "Once approved, money transfer in ";

    public static String TAT_BREACH_TEXT = "Money transfer in the next few days.";

    public static String TRANSFER_DAYS_TEXT_SUFFIX = " days.";

    public static String DUMMY_MERCHANT_TRANSFER_DAYS_TEXT = "Once approved, money transfer in 4-5 days.";
    public static String INITIAL_PHASE_LAST_DAY = "Money will get transferred today.";
    public static String INITIAL_PHASE = "Money transfer within %d days.";
    public static String FIRST_TAT_BREACH_PHASE = "It's taking time. Money transfer will be in next %d days";
    public static String FIRST_TAT_BREACH_PHASE_LAST_DAY = "It's taking time. Money transfer will happen today.";
    public static String SECOND_TAT_BREACH_PHASE = "Money will get transferred in the next few days.";
    public static String INVALID_CASE = "Money will get transferred in the next few days.";

    public static String UNDERWRITING_MASKED_MOBILE_EXCEPTION="CALL_MASKED_MOBILE_FLOW";

}

