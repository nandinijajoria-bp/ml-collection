package com.bharatpe.lending.dto;

import com.bharatpe.lending.common.enums.RankingType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibleOffersResponseDTO {
    private Double loanAmount;
    private List<TenureWithLender> eligibleOffers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenureWithLender {
        private String category;
        private String tenure;
        private Integer tenureInMonths;
        private Integer ediCount;
        private List<LenderData> initialLenders;
        private List<LenderData> fallbackLenders;
        private List<String> rejectedLenders;
        private List<String> ineligibleLenders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LenderData  {
        private Long eligibleLoanId;
        private String lenderName;
        private Double processingFee;
        private Double apr;
        private Double irr;
        private Double edi;
        private Integer repaymentAmount;
        private Double interestRate;
        private Boolean isRejected;
        private String approvalRate;
        private Integer nachBounceAmount;
        private RankingType rankingType;
        private Integer rankingOrder;
        private List<PenaltyConfig> penaltyConfigs;
        private List<ForeClosureEntityDTO> foreClosureDetails;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PenaltyConfig {
        private Long minAmount;
        private Long maxAmount;
        private Double penalty;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForeClosureEntityDTO {
        private Double rate;
        private Double minAmount;
        private long tenure;
        private long durationTo;
        private long durationFrom;
    }
}