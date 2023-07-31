package com.bharatpe.lending.loanV3.revamp.exception;


import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class LoanDetailsExceptionController {

    @ExceptionHandler(value=LoanDetailsException.class)
    public ResponseEntity<ApiResponse<?>> handleException(LoanDetailsException loanDetailsException){
        return ResponseEntity.ok().body(new ApiResponse<>(LoanDetailsConstant.API_RESPONSE_FAIL_STATUS,loanDetailsException.getErrorCode(),loanDetailsException.getErrorMessage()));
    }

}
