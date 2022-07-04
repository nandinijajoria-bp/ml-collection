package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateLendingLedgerRequestDTO {
    private Long merchantId;
    private Long merchantStoreId;
    private Long lendingSchedulePaymentId;
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
}
