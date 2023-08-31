package com.bharatpe.lending.loanV3.revamp.enums;



/*
This enum contains all the exception related to loan details/application of a merchant with error code and error message

 */


public enum LoanDetailExceptionEnum {

    DUMMY_EXCEPTION("000","Dummy Exception"),

    ENACH_HISTORY_EMPTY("LEN001","Enach history not present"),

    NO_SCOPE_PROVIDED("LEN0002","Scope not provided"),

    APPLICATION_NOT_FOUND("LEN0003", "Application not found"),

    SOMETHING_WENT_WRONG("LEN0004", "Something went wrong"),

    NON_NACHABLE_BANK("LEN0005", "Bank not nachable"),

    APPLICATION_ID_MISSING("LEN0006", "Application id missing in request"),

    PANCARD_DOES_NOT_EXIST("LEN0007","Pan card does not exist"),

    INITIATE_KYC_FAILED("LEN0008", "Unable to initiate KYC");







    private String errorCode;
    private String errorMessage;

    LoanDetailExceptionEnum(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
