package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class Eligibility {
    private Double loanAmount;
    private Integer arrangerFee;
    private Double interestRate;
    private Integer repaymentAmount;
    private Integer ediCount;
    private Integer ediAmount;
    private String tenure;
    private String category;
    private String loanType;
}
