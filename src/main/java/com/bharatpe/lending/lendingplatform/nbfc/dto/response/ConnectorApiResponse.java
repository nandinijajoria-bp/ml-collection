package com.bharatpe.lending.lendingplatform.nbfc.dto.response;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApiError;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConnectorApiResponse<T> {
    private boolean success;
    private String applicationId;
    private String customerId;
    private Lender lender;
    private ApiError apiError;
    private T data;

    public ConnectorApiResponse(boolean success, T data) {
        this.success = success;
        this.data = data;
        this.apiError = null;
    }

    public ConnectorApiResponse(boolean success, ApiError apiError, T data) {
        this.success = success;
        this.data = data;
        this.apiError = apiError;
    }

    public static <T> ConnectorApiResponse<T> success(T data) {
        return new ConnectorApiResponse<>(true, data);
    }

    public static <T> ConnectorApiResponse<T> error(String errorStatusCode, String errorMessage, T data) {
        return new ConnectorApiResponse<>(false, new ApiError(errorStatusCode, errorMessage), data);
    }
}
