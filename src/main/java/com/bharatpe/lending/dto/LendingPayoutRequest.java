package com.bharatpe.lending.dto;

import com.bharatpe.lending.enums.LendingPayoutType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Getter
@Setter
@ToString
@NoArgsConstructor
public class LendingPayoutRequest {
    private Long ownerId;
    private String orderId;
    private Double amount;
    private LendingPayoutType txnType;
    private Long paymentTxnId;
    private Long merchantId;
    private Long merchantStoreId;

    public LendingPayoutRequest(Long ownerId, String orderId, Double amount, LendingPayoutType txnType, Long merchantId) {
        this.ownerId = ownerId;
        this.orderId = orderId;
        this.amount = amount;
        this.txnType = txnType;
        this.merchantId = merchantId;
    }
}
