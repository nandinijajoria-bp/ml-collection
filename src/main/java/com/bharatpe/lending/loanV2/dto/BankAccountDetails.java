package com.bharatpe.lending.loanV2.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
public class BankAccountDetails {
    private String bankName;
    private String accountNumber;
    private String branchName;
    private String bankLogo;
    private String beneficiaryName;
}
