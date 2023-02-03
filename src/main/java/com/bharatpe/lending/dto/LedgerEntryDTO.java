package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.ToString;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@ToString
public class LedgerEntryDTO {
    private String type;
    private Double amount;
    private Double principle;
    private Double interest;
    private Long loanId;
    private Long merchantId;
    private String description;
}
