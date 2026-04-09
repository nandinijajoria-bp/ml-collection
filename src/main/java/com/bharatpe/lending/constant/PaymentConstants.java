package com.bharatpe.lending.constant;

import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PaymentConstants {

    public static final String PG_PAGE_HEADER_TEXT="Select Payment Mode";
    public static final String PG_PAGE_NARRATION="Payment for Order No {}";
    public static final String EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX = "_adjust_";
    public static final String UPI_AUTOPAY_EXCESS_CREDIT_MODE = "EXCESS_AUTOPAYUPI_CREDIT";
    public static final String TAT_EXCEEDED_ERROR_CODE = "TAT_EXCEEDED";
    public static final String INPROGRESS = "INPROGRESS";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String INIT = "INIT";

    public static final Map<String, AutoPayStatusEnum> NACH_STATUS_AUTO_PAY_STATUS_ENUM_MAP = new HashMap<>();
    public static final List<AutoPayStatusEnum> AUTOPAY_TERMINAL_STATUS = new ArrayList<>();
    static {
        NACH_STATUS_AUTO_PAY_STATUS_ENUM_MAP.put(NachStatus.INPROCESS.name(), AutoPayStatusEnum.PENDING);
        NACH_STATUS_AUTO_PAY_STATUS_ENUM_MAP.put(NachStatus.FAILED.name(), AutoPayStatusEnum.FAILED);
        NACH_STATUS_AUTO_PAY_STATUS_ENUM_MAP.put(NachStatus.APPROVED.name(), AutoPayStatusEnum.ACTIVE);
        NACH_STATUS_AUTO_PAY_STATUS_ENUM_MAP.put(NachStatus.CANCELLED.name(), AutoPayStatusEnum.CANCELLED);
        NACH_STATUS_AUTO_PAY_STATUS_ENUM_MAP.put(NachStatus.REJECTED.name(), AutoPayStatusEnum.FAILED);
        NACH_STATUS_AUTO_PAY_STATUS_ENUM_MAP.put(NachStatus.DEACTIVATED.name(), AutoPayStatusEnum.INACTIVE);
        NACH_STATUS_AUTO_PAY_STATUS_ENUM_MAP.put(NachStatus.REVOKED.name(), AutoPayStatusEnum.REVOKED);

        AUTOPAY_TERMINAL_STATUS.add(AutoPayStatusEnum.CANCELLED);
        AUTOPAY_TERMINAL_STATUS.add(AutoPayStatusEnum.FAILED);
        AUTOPAY_TERMINAL_STATUS.add(AutoPayStatusEnum.FAILURE);
        AUTOPAY_TERMINAL_STATUS.add(AutoPayStatusEnum.INACTIVE);
        AUTOPAY_TERMINAL_STATUS.add(AutoPayStatusEnum.REVOKED);
    }

    public static HashMap<AutoPayStatusEnum, String> UPI_AUTOPAY_MANDATE_STATUS_MAP = new HashMap<AutoPayStatusEnum, String>() {{
        put(AutoPayStatusEnum.INIT, INIT);
        put(AutoPayStatusEnum.PENDING, INPROGRESS);
        put(AutoPayStatusEnum.SUCCESS, SUCCESS);
        put(AutoPayStatusEnum.ACTIVE, SUCCESS);
        put(AutoPayStatusEnum.FAILED, FAILED);
        put(AutoPayStatusEnum.FAILURE, FAILED);
        put(AutoPayStatusEnum.CANCELLED, FAILED);
        put(AutoPayStatusEnum.INACTIVE, FAILED);
        put(AutoPayStatusEnum.REVOKED, FAILED);
    }};

    public static final Set<AutoPayStatusEnum> UPI_AUTOPAY_TERMINAL_STATES = new HashSet<AutoPayStatusEnum>() {{
        add(AutoPayStatusEnum.ACTIVE);
        add(AutoPayStatusEnum.FAILED);
        add(AutoPayStatusEnum.FAILURE);
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

    public static final Integer TL_DEFAULT_PAYMENT_TYPE_ID = 1021;
    public static final Map<String, Integer> tlPaymentTypeIdMap = new HashMap<String, Integer>(){{
        put("SETTLEMENT", 1009);
        put("FP", 1009);
        put("BHARATPE_NACH", 1018);
        put("NACH", 1018);
        put("NB", 1013);
        put("DC", 1017);
        put("UPI", 1014);
        put("UPI_AUTOPAY", 1019);
        put("DIRECT_TRANSFER", 1020);
    }};
}
