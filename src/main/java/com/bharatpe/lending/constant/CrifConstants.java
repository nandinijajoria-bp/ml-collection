package com.bharatpe.lending.constant;

import java.util.Arrays;
import java.util.List;

public interface CrifConstants {

    enum COLOR {
        RED, AMBER, LIGHT_GREEN, DARK_GREEN;
    }

    String INQUIRY_DATE = "INQUIRY-DATE";
    String INQUIRY_HISTORY = "INQUIRY-HISTORY";
    String LOAN_DETAILS = "LOAN-DETAILS";
    String HISTORY = "HISTORY";
    String REQUEST = "REQUEST";
    String RESPONSES = "RESPONSES";
    String RESPONSE = "RESPONSE";
    String SCORES = "SCORES";
    String DISBURSED_DT = "DISBURSED-DT";
    String DISBURSED_AMT = "DISBURSED-AMT";
    String CREDIT_LIMIT = "CREDIT-LIMIT";
    String CLOSED_DATE = "CLOSED-DATE";
    String DATE_REPORTED = "DATE-REPORTED";
    String EMPLOYMENT_DETAILS = "EMPLOYMENT-DETAILS";
    String EMPLOYMENT_DETAIL = "EMPLOYMENT-DETAIL";
    String SCORE = "SCORE";

    Boolean LOCKDOWN = false;
    String DATE_FORMAT = "dd-MM-yyyy";
    String ACCT_TYPE = "ACCT-TYPE";
    String ACCT_STATUS = "ACCOUNT-STATUS";
    String REPORT_HEADER = "B2C-REPORT";
    String PERSONAL_VARIATIONS = "PERSONAL-INFO-VARIATION";
    String PAN_VARIATIONS = "PAN-VARIATIONS";
    String VARIATION = "VARIATION";
    String PHONE_VARIATIONS = "PHONE-NUMBER-VARIATIONS";
    String CLIENT_NAME = "BHARATPE_EM";
    String VOUCHER_CODE = "BharatPe214K2";
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
    String DEROG_ACCOUNT_STATUS = "Loan default / partial settlement";
    String DEROG_DPD_LAST_3_MONTHS = "Late repayment (5+ days) in last 3 months";
    String DEROG_DPD_LAST_6_MONTHS = "Late repayment (30+ days) in last 6 months";
    String DEROG_DPD_LAST_24_MONTHS = "Late repayment (90+ days) in last 24 months";
    String DEROG_DPD_OLDER_THAN_24_MONTHS = "Late repayment (60+ days) in older than 2 year loans";
    String DEROG_DPD_LAST_12_MONTHS = "Late repayment (60+ days) in last 12 months";
    String DEROG_UNSECURED_LOAN_ENQUIRY = "High unsecured loan enquiries";
    String DEROG_UNSECURED_LOANS = "More than 3 Unsecured Loans";
    String DEROG_MORE_THAN_6_LOAN_ENQUIRY = "High number of loan enquiries in last 6 months";
    List<String> UNSECURED_ACCT_TYPES = Arrays.asList("education loan", "leasing", "personal loan", "consumer loan", "loan to professional", "credit card", "charge card", "fleet card", "overdraft", "od on savings account", "business loan general", "business loan priority sector small business", "business loan priority sector agriculture", "business loan priority sector others", "business non-funded credit facility general", "business non-funded credit facility-priority sector- small business", "business non-funded credit facility-priority sector-agriculture", "business non-funded credit facility-priority sector-others", "telco wireless", "telco broadband", "telco landline", "microfinance business loan", "microfinance personal loan", "microfinance others", "loan on credit card", "prime minister jaan dhan yojana - overdraft", "mudra loans – shishu / kishor / tarun", "business loan unsecured", "jlg individual", "jlg group", "individual", "shg group", "shg individual", "shg group – govt", "shd intra - group", "other");
    List<String> UNSECURED_PRODUCTS = Arrays.asList("personal loan", "credit card", "kisan credit card", "loan on credit card", "prime minister jaan dhan yojana - overdraft", "mudra loans – shishu / kishor / tarun", "microfinance others", "business loan general", "business loan priority sector small business", "business loan priority sector agriculture", "business loan priority sector others", "business non-funded credit facility general", "business non-funded credit facility-priority sector- small business", "business non-funded credit facility-priority sector-agriculture", "business non-funded credit facility-priority sector-others", "staff loan", "business loan unsecured");

}
