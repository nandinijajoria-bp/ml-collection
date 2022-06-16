package com.bharatpe.lending.handlers;

public class MerchantScoreException extends RuntimeException {
    public MerchantScoreException(String errorMessage) {
        super("merchant score not found for : " + errorMessage);
    }
}
