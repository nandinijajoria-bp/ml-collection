package com.bharatpe.lending.loanV3.dto.request.sib;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SibForeclosureRequest {
    @JsonProperty("npos_config_id")
    private Integer nposConfigId;

    @JsonProperty("investor_loan_id")
    private String investorLoanId;
}
