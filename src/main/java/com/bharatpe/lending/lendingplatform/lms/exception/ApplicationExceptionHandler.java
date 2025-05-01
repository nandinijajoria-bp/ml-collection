package com.bharatpe.lending.lendingplatform.lms.exception;

import com.bharatpe.lending.lendingplatform.lms.dto.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;

import static com.bharatpe.lending.lendingplatform.lms.constant.CustomStatus.FORBIDDEN;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApplicationExceptionHandler {

//    @ExceptionHandler(BadCredentialsException.class)
//    public ResponseEntity<ApiResponse<Object>> handleBadCredentialsException(BadCredentialsException ex) {
//        return buildErrorResponse(HttpStatus.UNAUTHORIZED, UNAUTHORIZED.getDescription(), ex.getMessage());
//    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, FORBIDDEN.getDescription(), ex.getMessage());
    }

    private ResponseEntity<ApiResponse<Object>> buildErrorResponse(
            HttpStatus status, String customStatus, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(String.valueOf(status.value()), customStatus + ": " + message, null));
    }
}
