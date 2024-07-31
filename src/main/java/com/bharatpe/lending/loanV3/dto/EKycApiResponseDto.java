package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EKycApiResponseDto {
    Long applicationId;
    String lender;
    Boolean success;
    String productName;
    Response data;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String responseStatus;
        private ResponseData data;
        private Error error;


        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Error {
            private String code;
            private String description;
            private String errorType;
        }

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ResponseData {
            private String captureExpiresAt;
            private String captureLink;
            private String profileId;
        }
    }
}
