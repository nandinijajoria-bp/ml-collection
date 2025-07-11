package com.bharatpe.lending.loanV3.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class LenderAggregationResponseDto {
    private Long applicationId;
    private String message;
    private List<LenderData> lenders;
    private Integer edi;
    private Double interestRate;
    private Double apr;
    private Double processingFee;
    private Double loanAmount;
    private String tenure;
    private String loanType;
    private String screenType;
    private Integer attemptCount;
    private String previousLender;
    private Boolean isRepeatLoan;
    private String lender;
    private Long merchantId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LenderData {
        private String lenderName;
        private String approvalRate = "HIGH";
        private Boolean isRejected = false;
        private Double edi;
        private Double processingFee;
        private List<PenaltyConfig> penaltyConfigs;
        private Integer nachBounceAmount;
        private Double interestRate;
        private List<ForeClosureEntityDTO> foreClosureEntityDTOList;
        private Double apr;
        private Double irr;

        public LenderData(String lenderName, String lenderLogo, Boolean isRejected) {
            this.lenderName = lenderName;
            this.isRejected = isRejected;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PenaltyConfig {
            private Long minAmount;
            private Long maxAmount;
            private Double penalty;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ForeClosureEntityDTO {
            private Double rate;
            private Double minAmount;
            private Long tenure;
            private Long durationTo;
            private Long durationFrom;
        }
    }
}