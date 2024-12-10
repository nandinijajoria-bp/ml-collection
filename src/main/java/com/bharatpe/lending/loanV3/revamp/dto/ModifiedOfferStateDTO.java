package com.bharatpe.lending.loanV3.revamp.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
public class ModifiedOfferStateDTO {
    private Long applicationId;
    private Integer ediAmount;
    private Integer ediCount;
    private Double apr;
    private String tenure;
    private Double interestRate;
    private Integer arrangerFee;
    private Double loanOffer;
    private Long merchantId;
}