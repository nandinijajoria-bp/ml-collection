package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LdcTopConsentApiResponseDTO {
    Boolean success;
    String errorCode;
    String message;
    String debugMessage;
    LdcTopConsentResponseData data;

    @Data
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LdcTopConsentResponseData {
        Integer responseCode;
        Integer code;
        String message;
        Response response;
    }

    @Data
    public static class Response {
        Integer code;
    }
}
