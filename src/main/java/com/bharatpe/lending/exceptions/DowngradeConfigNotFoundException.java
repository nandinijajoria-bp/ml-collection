package com.bharatpe.lending.exceptions;

public class DowngradeConfigNotFoundException extends RuntimeException{
    private String message;
    public DowngradeConfigNotFoundException(String message) {
        super(message);
    }
}
