package com.bharatpe.lending.loanV3.dto.request.oxyzo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OxyzoBreRequestDTO {

    private String organisationId;
    private BigDecimal loanAmount;
    private BigDecimal interestRate;
    private String referenceLoanId;
    private Integer numEdis;
    private BigDecimal processingAmount;
    private BigDecimal tpvMultiplier;
    private BigDecimal tpv;
    private BigDecimal dailyTpv;
    private BigDecimal ediDailyTpvRatio;
    private String productType;
    private String callbackUrl;
}
