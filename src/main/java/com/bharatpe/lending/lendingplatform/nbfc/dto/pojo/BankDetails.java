package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class BankDetails {
    private String beneficiaryName;
    private String accountNumber;
    private String ifsc;
    private String bankLogo;
    private String ifscLogo;
    private String bankName;
    private String signInType;
    private String bankCode;
    private String accountType;
}
