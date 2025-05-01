package com.bharatpe.lending.lendingplatform.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private Error error;
    private T data;

    public ApiResponse() {
        // Default constructor
    }

    public ApiResponse(boolean success, T data) {
        this.success = success;
        this.data = data;
        this.error = null;
    }

    public ApiResponse(boolean success, Error error, T data) {
        this.success = success;
        this.error = error;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data);
    }

    public static <T> ApiResponse<T> error(String errorStatusCode, String errorMessage, T data) {
        return new ApiResponse<>(false, new Error(errorStatusCode, errorMessage), data);
    }

    public static <T> ApiResponse<T> buildFailureApiResponse(String errorCode, String errorMessage) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setError(new Error(errorCode, errorMessage));
        return response;
    }
}
