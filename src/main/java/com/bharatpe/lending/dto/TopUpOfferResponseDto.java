package com.bharatpe.lending.dto;

import lombok.Data;
import java.util.List;

@Data
public class TopUpOfferResponseDto {
    private List<Offer> offers;
    private List<String> tenure;
    private Long existingApplicationId;
    private boolean success;
    private String message;

    @Data
    public static class Offer {
        private Long eligibleLoanId;
        private Double maxAmount;
        private Double amount;
        private Integer tenureInMonths;
        private Double edi;
        private Integer ediCount;
        private String category;
        private Double processingFee;
        private Double rateOfInterest;
        private Double repaymentAmount;
        private Double apr;
        private Double irr;
        private Double financeCharge;
        private String tenure;
    }
}