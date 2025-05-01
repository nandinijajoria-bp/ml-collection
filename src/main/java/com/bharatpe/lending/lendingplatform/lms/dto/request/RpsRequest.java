package com.bharatpe.lending.lendingplatform.lms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RpsRequest {

    @NotNull
    @Valid
    private LoanDetails loanDetails;

    @NotNull
    @Valid
    private ScheduleDetails scheduleDetails;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LoanDetails {
        @NotNull
        private BigDecimal loanAmount;
        @NotNull
        private BigDecimal interestRate;
        @Positive
        private int loanTenure;
        @NotNull
        private String lender;
        @Positive
        private int ediAmount;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ScheduleDetails {
        @NotBlank
        private String loanStartDate;
        @NotBlank
        private String firstDueDate;
    }
}
