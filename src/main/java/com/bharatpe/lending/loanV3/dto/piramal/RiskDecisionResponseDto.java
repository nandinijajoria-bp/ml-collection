package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiskDecisionResponseDto {

    RiskDecision riskDecision;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RiskDecision {
        String riskDecisionType;
        String rejectReason;
        String rejectCode;
        Double approvedLoanAmount;
        List<FeeList> feeList;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class FeeList {
            Double feeAmount;
            Boolean inclGST;
            String feeType;
        }

        Double totalAmountPayable;
        Double rateOfInterest;
        Double emiAmount;
        String emiPeriodicity;
        Integer loanTenor;
        String partnerApplicationId;
        Boolean ntc;

    }

    String leadId;
    String partnerApplicationId;
}
