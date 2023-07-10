package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class Gst3bSessionResponseDTO {
    Boolean success;
    String message;
    String debugMessage;
    Long statusCode;
    String requestId;
    List<Error> errors;

    @Data
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Error {
        String fieldName;
        String error;
    }
}
