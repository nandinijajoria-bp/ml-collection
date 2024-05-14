package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanPaymentDetailDto {
    boolean adjustNach;
    double advanceEdiAmount;
    double otherAmount;
    long orderId;
    String transferType;
    String remark;
    String owner;
    String terminalOrderId;
    boolean foreCloser;




    //internal use only
    @JsonIgnore double nachBalanceUsed;
    @JsonIgnore double advanceEdiAmountUsed;
    @JsonIgnore double otherAmountUsed;

}
