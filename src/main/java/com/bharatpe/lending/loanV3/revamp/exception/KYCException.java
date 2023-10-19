package com.bharatpe.lending.loanV3.revamp.exception;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KYCException extends RuntimeException{
    private String errorCode;
    private String errorMessage;

}
