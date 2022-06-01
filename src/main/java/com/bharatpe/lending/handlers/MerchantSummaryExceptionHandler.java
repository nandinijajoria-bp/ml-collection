package com.bharatpe.lending.handlers;

public class MerchantSummaryExceptionHandler extends RuntimeException {
    public MerchantSummaryExceptionHandler(String errorMessage) {
        super("merchant summary not found for : " + errorMessage);
    }
}
