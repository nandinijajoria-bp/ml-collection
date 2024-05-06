package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV2.dto.Eligibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class LoanDashboardResponse {
    private KycStatus kycStatus;
    private boolean activeLoan = false;
    private String ineligible;

    private boolean changeBankAccount;
    private Eligibility eligibility;
    private LoanApplicationDetailsV3 loanApplication;
    private LoanApplicationDetailsV3 topupLoanApplication;
    private BankAccountDetails accountDetails;
    private String merchantName;
    private boolean repeatLoan = false;
    private boolean isDummyMerchant = false;
    private String source;
    private Long merchantId;
    private Double excessNachAmount;
    private Double excessCollectionAmount;
    private Double excessCollectionAdjusted;
    private Double excessCollectionBalance;
    private String preApprovedTag;
    /*private String panNumber;
    private Integer pinCode;
    private Boolean isMileStoneProgramExpired;
    private MileStoneEligibilityResponseDto routeToEligibilityData;*/
    private String diwaliBannerType;
    private String diwaliBannerEndDate;
    private Boolean eligiblityExceptionFlag;

}

