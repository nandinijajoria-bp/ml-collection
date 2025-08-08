package com.bharatpe.lending.lendingplatform.nbfc.dto.callback;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VKYCCallback {
    private String partnerLoanId;
    private String trackingId;
    private String status;
    private RejectInfo rejectInfo;

    @Data
    @Builder
    public static class RejectInfo {
        private String code;
        private String reason;
    }
}
