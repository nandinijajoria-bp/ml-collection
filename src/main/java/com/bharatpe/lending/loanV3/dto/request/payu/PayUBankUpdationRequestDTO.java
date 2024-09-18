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
public class PayUBankUpdationRequestDTO {

    @JsonProperty("account_type_id")
    private String accountTypeId;

    @JsonProperty("bank_account_name")
    private String bankAccountName;

    @JsonProperty("account_number")
    private String accountNumber;

    @JsonProperty("bank_name")
    private String bankName;

    @JsonProperty("ifsc_code")
    private String ifscCode;

}
