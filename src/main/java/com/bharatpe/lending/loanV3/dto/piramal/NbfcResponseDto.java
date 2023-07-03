package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class NbfcResponseDto<T> {

    Boolean success;
    String applicationId;
    String productName;
    String lender;
    T data;

    String error;

    @lombok.Data
    public static class Error {
        String errorCode;
        String errorDescription;
        String httpStatus;
        List<String> missingFields;
    }
}
