package com.bharatpe.lending.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LendingCollectionExcessDto {
    private Long merchantId;
    private Long merchantStoreId;
    private Long loanId;
    private Double excessNachCreditAmount;
    private Double amount;
    private String terminalOrderId;
    private String status;
    private String transferType;
    private Date creditDate;
    private Double deductedAmount;
    private Integer deductionCount;
    private String postingRequired;
    private String mode;
    private String source;
}
