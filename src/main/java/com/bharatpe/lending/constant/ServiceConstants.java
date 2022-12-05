package com.bharatpe.lending.constant;


import com.bharatpe.lending.common.enums.LendingEnum;

public class ServiceConstants {

    public static class SELF {
        public static final String HEADER_HASH_NAME = "hash";
        public static final String HEADER_MID_NAME = "mid";
    }


    public static class PAYOUT {
        public static final String DEFAULT_PAYOUT_REQUEST_TOPIC = "payout2.lending.payout.request";
        public static final String CLIENT_NAME = "LENDING";
        public static final String NBFC_PAYOUT_STATUS_CALLBACK_TOPIC = "lending.payout2.payout.status.callback";
        public static final String EDI_FP_COLLECTION_PAYOUT_TYPE = LendingEnum.NBFCPAYOUTSTYPE.EDI_FP_COLLECTION_PAYOUT.name();
        public static final String CHECK_STATUS_URL = "/payout/status?orderId=%s&payoutType=%s";

        public static class HEADER {
            public static final String HASH_NAME = "hash";
            public static final String CLIENT_NAME = "clientName";
        }

        public enum BeneficiaryType {
            ACCOUNT_DETAILS
        }
    }
}
