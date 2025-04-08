package com.bharatpe.lending.loanV3.revamp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmiDashboardResponse {
    private boolean success;
    private String httpStatus;
    private String statusMessage;
    private String requestId;
    private String timeStamp;
    private Data result;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private Long merchantId;
        private Long applicationId;
        private Double loanAmount;
        private Integer tenureMonth;
        private String status;
        private Double roi;
        private String lender;
        private boolean activeLoan;
        private boolean changeBankAccount;
        private Double emi;
        private String rejectReason;
        private boolean repeatLoan;
        private boolean dummyMerchant;
        private LocalDateTime lastRejectedDate;
    }
}
