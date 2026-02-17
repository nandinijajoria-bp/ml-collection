package com.bharatpe.lending.exceptions;
import com.bharatpe.lending.constant.DataApiFlowConstants;
import com.bharatpe.lending.controller.LendingDataController;
import com.bharatpe.lending.dto.underwriting.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice(assignableTypes = LendingDataController.class)
public class LendingDataControllerExceptionHandler {

    @ExceptionHandler(DataApiException.InvalidInputException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidInput(DataApiException.InvalidInputException ex) {
        return ResponseEntity.badRequest().body(
                ApiResponse.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .errorCode(DataApiFlowConstants.INVALID_INPUT)
                        .data(null)
                        .build()
        );
    }

    @ExceptionHandler(DataApiException.MerchantNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleMerchantNotFound(DataApiException.MerchantNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .errorCode(DataApiFlowConstants.MERCHANT_NOT_FOUND)
                        .data(null)
                        .build()
        );
    }

    @ExceptionHandler(DataApiException.DatabaseException.class)
    public ResponseEntity<ApiResponse<Object>> handleDatabaseException(DataApiException.DatabaseException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .errorCode(DataApiFlowConstants.DATABASE_ERROR)
                        .data(null)
                        .build()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(
                ApiResponse.builder()
                        .success(false)
                        .message(message)
                        .errorCode(DataApiFlowConstants.INVALID_INPUT)
                        .data(null)
                        .build()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.builder()
                        .success(false)
                        .message("Unexpected error occurred: " + ex.getMessage())
                        .errorCode(DataApiFlowConstants.INTERNAL_ERROR)
                        .data(null)
                        .build()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleJsonParseError(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(
                ApiResponse.builder()
                        .success(false)
                        .message("Invalid request format: " + ex.getMostSpecificCause().getMessage())
                        .errorCode(DataApiFlowConstants.INVALID_INPUT)
                        .data(null)
                        .build()
        );
    }

    @ExceptionHandler(DataApiException.OperationNotAllowedException.class)
    public ResponseEntity<ApiResponse<Object>> handleOperationNotAllowed(DataApiException.OperationNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .errorCode(DataApiFlowConstants.OPERATION_NOT_ALLOWED)
                        .data(null)
                        .build()
        );
    }
}