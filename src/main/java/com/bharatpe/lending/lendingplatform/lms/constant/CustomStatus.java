package com.bharatpe.lending.lendingplatform.lms.constant;

import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
public class CustomStatus {
    public static final CustomStatus PAYMENT_REQUIRED =
            new CustomStatus(HttpStatus.PAYMENT_REQUIRED, 1402, "Payment is required to proceed.");

    public static final CustomStatus TRANSACTION_FAILED =
            new CustomStatus(
                    HttpStatus.BAD_REQUEST, 1403, "Transaction failed due to insufficient funds or other issues.");

    public static final CustomStatus BAD_REQUEST =
            new CustomStatus(HttpStatus.BAD_REQUEST, 1404, "Request was invalid or cannot be processed.");

    public static final CustomStatus UNAUTHORIZED =
            new CustomStatus(HttpStatus.UNAUTHORIZED, 1405, "Authentication is required or has failed.");

    public static final CustomStatus FORBIDDEN =
            new CustomStatus(HttpStatus.FORBIDDEN, 1406, "Request is forbidden or access is denied.");

    public static final CustomStatus NOT_FOUND =
            new CustomStatus(HttpStatus.NOT_FOUND, 1407, "Resource was not found.");

    public static final CustomStatus CONFLICT =
            new CustomStatus(HttpStatus.CONFLICT, 1408, "Request conflicts with existing resources.");

    public static final CustomStatus INTERNAL_SERVER_ERROR =
            new CustomStatus(HttpStatus.INTERNAL_SERVER_ERROR, 1500, "Server encountered an unexpected error.");

    private final HttpStatus httpStatus;
    private final int code;
    private final String description;

    private CustomStatus(HttpStatus httpStatus, int code, String description) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.description = description;
    }
}
