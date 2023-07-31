package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.common.enums.BankStatementSessionStatus;
import com.bharatpe.lending.enums.KycStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class LoanDetailsResponse {
    private boolean hasExperian = false;
    private KycStatus kycStatus;
    private boolean kycDone=false;
    private boolean activeLoan = false;
    private String pancard;
    private String pincode;
    private boolean bpClubMember = false;
    private String ineligible;

    private boolean changeBankAccount;
    private String errorString;
    private String stageOneHitId;
    private String stageTwoHitId;
    private String creditLineDeeplink;
    private Eligibility eligibility;
    private LoanApplicationDetails loanApplication;
    private LoanApplicationDetails topupLoanApplication;
    private String merchantName;
    private boolean bankLinked = false;
    private boolean repeatLoan = false;
    private boolean isDummyMerchant = false;
    private BankAccountDetails accountDetails;
    private String businessName;
    private String businessCategory;
    private String businessSubCategory;
    private boolean eligibleForCallback;
    private String source;
    private Boolean clubV2Member = false;
    private Boolean showReferencePage = true;
    private Integer ediDaysModel;
    private Long merchantId;
    private Boolean showLoanUpgradeOffer = Boolean.FALSE;
    private Double excessNachAmount;
}
