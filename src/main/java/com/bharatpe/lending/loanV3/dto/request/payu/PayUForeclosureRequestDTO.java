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
public class PayUForeclosureRequestDTO {

    @JsonProperty("application-id")
    private String applicationId;

    private Integer loanId;

    private String transactionDate;

    private String utr;

    private String referenceId;

    private Number amount;

    private String paymentMode;

    private String note;
}
