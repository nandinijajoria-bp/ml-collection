package com.bharatpe.lending.dto;

import com.bharatpe.common.entities.Experian;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class GlobalLimitResponse {
    private boolean success;
    private String message;
    private Data data;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Data {
        private Long merchantId;
        private Double globalLimit;
        private boolean derog;
        private String rejectReason;
        private String rejectionType;
        private String pancardName;
        private Experian experian;
        List<OfferDetail> offerDetails;
        Double version;
        String riskSegment;
        String riskGroup;
        Double limit;
        String loanType;
    }

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class OfferDetail {
        private Double interestRate;
        private Double maxLoanAmount;
        private Double loanAmount;
        private Double bureauLimit;
        private Double ntcLimit;
        private Integer tenure;
        private Integer ediCount;
        private Double processingFee;
    }
}
