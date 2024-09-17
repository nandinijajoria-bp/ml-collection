package com.bharatpe.lending.loanV3.dto.request.payu;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayULoanPreviewRequestDTO {

    @JsonProperty("application-id")
    private String applicationId;

    private Integer amount;

    private Number tenure;

    private String roi;

    private Number pf;

    @JsonProperty("pf_type")
    private String pfType;

}
