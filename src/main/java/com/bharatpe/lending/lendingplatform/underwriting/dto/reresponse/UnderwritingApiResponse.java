package com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse;

import com.bharatpe.lending.lendingplatform.underwriting.dto.pojo.ApiError;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingApiResponse<T> {
    private boolean success;
    private ApiError apiError;
    private T data;

    public UnderwritingApiResponse(boolean success, T data) {
        this.success = success;
        this.data = data;
        this.apiError = null;
    }

    public static <T> UnderwritingApiResponse<T> success(T data) {
        return new UnderwritingApiResponse<>(true, data);
    }

    public static <T> UnderwritingApiResponse<T> error(String errorStatusCode, String errorMessage, T data) {
        return new UnderwritingApiResponse<>(false, new ApiError(errorStatusCode, errorMessage), data);
    }
}
