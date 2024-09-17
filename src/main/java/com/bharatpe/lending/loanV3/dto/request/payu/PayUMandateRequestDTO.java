package com.bharatpe.lending.loanV3.dto.request.payu;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayUMandateRequestDTO {

    @JsonProperty("application-id")
    private String applicationId;

    @JsonProperty("mandate_id")
    private String mandateId;

    private String umrn;

    @JsonProperty("mandate_state")
    private String mandateState;

    @JsonProperty("auth_mode")
    private String authMode;

    @JsonProperty("auth_amount")
    private Double authAmount;

    @JsonProperty("mandate_account_details")
    private MandateAccountDetails mandateAccountDetails;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MandateAccountDetails {

        private String ifsc;

        @JsonProperty("masked_account_number")
        private String maskedAccountNumber;
    }
}
