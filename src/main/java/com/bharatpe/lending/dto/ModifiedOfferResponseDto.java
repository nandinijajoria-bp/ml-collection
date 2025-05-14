package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data@NoArgsConstructor
public class ModifiedOfferResponseDto {

    private OfferDetails oldOffer;
    private OfferDetails newOffer;
    private Boolean isOfferModified;

    @Data
    @AllArgsConstructor
    public static class OfferDetails {
        private Double loanAmount;
        private Double ediAmount;
        private Double interestRate;
        private Double processingFee;
        private Double repayment;
        private Double disbursalAmount;
        private Double apr;
        private Double irr;
        private Integer ediCount;
        private Integer tenure;
    }
}