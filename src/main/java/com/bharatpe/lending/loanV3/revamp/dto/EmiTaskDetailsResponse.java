package com.bharatpe.lending.loanV3.revamp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmiTaskDetailsResponse {
    private boolean success;
    private String httpStatus;
    private String statusMessage;
    private String requestId;
    private String timeStamp;
    private EmiTaskDetailsResponse.Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Long merchantId;
        private Long applicationId;
        private String status;
        private LocalDateTime lastRejectedDate;
        private LocalDateTime agreementDate;
        private boolean isNachDone;
        private boolean isAgreementDone;
        private LocalDateTime nachCompletionTime;
        private String stage;
        private LocalDateTime disbursalTimeStamp;
    }
}
