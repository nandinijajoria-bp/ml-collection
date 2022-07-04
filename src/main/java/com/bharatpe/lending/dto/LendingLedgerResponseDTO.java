package com.bharatpe.lending.dto;

import com.bharatpe.common.entities.LendingLedger;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.ObjectUtils;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LendingLedgerResponseDTO {
    private Long id;
    private Date createdAt;
    private Date updatedAt;
    private Long merchantId;
    private Long merchantStoreId;
    private String txnType;
    private Date date;
    private Double amount;
    private String description;
    private Double principle;
    private Double interest;
    private Double otherCharges;
    private Double penalty;
    private String adjustmentMode;
    private Long loanId;

    public static LendingLedgerResponseDTO from(LendingLedger lendingLedger) {
        if (ObjectUtils.isEmpty(lendingLedger)) {
            return  null;
        }

        LendingLedgerResponseDTO lendingLedgerResponseDTO = LendingLedgerResponseDTO.builder()
          .id(lendingLedger.getId())
          .createdAt(lendingLedger.getCreatedAt())
          .updatedAt(lendingLedger.getUpdatedAt())
          .merchantId(lendingLedger.getMerchantId())
          .merchantStoreId(lendingLedger.getMerchantStoreId())
          .txnType(lendingLedger.getTxnType())
          .date(lendingLedger.getDate())
          .amount(lendingLedger.getAmount())
          .description(lendingLedger.getDescription())
          .principle(lendingLedger.getPrinciple())
          .interest(lendingLedger.getInterest())
          .otherCharges(lendingLedger.getOtherCharges())
          .penalty(lendingLedger.getPenalty())
          .adjustmentMode(lendingLedger.getAdjustmentMode())
          .loanId(lendingLedger.getLendingPaymentSchedule().getId())
          .build();

        return lendingLedgerResponseDTO;
    }
}
