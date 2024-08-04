package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EKycCallbackResponseDto {
    Boolean success;
    String applicationId;
    String productName;
    String lender;
    Response data;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        String responseStatus;
        Long status;
        String accountId;
        @JsonProperty("partner_request_id")
        String partnerRequestId;
        ResponseData data;

    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseData {
        @JsonProperty("profile_id")
        String profileId;
        String digixmlaadhaar;
    }
}
