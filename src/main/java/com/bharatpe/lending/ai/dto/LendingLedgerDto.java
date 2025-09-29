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
public class LendingLedgerDto {
    private Long merchantId;
    private Long merchantStoreId;
    private Long lendingPaymentScheduleId; // Only ID instead of full object
    private Long settlementId;
    private String txnType;
    private Date date;
    private Double amount;
    private String description;
    private Double principle;
    private Double interest;
    private Double otherCharges;
    private Double penalty;
    private String adjustmentMode;
    private String transferType;
    private String terminalOrderId;
}

