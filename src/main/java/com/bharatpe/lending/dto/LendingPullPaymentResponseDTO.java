package com.bharatpe.lending.dto;

import com.bharatpe.lending.common.entity.LendingPullPayment;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.ObjectUtils;

import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LendingPullPaymentResponseDTO {

    private Long id;
    private Date createdAt;
    private Date updatedAt;
    private Long merchantId;
    private Long merchantStoreId;
    private Double dueAmount;
    private Double duePrinciple;
    private Double dueInterest;
    private Double deductedAmount;
    private String mode;
    private Long loanId;
    private String status;
    private Long ownerId;
    private String txnDate;

    public static LendingPullPaymentResponseDTO from(LendingPullPayment lendingPullPayment) {
        if (ObjectUtils.isEmpty(lendingPullPayment)) {
            return null;
        }

        LendingPullPaymentResponseDTO lendingPullPaymentResponseDTO = LendingPullPaymentResponseDTO.builder()
          .id(lendingPullPayment.getId())
          .createdAt(lendingPullPayment.getCreatedAt())
          .updatedAt(lendingPullPayment.getUpdatedAt())
          .merchantId(lendingPullPayment.getMerchantId())
          .merchantStoreId(lendingPullPayment.getMerchantStoreId())
          .dueAmount(lendingPullPayment.getDueAmount())
          .duePrinciple(lendingPullPayment.getDuePrinciple())
          .dueInterest(lendingPullPayment.getDueInterest())
          .deductedAmount(lendingPullPayment.getDeductedAmount())
          .mode(lendingPullPayment.getMode())
          .loanId(lendingPullPayment.getLoanId())
          .status(lendingPullPayment.getStatus())
          .ownerId(lendingPullPayment.getOwnerId())
          .txnDate(lendingPullPayment.getTxnDate())
          .build();

        return lendingPullPaymentResponseDTO;
    }
}
