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
public class OxyzoForeclosureDetailsRequestDTO {

    private String loanId;

    private BigDecimal amount;

    private String referenceNo;

    private Long repaymentDate;

    private Long closureDate;

    private BigDecimal penalCharges;

    private BigDecimal latePaymentCharges;

    private BigDecimal foreclosureCharges;

    private BigDecimal bounceCharges;

    private Boolean isCoolOffCase;

    private BigDecimal coolingOffCharges;

    private BigDecimal creditAmount;

}
