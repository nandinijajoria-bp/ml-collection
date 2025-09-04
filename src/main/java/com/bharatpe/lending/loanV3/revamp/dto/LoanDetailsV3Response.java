package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.enums.VkycStatus;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV2.dto.Eligibility;
import com.bharatpe.lending.loanV3.dto.LenderAggregationResponseDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanDetailsV3Response {
    private String applicationStatus;
    private Boolean hasExperian;
    private KycStatus kycStatus;
    private KycStatus kycPanStatus;
    private Boolean kycDone;
    private Boolean showKycPage;
    private Boolean activeLoan;
    private String pancard;
    private String pincode;
    private Boolean bpClubMember;
    private String ineligible;
    private Boolean changeBankAccount;
    private String errorString;
    private String stageOneHitId;
    private String stageTwoHitId;
    private String creditLineDeeplink;
    private Eligibility eligibility;
    private LoanApplicationDetailsV3 loanApplication;
    private LoanApplicationDetailsV3 topupLoanApplication;
    private String merchantName;
    private Boolean bankLinked;
    private Boolean repeatLoan;
    private Boolean dummyMerchant;
    private BankAccountDetails accountDetails;
    private String businessName;
    private String businessCategory;
    private String businessSubCategory;
    private Boolean eligibleForCallback;
    private String source;
    private Boolean clubV2Member;
    private Boolean showReferencePage;
    private Integer ediDaysModel;
    private Long merchantId;
    private boolean paymentBank;
    private boolean hasLinkedPaymentBank;

    private String currentPage;
    private String nextPage;

    private String lender;
    private Double interestRate;
    private Double annualRoi;
    private Integer arrangerFee;
    private Double disbursalAmount;
    private String tenure;
    private Integer ediAmount;
    private Integer ediCount;
    private com.bharatpe.lending.loanV2.dto.AgreementResponse.Repayment repayment;
    private Boolean ediModelModified;
    private Boolean resubmitDone;
    private Boolean smsPermissionIsActive;
    private Boolean locationPermissionIsActive;
    private Date locationPermissionDate;

    private String mobile;
    private String kycDeeplink;
    private Boolean isSelfieResumit;
    private Boolean isPreapprovedRepeatLoan;
    private String kycMessage;
    private Boolean isPanNsdlVerified;
    private Boolean maxCountReached;
    private Boolean eligibilityExceptionFlag;

    private Boolean upiAutoPayEligible;
    private String upiAutoPayMandateStatus;
    private Boolean agreementDone;

    private String offerIncreased;
    private Double previousFinalOffer;

    private Boolean lenderKycPipe;
    private String fullName;
    private String dob;
    private Long refreshCountDownMinutes;
    private Double apr;
    private Double loanOffer;
    private Long applicationId;
    private List<LenderAggregationResponseDto.LenderData> lenders;
    private String message;
    private String screenType;
    private Double loanAmount;
    private Double emiLoanAmount;
    private Integer emiMinLoanAmountAllowed;
    private String emiRiskSegment;
    private Boolean emiRejected;
    private String rejectReason;
    private Integer emiEligibleIn;
    private String loanType;
    private String previousLender;
    private Double processingFee;
    private Integer attemptCount;
    private Boolean isAggregationFlowApplicable;
    private String blDocUploadUrl;
    private List<String> maskedMobileList;
    private boolean retryLimitExhausted;
    private Boolean udyamRegistrationRequired;
    private String udyamRegistrationLink;
    private String kycAddress;
    private Boolean invalidState;
    private Boolean isAadhaarAddressVerified;
    private Boolean loanPurpose;
    private Boolean skipShopPicture;
    private Boolean imageExist;
    private String udyamFlowStatus;
    private Boolean vkycCompleted;
    private VkycStatus vkycStatus;
    private Boolean vkycEligible;
    private Boolean dkycEligible;
    private Boolean skipKycEligible;
    private LendingApplication lendingApplication;
    private String lenderAggregationScreen;

    @Data
    @ToString
    @Builder
    public static class Repayment {
        private Double principal;
        private Double interest;
        private Double total;
    }
}
