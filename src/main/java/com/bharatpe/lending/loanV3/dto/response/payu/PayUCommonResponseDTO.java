package com.bharatpe.lending.loanV3.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayUCommonResponseDTO<T> {

    private String apiStatus;
    private int httpStatus;
    private String errorCode;
    private T apiResponse;
    private String message;
    private String headers;
    private Integer page;
    private List<String> errors;

}
