package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LenderEdIScheduleResponseDTO {
    List<RepaymentSchedule> repaymentSchedule;
    Double totalInterestPayable;
    Date loanMaturityDate;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepaymentSchedule {
        Date dueDate;
        Double principal;
        Double interest;
        Double openingBalance;
        Integer totalEdi;
    }
}
