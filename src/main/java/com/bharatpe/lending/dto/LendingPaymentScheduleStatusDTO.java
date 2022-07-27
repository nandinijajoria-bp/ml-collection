package com.bharatpe.lending.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LendingPaymentScheduleStatusDTO {
    Long id;
    String status;
}
