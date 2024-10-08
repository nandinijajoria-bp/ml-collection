package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AbflGenerateDocRequestDto {
    private String applicationId;
    private String uniqueId;
    private String loanAmount;
    private String disbursementAmount;
    private String tenure;
    private String processingFee;
    private String roi;
    private String parentLanNo;
    private Double parentLoanOutstandingAmount;
}

