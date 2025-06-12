package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EKycApiResponseDto {
    private Long applicationId;
    private String lender;
    private Boolean success;
    private String productName;
    private Response data;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String responseStatus;
        private ResponseData data;
        private Error error;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Error {
        private String code;
        private String description;
        private String errorType;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class ResponseData {
        private String id;
        private String createdAt;
        private String status;
        private String customerIdentifier;
        private String referenceId;
        private String transactionId;
        private String customerName;
        private int expireInDays;
        private boolean reminderRegistered;
        private AccessToken accessToken;
        private String workflowName;
        private boolean autoApproved;
        private String templateId;
        private String accountId;
        @JsonProperty("captureLink")
        private String captureLink;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class AccessToken {
        private String entityId;
        private String id;
        private String validTill;
        private String createdAt;
    }
}
