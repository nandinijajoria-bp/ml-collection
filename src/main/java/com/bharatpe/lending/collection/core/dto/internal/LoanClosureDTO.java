package com.bharatpe.lending.collection.core.dto.internal;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanClosureDTO {
    private LendingPaymentSchedule activeLoan;
    private LendingLedger lendingLedger;
    private Long orderId;
    private String paymentType;
    private boolean foreClosure;
}
