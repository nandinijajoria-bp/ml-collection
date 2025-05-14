package com.bharatpe.lending.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CancelNachApiException extends Exception{
    private final HttpStatus status;
    public CancelNachApiException(String message){
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }
    public CancelNachApiException(HttpStatus status, String message){
        super(message);
        this.status = status;
    }
}
