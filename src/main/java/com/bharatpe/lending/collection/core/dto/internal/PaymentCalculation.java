package com.bharatpe.lending.collection.core.dto.internal;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCalculation {
    double received;
    double used;
    double balance;
    double principleSettled;
    double interestSettled;
    double penaltySettled;
    double chargesSettled;
}
