package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DocUploadApiResponse {

    Long applicationId;
    String lender;
    String productName;
    Boolean success;
    Data data;

    @lombok.Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Data {

        String responseStatus;
        ErrorPayload error;

        @lombok.Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ErrorPayload {
            String code;
            String description;
            String errorType;
        }
    }
}
