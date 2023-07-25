package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.entity.LendingRefundLedger;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class ExcessNachDetailDTO {
    private Double amount;
    private String status;
    private String terminalOrderId;
    private String utr;
    private Date refundTxnDate;
    private Date settlementDate;

    public static ExcessNachDetailDTO from(LendingRefundLedger lendingRefundLedger) {
        return ExcessNachDetailDTO.builder()
                .amount(lendingRefundLedger.getAmount())
                .utr(lendingRefundLedger.getReferenceNo())
                .status(lendingRefundLedger.getStatus())
                .refundTxnDate(lendingRefundLedger.getCreatedAt())
                .settlementDate(lendingRefundLedger.getSettlementDate())
                .terminalOrderId(lendingRefundLedger.getTerminalOrderId())
                .build();
    }
}
