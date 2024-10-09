package com.bharatpe.lending.loanV3.dto.response.creditsasion;

public enum CreditSasionCallbackResponseStatuses {
    KYC("09"),
    KYC_REJECT("-1"),
    PENNYDROP_SUCCESS("35"),
    PENNYDROP_FAILED("-35"),
    BRE_APPROVED("15"),
    BRE_REJECT("-20"),
    DRAWDOWN("1000"),
    DRAWDOWN_REJECT("-1000");
    ;

    private final String statusCode;

    CreditSasionCallbackResponseStatuses(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusCode() {
        return statusCode;
    }
}
