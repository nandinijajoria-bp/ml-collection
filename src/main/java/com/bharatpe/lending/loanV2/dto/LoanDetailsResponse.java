package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.enums.KycStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanDetailsResponse {
    private boolean hasExperian = false;
    private KycStatus kycStatus;
    private boolean activeLoan = false;
    private String pancard;
    private String pincode;
    private boolean bpClubMember = false;
    private String ineligible;
    private boolean changeBankAccount;
    private String creditLineDeeplink;
    private Eligibility eligibility;
    private LoanApplicationDetails loanApplication;
    private String merchantName;
    private boolean bankLinked = false;
    private boolean repeatLoan = false;
    private BankAccountDetails accountDetails;
    private List<String> businessCategories;
    private Map<String, List<String>> businessSubCategories;
}
