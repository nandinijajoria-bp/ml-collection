package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Eligibility {
    private Double loanAmount;
    private Integer arrangerFee;
    private Double interestRate;
    private Double initialRoi;
    private Integer repaymentAmount;
    private Integer ediCount;
    private Integer ediAmount;
    private String tenure;
    private String category;
    private String loanType;
    private Double clubV2Amount;
    private Long uniqueKey;
}
