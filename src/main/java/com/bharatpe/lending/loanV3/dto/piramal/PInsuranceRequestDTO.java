package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PInsuranceRequestDTO {
    @JsonProperty("leadId")
    private String leadId;

    @JsonProperty("source")
    private String source;

    @JsonProperty("device")
    private String device;

    @JsonProperty("loanAmount")
    private Double loanAmount;

    @JsonProperty("loanTenureInMonths")
    private Integer loanTenureInMonths;
}
