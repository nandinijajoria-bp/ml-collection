package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountAggregatorInitiateResponseDTO {
    Boolean success;
    String message;
    String requestId;
    AAInitiateResponseData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AAInitiateResponseData {
        String status;
        String statusCode;
        String trackingId;
        String referenceId;
        String notificationType;
        String dataDetails;
        String redirectionUrl;
        String vuaId;
        String accountAggregatorId;
        String ver;
        String timeStamp;
        String errorCode;
        String errorMsg;
        String timestamp;
        String message;
    }

}
