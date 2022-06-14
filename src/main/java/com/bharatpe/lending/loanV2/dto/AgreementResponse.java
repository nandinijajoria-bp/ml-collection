package com.bharatpe.lending.loanV2.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
public class AgreementResponse {
    private Long applicationId;
    private String lender;
    private Double loanAmount;
    private Double interestRate;
    private Integer arrangerFee;
    private Double disbursalAmount;
    private String tenure;
    private Integer ediAmount;
    private Integer ediCount;
    private Repayment repayment;
    private BankAccountDetails accountDetails;
    private boolean bpClubMember;
    private boolean clubV2;

    @Data
    @ToString
    @Builder
    public static class Repayment {
        private Double principal;
        private Double interest;
        private Double total;
    }
}
