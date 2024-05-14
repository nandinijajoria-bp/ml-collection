package com.bharatpe.lending.collection.dto;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LoanClosureDTO {
    private LendingPaymentSchedule activeLoan;
    private LendingLedger lendingLedger;
    private Long orderId;
    private String paymentType;
}
