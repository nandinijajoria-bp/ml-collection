package com.bharatpe.lending.constant;


public class RejectionReasons {
    public static final String ENACH_FAILURE = "ENACH failure";
    public static final String DEFAULT_LENDER_REJECTED = "Default lender rejected the application";
    public static final String MAX_LENDER_SELECTION_ATTEMPTS = "Max lender selection attempts reached";
    public static final String BRE_FAILED = "BRE FAILED";
    public static final String CREDIT_ASSESSMENT_FAILED_TPV = "credit assessment failed : F_TPV";
    public static final String EXPERIAN_DEROG_FAILED = "Experian derog failed";
    public static final String PENNY_DROP_FAILED = "Penny drop failed";
    public static final String LENDER_PENNY_DROP_FAILED = "Penny drop failed at lender";
    public static final String NULLABLE_LENDER = "Rejected due to nullable lender";
    public static final String APPLICATION_ALREADY_PENDING_DISBURSAL = "Another application already pending disbursal for merchantId";
    public static final String MERCHANT_HAS_ACTIVE_LOAN = "Merchant has a active loan For merchantId";
    public static final String KYC_REJECTED = "KYC REJECTED";
    public static final String LENDER_NONE = "No Default Lender Assigned";
    public static final String REJECT_DOWNGRADE_01 = "Downgrade rejected";
    public static final String REJECT_DOWNGRADE_02 = "Downgrade rejected";
    public static final String BELOW_FORECLOURE_THRESHOLD_BRE_REJECTED = "BRE rejected since foreclosure amount is below threshold for TOPUP loan";
    public static final String LENDER_FAILED_TOPUP_DISBURSAL = "Lender Failed Disbursal for TOPUP";
    public static final String LENDER_FAILED_DISBURSAL = "Loan disbursal failed at lender end";
    public static final String LENDER_FAILED_BRE = "Lender Failed BRE";
    public static final String BANK_ACCOUNT_MISMATCH = "Mismatch in bank account details";
    public static final String MERCHANT_CATEGORY_NOT_FOUND = "Merchant category not found";
    public static final String MAX_BRE_RETRY = "Max retries reached for BRE invocation. Stopping further attempts.";
    public static final String LEAD_NOT_PRESENT = "LALD/LeadId is not present for applicationId";
    public static final String EMPTY_COUNTER_OFFER = "counterOffer payload is empty for applicationId";
    public static final String UPDATE_LEAD_FAILED = "Update lead request failed";
    public static final String LENDER_FAILED_DOC_UPLOAD = "Doc upload failed at lender end";
    public static final String LENDER_FAILED_GET_LEAD =  "Get lead failed at lender end";
    public static final String LENDER_REJECTED_UDYAM =  "Udyam rejected at lender in status check";
    public static final String LENDER_FAILED_TOP_DRAWDOWN = "Drawdown failed at lender for topup - Marking prev loan active";
    public static final String LENDER_FAILED_DRAWDOWN = "Lender failed drawdown";
}
