package com.bharatpe.lending.dto;

import com.bharatpe.lending.common.enums.LendingEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class UPIPgRequestDto {

    private String orderId;
    private double orderAmount;
    private String mandateId;
    private long executionDate;
    private LendingEnum.LENDER lender;
}
