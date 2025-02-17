package com.bharatpe.lending.loanV3.dto.response.oxyzo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OxyzoCommonResponseDTO<T> {

    private Boolean success;
    private String errorMessage;
    private String errorCode;
    private String requestId;
    private String errorRootCause;
    private String taskRequestId;
    private String partnerId;
    private T data;
}
