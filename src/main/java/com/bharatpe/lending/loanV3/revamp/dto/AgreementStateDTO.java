package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.dto.LoanInsuranceDTO;
import com.bharatpe.lending.loanV2.dto.AddressDetails;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgreementStateDTO {
    private Long applicationId;
    private String lender;
    private Double loanAmount;
    private Double interestRate;
    private Double annualRoi;
    private Integer arrangerFee;
    private Double disbursalAmount;
    private String tenure;
    private Integer ediAmount;
    private Integer ediCount;
    private com.bharatpe.lending.loanV2.dto.AgreementResponse.Repayment repayment;
    private BankAccountDetails accountDetails;
    private AddressDetails addressDetails;
    private boolean bpClubMember;
    private boolean clubV2;
    private boolean ediModelModified;
    private Boolean enachBank;
    private boolean isTopup;
    private Double apr;
    private List<LoanInsuranceDTO.InsuranceDetails> loanInsurances;
    private Boolean isInsured;
    private String externalLoanId;
    private Long merchantId;
    private Boolean isAadhaarAddressVerified;
    private Boolean loanPurpose;
    private String businessName;
    @Data
    @ToString
    @Builder
    public static class Repayment {
        private Double principal;
        private Double interest;
        private Double total;
    }
}
