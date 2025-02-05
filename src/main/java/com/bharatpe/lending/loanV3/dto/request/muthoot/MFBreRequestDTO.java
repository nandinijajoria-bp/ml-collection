package com.bharatpe.lending.loanV3.dto.request.muthoot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MFBreRequestDTO {

    public String customerID;
    private RiskVariables riskVariables;
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RiskVariables {

        private Double tpv;
        private Double loanAmount;
        private Integer tenure;
        private String riskGroup;
        private String riskSegment;
        private String pincodeColour;
        private Double netFreeIncome;

    }

}
