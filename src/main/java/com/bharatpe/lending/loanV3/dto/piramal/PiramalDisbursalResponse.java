package com.bharatpe.lending.loanV3.dto.piramal;

import java.time.Instant;
import java.time.LocalDateTime;
        import lombok.Builder;
        import lombok.Data;

@Data
@Builder
public class PiramalDisbursalResponse {
    private String utrNumber;
    private String productId;
    private String leadId;
    private String loanAccountNumber;
    private Long disbursementDate;
    private Double disbursedAmount;
    private String disbursementName;
    private String disbursementId;
    private String status;
    private Boolean disbursementSuccessful;
    private Error error;

    @Data
    @Builder
    public static class Error {
        private String errorCode;
        private String errorMessage;
    }
}

