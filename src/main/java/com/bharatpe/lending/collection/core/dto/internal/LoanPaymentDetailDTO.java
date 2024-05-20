package com.bharatpe.lending.collection.core.dto.internal;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanPaymentDetailDTO {
    boolean adjustNach;
    double advanceEdiAmount;
    double otherAmount;
    long orderId;
    String transferType;
    String remark;
    String source;
    String terminalOrderId;
    boolean foreCloser;
    String bankRefNumber;

    //internal use only
    @JsonIgnore double nachBalanceUsed;
    @JsonIgnore double advanceEdiAmountUsed;
    @JsonIgnore double otherAmountUsed;

}
