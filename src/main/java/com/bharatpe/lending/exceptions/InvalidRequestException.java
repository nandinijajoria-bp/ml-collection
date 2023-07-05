package com.bharatpe.lending.exceptions;

public class InvalidRequestException extends RuntimeException{

    private String message;

    public InvalidRequestException(String message) {
        super(message);
    }
}
