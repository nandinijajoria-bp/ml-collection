package com.bharatpe.lending.loanV3.dto.response.payu;

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
public class PayUBankUpdationResponseDTO {

    @JsonProperty("application_id")
    private String applicationId;

    @JsonProperty("account_number")
    private String accountNumber;

    @JsonProperty("ifsc_code")
    private String ifscCode;

    private String message;
}
