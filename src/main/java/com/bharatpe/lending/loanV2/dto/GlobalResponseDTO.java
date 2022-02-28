package com.bharatpe.lending.loanV2.dto;

import lombok.Data;

import java.util.List;

@Data
public class GlobalResponseDTO {

    List<TenureDetail> tenureDetails;
    String version;
    String riskSegment;
    String riskGroup;
    Double limit;

    @Data
    public class TenureDetail {
        private Double interestRate;
        private Double maxLoanAmount;
        private Integer tenure;
        private Integer ediCount;
        private Double processingFee;
    }
}
