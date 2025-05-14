package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoanApplicationDetailsDto {
    private Long id;
    private Double edi;
    private Integer tenureInMonths;
    private Double loanAmount;
    private Long payableDays;
    private String lender;
}
