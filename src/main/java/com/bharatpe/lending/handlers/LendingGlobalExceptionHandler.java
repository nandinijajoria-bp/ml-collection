package com.bharatpe.lending.handlers;

import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.LoanDetailsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

@Slf4j
@ControllerAdvice
public class LendingGlobalExceptionHandler {

    @ExceptionHandler(value = BureauCallMaskedApiException.class)
    public ResponseEntity<ApiResponse<?>> exception(BureauCallMaskedApiException exception) {
        return new ResponseEntity<>(new ApiResponse<>(true, "Call Masked Mobile Api", "Call Masked Mobile Api"), HttpStatus.OK);
    }

    @ExceptionHandler(value = Exception.class)
    public void logException(Exception exception, HttpServletRequest request) throws Exception {
        log.error("LendingGlobalExceptionHandler: Unhandled exception in API: {} {}, Message: {}, Stack Trace: {}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage(),
                Arrays.toString(exception.getStackTrace()));
        throw exception;
    }
}
