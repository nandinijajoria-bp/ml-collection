package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV2.dto.Eligibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

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

    private String currentPage;
    private String nextPage;

    private String lender;
    private Double interestRate;
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
    private String dob;
    private String fullName;
    private String message;

    @Data
    @ToString
    @Builder
    public static class Repayment {
        private Double principal;
        private Double interest;
        private Double total;
    }

    public static LoanDetailsV3Response populateResponseForRequestWithScope(LendingStateDTO<?> lendingStateDTO, LoanDetailsV3Response loanDetailsV3Response) {
        try {
            switch (lendingStateDTO.getScopeState()) {
                case OFFER_PAGE:
                    setOfferResponse((EligibilityStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case PAN_PIN_PAGE:
                    setPanPinResponse((EligibilityStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case AGREEMENT_PAGE:
                    setAgreementResponse((AgreementStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case KEY_FACTOR_STATEMENT_PAGE:
                    setKfsResponse((KFSStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case KYC_PAGE:
                    setKycResponse((KYCStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case SHOP_DETAILS_PAGE:
                    setShopDetailsResponse((ShopDetailsStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case SHOP_PICTURES_PAGE:
                    setShopPicturesResponse((ShopPicturesStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case APPLICATION_STATUS_PAGE:
                    setApplicationStatusResponse((ApplicationStatusStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case REFERENCE_PAGE:
                    setReferencesResponse((ReferenceStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case LENDER_EVALUATION_PAGE:
//                    setLenderEvaluationResponse((LenderEvaluationStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case PERMISSIONS_PAGE:
                    setPermissionResponse((PermissionStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case ENACH_PAGE:
                    setEnachResponse((EnachStateDTO)lendingStateDTO.getData(),loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case KYC_ROUTE_TO_ELIGIBILITY:
                    setKYCRouteToEligibilityResponse((KYCRTEDto) lendingStateDTO.getData(),loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case RTE_PIN_PAGE:
                    setRTEPinPageResponse((EligibilityStateDTO) lendingStateDTO.getData(),loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;

                default:

            }
        } catch (Exception e) {
            log.error("Exception while casting data for {} {} {}", lendingStateDTO, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return loanDetailsV3Response;
    }

    private static void setKfsResponse(KFSStateDTO kfsStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setLoanApplication(kfsStateDTO.getLoanApplication());
        loanDetailsV3Response.setTopupLoanApplication(kfsStateDTO.getTopupLoanApplication());
        loanDetailsV3Response.setRepeatLoan(kfsStateDTO.isRepeatLoan());
        loanDetailsV3Response.setMobile(kfsStateDTO.getMobile());
    }

    private static void setKycResponse(KYCStateDTO kycStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setKycStatus(kycStateDTO.getKycStatus());
        loanDetailsV3Response.setKycDeeplink(kycStateDTO.getDeeplink());
        loanDetailsV3Response.setShowKycPage(kycStateDTO.getShowKycPage());
        loanDetailsV3Response.setIsSelfieResumit(kycStateDTO.isSelfieResumit());

        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setLenderAssc(kycStateDTO.getLenderAssc());
        loanDetailsV3Response.setLoanApplication(applicationDetails);
    }

    private static void setRTEPinPageResponse(EligibilityStateDTO eligibilityStateDTO, LoanDetailsV3Response loanDetailsV3Response)
    {
        loanDetailsV3Response.setPancard(eligibilityStateDTO.getPancard());
        loanDetailsV3Response.setPincode(eligibilityStateDTO.getPincode());
        loanDetailsV3Response.setHasExperian(eligibilityStateDTO.isHasExperian());
        loanDetailsV3Response.setMerchantName(eligibilityStateDTO.getMerchantName());
    }


    private static void setKYCRouteToEligibilityResponse(KYCRTEDto kycStateDTO, LoanDetailsV3Response loanDetailsV3Response)

    {
        loanDetailsV3Response.setKycStatus(kycStateDTO.getKycStatus());
        loanDetailsV3Response.setKycDeeplink(kycStateDTO.getDeepLink());
        loanDetailsV3Response.setShowKycPage(kycStateDTO.getShowKycPage());

    }

    private static void setAgreementResponse(AgreementStateDTO agreementStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setApplicationId(agreementStateDTO.getApplicationId());
        applicationDetails.setLoanAmount(agreementStateDTO.getLoanAmount());
        applicationDetails.setEnachBank(agreementStateDTO.getEnachBank());
        if(agreementStateDTO.isTopup())loanDetailsV3Response.setTopupLoanApplication(applicationDetails);
        else loanDetailsV3Response.setLoanApplication(applicationDetails);

        loanDetailsV3Response.setLender(agreementStateDTO.getLender());
        loanDetailsV3Response.setInterestRate(agreementStateDTO.getInterestRate());
        loanDetailsV3Response.setArrangerFee(agreementStateDTO.getArrangerFee());
        loanDetailsV3Response.setDisbursalAmount(agreementStateDTO.getDisbursalAmount());
        loanDetailsV3Response.setTenure(agreementStateDTO.getTenure());
        loanDetailsV3Response.setEdiCount(agreementStateDTO.getEdiCount());
        loanDetailsV3Response.setEdiAmount(agreementStateDTO.getEdiAmount());
        loanDetailsV3Response.setBpClubMember(agreementStateDTO.isBpClubMember());
        loanDetailsV3Response.setClubV2Member(agreementStateDTO.isClubV2());
        loanDetailsV3Response.setEdiModelModified(agreementStateDTO.isEdiModelModified());
        loanDetailsV3Response.setRepayment(agreementStateDTO.getRepayment());
        loanDetailsV3Response.setAccountDetails(agreementStateDTO.getAccountDetails());
    }

    private static void setShopDetailsResponse(ShopDetailsStateDTO shopDetailsStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setDummyMerchant(shopDetailsStateDTO.isDummyMerchant());
        loanDetailsV3Response.setBusinessName(shopDetailsStateDTO.getBusinessName());
        loanDetailsV3Response.setBusinessCategory(shopDetailsStateDTO.getBusinessCategory());
        loanDetailsV3Response.setBusinessSubCategory(shopDetailsStateDTO.getBusinessSubCategory());
        loanDetailsV3Response.setPincode(shopDetailsStateDTO.getPincode());

        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setApplicationId(shopDetailsStateDTO.getApplicationId());
        applicationDetails.setApplicationStatus(shopDetailsStateDTO.getApplicationStatus());
        applicationDetails.setRejectReason(shopDetailsStateDTO.getResubmitReason());
        loanDetailsV3Response.setLoanApplication(applicationDetails);

        if(Objects.nonNull(shopDetailsStateDTO.getResubmitDone())){
            loanDetailsV3Response.setResubmitDone(shopDetailsStateDTO.getResubmitDone());
        }
    }

    private static void setShopPicturesResponse(ShopPicturesStateDTO shopPicturesStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setDummyMerchant(shopPicturesStateDTO.isDummyMerchant());
        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setApplicationId(shopPicturesStateDTO.getApplicationId());
        loanDetailsV3Response.setLoanApplication(applicationDetails);
    }

    private static void setApplicationStatusResponse(ApplicationStatusStateDTO applicationStatusStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setApplicationId(applicationStatusStateDTO.getApplicationId());
        applicationDetails.setTransferDays(applicationStatusStateDTO.getTransferDays());
        applicationDetails.setEnachDeeplink(applicationStatusStateDTO.getEnachDeeplink());
        applicationDetails.setEnachBank(applicationStatusStateDTO.getEnachBank());
        applicationDetails.setEnachDone(applicationStatusStateDTO.getEnachDone());
        applicationDetails.setReapply(applicationStatusStateDTO.getReapply());
        applicationDetails.setReapplyTime(applicationStatusStateDTO.getReapplyTime());
        applicationDetails.setReapplyTimeEpoch(applicationStatusStateDTO.getReapplyTimeEpoch());

        loanDetailsV3Response.setLoanApplication(applicationDetails);
    }

    private static void setReferencesResponse(ReferenceStateDTO referenceStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setDummyMerchant(referenceStateDTO.isDummyMerchant());
        loanDetailsV3Response.setApplicationStatus(referenceStateDTO.getApplicationStatus());
    }

    private static void setLenderEvaluationResponse(LenderEvaluationStateDTO lenderEvaluationStateDTO, LoanDetailsV3Response loanDetailsV3Response){
    }

    private static void setPermissionResponse(PermissionStateDTO permissionStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setErrorString(permissionStateDTO.getErrorString());
        loanDetailsV3Response.setDummyMerchant(permissionStateDTO.getDummyMerchant());
        loanDetailsV3Response.setSmsPermissionIsActive(permissionStateDTO.getSmsPermissionIsActive());
        loanDetailsV3Response.setLocationPermissionIsActive(permissionStateDTO.getLocationPermissionIsActive());
        loanDetailsV3Response.setLocationPermissionDate(permissionStateDTO.getLocationPermissionDate());
    }

    private static void setOfferResponse(EligibilityStateDTO eligibilityStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setErrorString(eligibilityStateDTO.getErrorString());
        loanDetailsV3Response.setEdiDaysModel(eligibilityStateDTO.getEdiDaysModel());
        loanDetailsV3Response.setKycPanStatus(eligibilityStateDTO.getKycPanStatus());
        loanDetailsV3Response.setPancard(eligibilityStateDTO.getPancard());
        loanDetailsV3Response.setPincode(eligibilityStateDTO.getPincode());
        loanDetailsV3Response.setBpClubMember(eligibilityStateDTO.getBpClubMember());
        loanDetailsV3Response.setClubV2Member(eligibilityStateDTO.getClubV2Member());
        loanDetailsV3Response.setAccountDetails(eligibilityStateDTO.getAccountDetails());
        loanDetailsV3Response.setEligibility(eligibilityStateDTO.getEligibility());
        loanDetailsV3Response.setIsPreapprovedRepeatLoan(eligibilityStateDTO.getIsPreapprovedRepeatLoan());
        loanDetailsV3Response.setIneligible(eligibilityStateDTO.getIneligible());
    }

    private static void setPanPinResponse(EligibilityStateDTO eligibilityStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setPancard(eligibilityStateDTO.getPancard());
        loanDetailsV3Response.setPincode(eligibilityStateDTO.getPincode());
        loanDetailsV3Response.setHasExperian(eligibilityStateDTO.isHasExperian());
        loanDetailsV3Response.setMerchantName(eligibilityStateDTO.getMerchantName());
        loanDetailsV3Response.setDob(eligibilityStateDTO.getDob());
        loanDetailsV3Response.setFullName(eligibilityStateDTO.getFullName());
        loanDetailsV3Response.setMessage(eligibilityStateDTO.getMessage());
    }

    private static void setEnachResponse(EnachStateDTO enachStateDTO,LoanDetailsV3Response loanDetailsV3Response){
        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        loanDetailsV3Response.setAccountDetails(enachStateDTO.getBankDetails());
        applicationDetails.setEnachDeeplink(enachStateDTO.getEnachDeeplink());
        applicationDetails.setEnachDone(enachStateDTO.getEnachDone());
        applicationDetails.setEnachMode(enachStateDTO.getEnachMode());
        applicationDetails.setNachStartedAt(enachStateDTO.getNachStartedAt());
        applicationDetails.setNachSessionStatus(enachStateDTO.getNachSessionStatus());
        applicationDetails.setNachSessionMode(enachStateDTO.getNachSessionMode());
        applicationDetails.setEnachErrorResponse(enachStateDTO.getEnachErrorResponse());
        if(enachStateDTO.isTopup())loanDetailsV3Response.setTopupLoanApplication(applicationDetails);
        else loanDetailsV3Response.setLoanApplication(applicationDetails);
    }

    public static LoanDetailsV3Response populateResponseForRequestWithoutScope(LendingStateDTO<?> lendingStateDTO, LoanDetailsV3Response loanDetailsV3Response) {
        try {
            loanDetailsV3Response.setNextPage(lendingStateDTO.getScopeState().name());
            return loanDetailsV3Response;
        } catch (Exception e) {
            log.error("Exception while casting data for {} {}", lendingStateDTO, e.getMessage());
        }
        return loanDetailsV3Response;
    }
}
