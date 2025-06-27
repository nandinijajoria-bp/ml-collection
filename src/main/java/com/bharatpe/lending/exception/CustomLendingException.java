package com.bharatpe.lending.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CustomLendingException extends RuntimeException{
    private final HttpStatus status;

    public CustomLendingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
