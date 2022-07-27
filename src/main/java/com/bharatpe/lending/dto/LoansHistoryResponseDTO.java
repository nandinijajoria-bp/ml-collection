package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LoansHistoryResponseDTO {
    private Long loanId;
    private Double loanAmount;
    private String status;
    private Date startDate;
    private Date closingDate;
    private Date tentativeClosingDate;
    private Double interestRate;
    private Float tenure;
}
