package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LendingPaymentScheduleDetailsDTO {
    private Long loanId;
    private Double loanAmount;
    private Double paidAmount;
    private Double totalPayableAmount;
    private Double ediAmount;
    private Integer ediCount;
}
