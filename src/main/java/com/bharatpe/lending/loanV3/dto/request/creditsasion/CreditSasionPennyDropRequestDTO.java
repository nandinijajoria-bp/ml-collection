package com.bharatpe.lending.loanV3.dto.request.creditsasion;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditSasionPennyDropRequestDTO {

    private String partnerLoanId;
    private String loanProductCode;
    private List<BankAccount> bankAccounts;
    private Boolean isRetry;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BankAccount {
        private String type;
        private String holderName;
        private String accountNumber;
        private String branch;
        private String bankName;
        private String ifscCode;
    }
}
