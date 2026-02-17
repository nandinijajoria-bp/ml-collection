package com.bharatpe.lending.lendingplatform.lms.constant;

public class Constants {
    public static final String YES = "YES";
    public static final String NO = "NO";
    public static final String DISBURSED_LOAN = "DISBURSED";
    public static final String ACTIVE = "ACTIVE";
    public static final String DISBURSAL_STATUS_UNKNOWN = "UNKNOWN";
    public static final String PROCESSING_FEE = "PROCESSING_FEE";
    public static final String ONE_LMS = "1LMS";
    public static final String NBFC_FUNDS = "NBFC_FUNDS";
    public static final Long MONTH_IN_MILLIES = 30*24*60*60*1000L;

    private Constants() {
    }

    public static class Consumer {
        public static final String LMS_ERRORS = "LMS_ERRORS";
        public static final String CUSTOM_LMS_ERRORS = "CUSTOM_LMS_ERRORS";
        public static final String REST_ADVICE_ERRORS = "REST_ADVICE_ERRORS";
        public static final String FIELD_VALIDATION_ERRORS = "FIELD_VALIDATION_ERRORS";
        public static final String RETRY_EXHAUSTED = "RETRY_EXHAUSTED";


        private Consumer() {
        }
    }

    public static class DocumentNamesConstants {
        public static final String DIGILOCKER_AADHAAR_XML = "DIGILOCKER_AADHAAR_XML";
        public static final String CUSTOMER_SELFIE = "CUSTOMER_SELFIE";
        public static final String KEY_FACT_STATEMENT = "KEY_FACT_STATEMENT";
        public static final String SHOP_FRONT_PHOTO = "SHOP_FRONT_PHOTO";
        public static final String SHOP_STOCK_PHOTO = "SHOP_STOCK_PHOTO";
        public static final String LOAN_AGREEMENT = "LOAN_AGREEMENT";

        private DocumentNamesConstants(){

        }
    }

    public static class ApiEndPointConstants {
        public static final String CREATE_LOAN = "/lms/v1/loan";
        public static final String GET_FORECLOSURE_AMOUNT = "/lms/v1/loan/foreclosure_details";
        public static final String FORECLOSE_LOAN = "/lms/v1/loan/foreclose";
        public static final String FETCH_LENDER_FORECLOSURE = "/lender/v1/loan/foreclose_details";
        public static final String POST_PAYMENT = "/lms/v1/loan/payment";
        public static final String GET_LOAN_SUMMARY = "/lms/v1/loan/summary";
        public static final String GET_ALL_TRANSACTIONS_DETAIL = "/lms/v1/loan/transaction_details";
        public static final String GET_RPS = "/lms/v1/loan/rps";


        private ApiEndPointConstants() {
        }
    }

    public static class PaymentTransferConstant {
        public static final String PAYMENT_SUCCESS = "CREDITED";
        public static final String PAYMENT_INITIATED = "DEPOSIT";
        public static final String PAYMENT_CANCELLED = "CANCELLED";
        public static final String PAYMENT_BOUNCE = "BOUNCE";

        private PaymentTransferConstant() {
        }
    }

    public static class ErrorStatusCode {

        public static final String BAD_GATEWAY = "502";
        public static final String INTERNAL_SERVER_ERROR = "500";
        public static final String BAD_REQUEST = "400";
        public static final String SERVICE_UNAVAILABLE = "503";
    }
}
