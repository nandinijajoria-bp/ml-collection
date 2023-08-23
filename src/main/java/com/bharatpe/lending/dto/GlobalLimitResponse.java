package com.bharatpe.lending.dto;

import com.bharatpe.common.entities.Experian;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class GlobalLimitResponse {
    private boolean success;
    private String message;
    private Data data;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private Long merchantId;
        private Double globalLimit;
        private boolean derog;
        private String rejectReason;
        private String rejectionType;
        private String pancardName;
        private Experian experian;
        private String errorString;
        private String stageOneId_;
        private String stageTwoId_;
        List<OfferDetail> offerDetails;
        Double version;
        String riskSegment;
        String riskGroup;
        Double limit;
        String loanType;
        Boolean isClubV2Member;
        Boolean bankAffectedOffer;
        Boolean gst3bAffectedOffer;
        private Boolean preApprovedLoan;
    }

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OfferDetail {
        private Double interestRate;
        private Double initialRoi;
        private Double maxLoanAmount;
        private Double loanAmount;
        private Double bureauLimit;
        private Double ntcLimit;
        private Integer tenure;
        private Integer ediCount;
        private Double processingFee;
        private Double clubV2Amount;
    }
}
