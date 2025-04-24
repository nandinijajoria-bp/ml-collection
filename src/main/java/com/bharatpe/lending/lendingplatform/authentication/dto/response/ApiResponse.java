package com.bharatpe.lending.lendingplatform.authentication.dto.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private Error error;
    private T data;

    public ApiResponse(boolean success, T data){
        this.success = success;
        this.data = data;
        this.error = null;
    }

    public ApiResponse(boolean success, Error error, T data) {
        this.success = success;
        this.data = data;
        this.error = error;
    }
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data);
    }
    public static <T> ApiResponse<T> error(String errorStatusCode, String errorMessage, T data) {
        return new ApiResponse<>(false, new Error(errorStatusCode, errorMessage), data);
    }
}
