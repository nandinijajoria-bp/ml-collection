package com.bharatpe.lending.exceptions;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ResponseNotSuccessException extends RuntimeException{

    private String message;

    public ResponseNotSuccessException(String message) {
        this.message = message;
    }
}
