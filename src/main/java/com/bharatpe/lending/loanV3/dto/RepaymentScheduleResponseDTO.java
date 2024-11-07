package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RepaymentScheduleResponseDTO {
    String repaymentSchedule;
    Double totalInterestPayable;
    Double totalRepaymentExpected;
    Double netDisbursalAmount;
}