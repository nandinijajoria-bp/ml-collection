package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.common.enums.Loan;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LatestLoanDetailResponse {

    LoanDetail approved;

    LoanDetail rejected;

    LoanDetail disbursed;

    @Builder
    @Data
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class LoanDetail{
        Double loanAmount;
        String status;
        Long updatedAt;
    }
}
