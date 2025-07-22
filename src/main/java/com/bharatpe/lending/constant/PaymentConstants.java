package com.bharatpe.lending.constant;

import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;

import java.util.HashMap;

public class PaymentConstants {

    public static final String PG_PAGE_HEADER_TEXT="Select Payment Mode";
    public static final String PG_PAGE_NARRATION="Payment for Order No {}";
    public static final String EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX = "_adjust_";
    public static final String UPI_AUTOPAY_EXCESS_CREDIT_MODE = "EXCESS_AUTOPAYUPI_CREDIT";
    public static final String INPROGRESS = "INPROGRESS";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    public static HashMap<AutoPayStatusEnum, String> UPI_AUTOPAY_MANDATE_STATUS_MAP = new HashMap<AutoPayStatusEnum, String>() {{
        put(AutoPayStatusEnum.PENDING, INPROGRESS);
        put(AutoPayStatusEnum.ACTIVE, SUCCESS);
        put(AutoPayStatusEnum.FAILED, FAILED);
        put(AutoPayStatusEnum.FAILURE, FAILED);
    }};

    public static HashMap<String, String> UPI_AUTOPAY_ERROR_CODE_TO_DISPLAY_MESSAGE_MAP = new HashMap<String, String>() {{
        put("MR_0001", "AutoPay failed since you didn't complete the setup. Please retry");
        put("MR_0002", "AutoPay failed because of insufficient balance in your account. Please ensure you have INR 1 for the setup");
        put("MR_0003", "AutoPay failed because you selected a bank account which is not linked with BharatPe. Please retry with correct account");
        put("MR_0004", "AutoPay failed due to NPCI error");
        put("MR_0005", "AutoPay declined by your bank");
        put("MR_0006", "AutoPay failed due to technical issue at your bank's end. Please retry");
        put("MR_0007", "AutoPay failed due to authorization issue. Please retry");
        put("MR_0008", "AutoPay failed as your UPI collect request has expired. Please retry");
        put("MR_0009", "AutoPay failed");
        put("TAT_EXCEEDED", "AutoPay failed");
        put("API_ERROR", "AutoPay failed");
    }};
    public static HashMap<String, Boolean> UPI_AUTOPAY_ERROR_CODE_TO_RETRY_ELIGIBLE_MAP = new HashMap<String, Boolean>() {{
        put("MR_0001", true);
        put("MR_0002", true);
        put("MR_0003", true);
        put("MR_0004", true);
        put("MR_0005", true);
        put("MR_0006", true);
        put("MR_0007", true);
        put("MR_0008", true);
        put("MR_0009", true);
        put("TAT_EXCEEDED", true);
        put("API_ERROR", true);
    }};
}
