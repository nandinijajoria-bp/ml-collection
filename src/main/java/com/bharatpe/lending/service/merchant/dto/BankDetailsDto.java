package com.bharatpe.lending.service.merchant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class BankDetailsDto {

    String beneficiaryName;
    String accountNumber;
    String ifsc;
    String bankLogo;
    String bankName;

}
