package com.bharatpe.lending.loanV3.dto;

import com.bharatpe.lending.entity.LenderAssignmentRules;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExtractedRulesAndLendersDTO {
    RiskParamsDTO riskParamsDTO;
    List<LenderAssignmentRules> lenderAssignmentRules;

    Boolean runModelAssignmentOnly;

    Long merchantId;

    List<String> filteredLenders;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RiskParamsDTO {
        Double bureauScore;
        String riskSegment;
        String riskGroupLike;
        String pincodeColor;
        Boolean isGstOffer;
        String tenureInMonths;
        Double amount;
    }
}
