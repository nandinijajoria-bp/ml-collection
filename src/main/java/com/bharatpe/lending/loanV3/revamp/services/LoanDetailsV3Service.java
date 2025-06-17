package com.bharatpe.lending.loanV3.revamp.services;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.service.merchant.dto.*;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.enums.LoanSegment;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.lendingplatform.authentication.dto.response.ApiResponse;
import com.bharatpe.lending.loanV2.dto.AddressDetails;
import com.bharatpe.lending.loanV2.dto.EmiEligibility;
import com.bharatpe.lending.loanV3.dto.LenderAggregationResponseDto;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.stateManager.RenderStateViaScope;
import com.bharatpe.lending.loanV3.revamp.stateManager.RenderStateWithoutScope;
import com.bharatpe.lending.service.ImageURLService;
import com.bharatpe.lending.service.UploadDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.validation.constraints.NotNull;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates.AGREEMENT_PAGE;

@Service
@Slf4j
public class LoanDetailsV3Service {

    @Autowired
    RenderStateViaScope renderStateViaScope;

    @Autowired
    RenderStateWithoutScope renderStateWithoutScope;

    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    ImageURLService imageURLService;

    @Autowired
    LmsFieldValuesDao lmsFieldValuesDao;

    @Autowired
    UploadDocumentService uploadDocumentService;

    @Autowired
    LendingMerchantDetailsDao lendingMerchantDetailsDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    KycHandler kycHandler;

    @Value("${shop.picture.skip.enabled:false}")
    private boolean shouldSkipShopPicture;

    @Value("${lenders.skip.shop.picture:}")
    private List<String> lendersToSkipShopPicture;

    @Value("${skip.picture.threshold:0}")
    private int skipPictureThreshold;

    @Value("${sid.threshold}")
    Double sidThreshold;

    private static final Set<String> ALLOWED_SHOP_STRUCTURE_TYPES = new HashSet<>(Arrays.asList("permanent", "temporary"));

    public LoanDetailsV3Response getLoanDetails(LoanDetailsV3Request request, BasicDetailsDto merchant, String token)
    {
        log.info("LoanDetailsV3Request for {} : {}", merchant.getId(), request);
        LoanDetailsV3Response loanDetailsV3Response = new LoanDetailsV3Response();
        if (null != request.getScope()) {
            ScopeDataArgs scopeDataArgs = ScopeDataArgs.builder()
                    .currentState(LendingViewStates.valueOf(request.getScope()))
                    .loanDetailsV3Request(request)
                    .merchant(merchant)
                    .token(token)
                    .applicationId(request.getApplicationId())
                    .build();
            renderStateViaScope.populateNextLendingState(scopeDataArgs);
            if (AGREEMENT_PAGE.equals(scopeDataArgs.getCurrentState())) {
                loanDetailsV3Response.setKycAddress("");
                LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(request.getApplicationId());
                if (!ObjectUtils.isEmpty(lendingApplicationKycDetails) && !ObjectUtils.isEmpty(lendingApplicationKycDetails.getAadharAddress())) {
                    loanDetailsV3Response.setKycAddress(lendingApplicationKycDetails.getAadharAddress());
                }
            }
            populateResponseForRequestWithScope(merchant,scopeDataArgs.getLendingStateDTOForCurrPage(), loanDetailsV3Response, scopeDataArgs.getToken());
            log.info("LoanDetailsV3Response for {} : {} ", merchant.getId(), loanDetailsV3Response);
            return loanDetailsV3Response;
        }
        throw new LoanDetailsException(LoanDetailExceptionEnum.NO_SCOPE_PROVIDED.getErrorCode(),LoanDetailExceptionEnum.NO_SCOPE_PROVIDED.getErrorMessage());
    }

    public LoanDetailsV3Response getLoanDetailsWithoutScope(BasicDetailsDto merchant, String scope, Long applicationId, String token){
        if (null == scope) {
            ScopeDataArgs scopeDataArgs = ScopeDataArgs.builder()
                    .loanDetailsV3Request(new LoanDetailsV3Request())
                    .merchant(merchant)
                    .applicationId(applicationId)
                    .token(token)
                    .build();
            return renderStateWithoutScope.fetchLendingStateData(scopeDataArgs);
        } else {
            LoanDetailsV3Response loanDetailsV3Response = new LoanDetailsV3Response();
            ScopeDataArgs scopeDataArgs = ScopeDataArgs.builder()
                    .currentState(LendingViewStates.valueOf(scope))
                    .loanDetailsV3Request(new LoanDetailsV3Request())
                    .merchant(merchant)
                    .token(token)
                    .build();
            if (AGREEMENT_PAGE.equals(scopeDataArgs.getCurrentState())) {
                loanDetailsV3Response.setKycAddress("");
                LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
                if (!ObjectUtils.isEmpty(lendingApplicationKycDetails) && !ObjectUtils.isEmpty(lendingApplicationKycDetails.getAadharAddress())) {
                    loanDetailsV3Response.setKycAddress(lendingApplicationKycDetails.getAadharAddress());
                }
            }
            renderStateViaScope.fetchLendingStateData(scopeDataArgs);
            return populateResponseForRequestWithScope(merchant, scopeDataArgs.getLendingStateDTOForCurrPage(), loanDetailsV3Response, scopeDataArgs.getToken());
        }
    }

    public void saveApplicationViewState(LendingApplicationDetails lendingApplicationDetails, Long applicationId, LendingViewStates nextState){
        try{
            if(Objects.isNull(nextState)){
                log.info("LendingViewState or applicationId is empty {}, {}", nextState.name(), applicationId);
            }
            LendingApplicationDetails lendingApplicationDetails1 = lendingApplicationDetails;
            if(ObjectUtils.isEmpty(lendingApplicationDetails1)){
                lendingApplicationDetails1 = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
                if(ObjectUtils.isEmpty(lendingApplicationDetails1)){
                    log.info("LendingApplicationDetails entry not found for {}", applicationId);
                    return;
                }
            }
            if(nextState.name().equalsIgnoreCase(lendingApplicationDetails1.getApplicationViewState()))return;

            lendingApplicationDetails1.setApplicationViewState(nextState.name());
            lendingApplicationDetailsDao.save(lendingApplicationDetails1);
        }
        catch (Exception e){
            log.error("Exception while updating application View state for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public LoanDetailsV3Response populateResponseForRequestWithScope(BasicDetailsDto merchant, LendingStateDTO<?> lendingStateDTO, LoanDetailsV3Response loanDetailsV3Response, String token) {
        if(lendingStateDTO.getLendingViewStates()==null){
            loanDetailsV3Response.setInvalidState(true);
            return loanDetailsV3Response;
        }
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
                case PLAN_SELECTION_PAGE:
                    setPlanSelectionPageResponse((EligibilityStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case MASKED_MOBILE:
                    setMaskedMobile((MaskedMobileDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case AGREEMENT_PAGE:
                    setAgreementResponse((AgreementStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case LENDER_AGGREGATION:
                    setLenderAggregationResponse((LenderAggregationResponseDto) lendingStateDTO.getData(), loanDetailsV3Response);
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
                    setShopDetailsResponse((ShopDetailsStateDTO) lendingStateDTO.getData(), loanDetailsV3Response, token);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;
                case SHOP_PICTURES_PAGE:
                    setShopPicturesResponse(merchant, (ShopPicturesStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    if(Boolean.TRUE.equals(loanDetailsV3Response.getSkipShopPicture()) && Boolean.FALSE.equals(loanDetailsV3Response.getImageExist()))
                        loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    else
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
                    setLenderEvaluationResponse((LenderEvaluationStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
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

                case MODIFIED_OFFER:
                    setModifiedOfferResponse((ModifiedOfferStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;

                case BL_DOC_UPLOAD_PAGE:
                    setBLDocUploadStageResponse((BLDocUploadStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                    return loanDetailsV3Response;

                case UDYAM_REGISTRATION_PAGE:
                    setUdyamRegistrationPageResponse((UdyamRegistrationStateDTO) lendingStateDTO.getData(), loanDetailsV3Response);
                    loanDetailsV3Response.setNextPage(lendingStateDTO.getLendingViewStates().name());
                default:

            }
        } catch (Exception e) {
            log.error("Exception while casting data for {} {} {}", lendingStateDTO, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return loanDetailsV3Response;
    }

    private static void setPlanSelectionPageResponse(@NotNull EligibilityStateDTO data, LoanDetailsV3Response loanDetailsV3Response) {
        if(data.getEmiEligibility() != null){
            EmiEligibility emiEligibility = data.getEmiEligibility();
            loanDetailsV3Response.setEmiLoanAmount(emiEligibility.getEmiLoanAmount());
            loanDetailsV3Response.setEmiRejected(emiEligibility.getEmiRejected());
            loanDetailsV3Response.setRejectReason(emiEligibility.getRejectReason());
        }
        if(data.getEligibility()!=null){
            loanDetailsV3Response.setLoanAmount(data.getEligibility().getLoanAmount());
        }
    }

    private static void setMaskedMobile(MaskedMobileDTO maskedMobileDTO, LoanDetailsV3Response loanDetailsV3Response) {
        loanDetailsV3Response.setMaskedMobileList(maskedMobileDTO.getMaskedMobileList());
        loanDetailsV3Response.setRetryLimitExhausted(maskedMobileDTO.isRetryLimitExhausted());
    }

    private static void setKfsResponse(KFSStateDTO kfsStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setLoanApplication(kfsStateDTO.getLoanApplication());
        loanDetailsV3Response.setTopupLoanApplication(kfsStateDTO.getTopupLoanApplication());
        loanDetailsV3Response.setRepeatLoan(kfsStateDTO.isRepeatLoan());
        loanDetailsV3Response.setMobile(kfsStateDTO.getMobile());
        loanDetailsV3Response.setUpiAutoPayMandateStatus(kfsStateDTO.getUpiAutoPayMandateStatus());
        loanDetailsV3Response.setUpiAutoPayEligible(kfsStateDTO.getUpiAutoPayEligible());
        loanDetailsV3Response.setAgreementDone(kfsStateDTO.getAgreementDone());
        loanDetailsV3Response.setLender(kfsStateDTO.getLender());
        loanDetailsV3Response.setMerchantId(kfsStateDTO.getMerchantId());
    }

    private static void setKycResponse(KYCStateDTO kycStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setKycStatus(kycStateDTO.getKycStatus());
        loanDetailsV3Response.setKycDeeplink(kycStateDTO.getDeeplink());
        loanDetailsV3Response.setShowKycPage(kycStateDTO.getShowKycPage());
        loanDetailsV3Response.setIsSelfieResumit(kycStateDTO.isSelfieResumit());
        loanDetailsV3Response.setLender(kycStateDTO.getLender());
        loanDetailsV3Response.setLenderKycPipe(kycStateDTO.isLenderKycPipe());
        loanDetailsV3Response.setMerchantId(kycStateDTO.getMerchantId());

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
        loanDetailsV3Response.setMerchantId(eligibilityStateDTO.getMerchantId());
    }


    private static void setKYCRouteToEligibilityResponse(KYCRTEDto kycStateDTO, LoanDetailsV3Response loanDetailsV3Response)
    {
        loanDetailsV3Response.setKycStatus(kycStateDTO.getKycStatus());
        loanDetailsV3Response.setKycDeeplink(kycStateDTO.getDeepLink());
        loanDetailsV3Response.setShowKycPage(kycStateDTO.getShowKycPage());
        loanDetailsV3Response.setMerchantId(kycStateDTO.getMerchantId());
    }

    private static void setAgreementResponse(AgreementStateDTO agreementStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setApplicationId(agreementStateDTO.getApplicationId());
        applicationDetails.setLoanAmount(agreementStateDTO.getLoanAmount());
        applicationDetails.setEnachBank(agreementStateDTO.getEnachBank());
        applicationDetails.setIsInsured(agreementStateDTO.getIsInsured());
        applicationDetails.setLoanInsurances(agreementStateDTO.getLoanInsurances());
        applicationDetails.setApr(agreementStateDTO.getApr());
        applicationDetails.setExternalLoanId(agreementStateDTO.getExternalLoanId());
        if(agreementStateDTO.isTopup())loanDetailsV3Response.setTopupLoanApplication(applicationDetails);
        else loanDetailsV3Response.setLoanApplication(applicationDetails);

        loanDetailsV3Response.setLender(agreementStateDTO.getLender());
        loanDetailsV3Response.setMerchantId(agreementStateDTO.getMerchantId());
        loanDetailsV3Response.setInterestRate(agreementStateDTO.getInterestRate());
        loanDetailsV3Response.setAnnualRoi(agreementStateDTO.getAnnualRoi());
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
        loanDetailsV3Response.setIsAadhaarAddressVerified(agreementStateDTO.getIsAadhaarAddressVerified());
        loanDetailsV3Response.setLoanPurpose(agreementStateDTO.getLoanPurpose());

    }

    private void setShopDetailsResponse(ShopDetailsStateDTO shopDetailsStateDTO, LoanDetailsV3Response loanDetailsV3Response, String token) {
        log.info("shopDetailsStateDTO: {}", shopDetailsStateDTO);
        if (shopDetailsStateDTO == null || loanDetailsV3Response == null) {
            log.warn("ShopDetailsStateDTO or LoanDetailsV3Response is null. Skipping response population.");
            return;
        }

        if (shopDetailsStateDTO.getBusinessName() == null) {
            populateBusinessName(shopDetailsStateDTO);
        }
        log.info("Populating shop details {}", shopDetailsStateDTO);

        populateBasicShopDetails(shopDetailsStateDTO, loanDetailsV3Response);
        populateLoanApplicationDetails(shopDetailsStateDTO, loanDetailsV3Response);
        //populateLoanApplicationAddressDetails(loanDetailsV3Response.getLoanApplication(), token, shopDetailsStateDTO);

        log.info("Shop details response populated successfully {}", loanDetailsV3Response);
    }

    private void populateBusinessName(ShopDetailsStateDTO shopDetailsStateDTO) {
        Long merchantId = shopDetailsStateDTO.getMerchantId();
        Long applicationId = shopDetailsStateDTO.getApplicationId();
        log.info("Populating business name for Merchant ID: {}", merchantId);

        if (merchantId == null) {
            log.warn("Merchant ID is null. Cannot populate business name.");
            return;
        }

        if (applicationId == null) {
            log.info("applicationId is null for merchant: {}", merchantId);
            return;
        } else {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if (lendingApplication != null) {
                LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(applicationId);
                if (LoanSegment.FRESH.name().equalsIgnoreCase(lendingRiskVariablesSnapshot.getLoanSegment()) ||
                        LoanSegment.REPEAT.name().equalsIgnoreCase(lendingRiskVariablesSnapshot.getLoanSegment())) {
                    log.info("Business name found in lending application: {}", lendingApplication.getBusinessName());
                    populateBusinessNameFromPreviousApplications(shopDetailsStateDTO, lendingApplication);
                }
            }
            if(shopDetailsStateDTO.getBusinessName() == null) {
                log.info("Business name not found in lending application. Fetching from MerchantService for Merchant ID: {}", merchantId);
                MerchantDetailsDto merchantDetails = merchantService.fetchMerchantDetails(merchantId);
                shopDetailsStateDTO.setBusinessName(merchantDetails.getMerchantDetail().getBussinessName());
            }
        }
        LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        lendingMerchantDetails.setBusinessName(shopDetailsStateDTO.getBusinessName());
        lendingMerchantDetailsDao.save(lendingMerchantDetails);
        log.info("Business name populated: {}", shopDetailsStateDTO.getBusinessName());
    }

    private void populateBusinessNameFromPreviousApplications(ShopDetailsStateDTO shopDetailsStateDTO, LendingApplication lendingApplication) {
        List<LendingApplication> previousApplications = lendingApplicationDao.fetchLatestOpenApplication(lendingApplication.getMerchantId());
        if (!CollectionUtils.isEmpty(previousApplications)) {
            for (LendingApplication previousApplication : previousApplications) {
                if (previousApplication.getBusinessName() != null && !previousApplication.getId().equals(lendingApplication.getId())) {
                    shopDetailsStateDTO.setBusinessName(previousApplication.getBusinessName());
                    break;
                }
            }
        }
    }

    private static void populateBasicShopDetails(ShopDetailsStateDTO shopDetailsStateDTO, LoanDetailsV3Response loanDetailsV3Response) {
        loanDetailsV3Response.setDummyMerchant(shopDetailsStateDTO.isDummyMerchant());
        loanDetailsV3Response.setBusinessName(shopDetailsStateDTO.getBusinessName());
        loanDetailsV3Response.setBusinessCategory(shopDetailsStateDTO.getBusinessCategory());
        loanDetailsV3Response.setBusinessSubCategory(shopDetailsStateDTO.getBusinessSubCategory());
        loanDetailsV3Response.setPincode(shopDetailsStateDTO.getPincode());
        loanDetailsV3Response.setLender(shopDetailsStateDTO.getLender());
        loanDetailsV3Response.setMerchantId(shopDetailsStateDTO.getMerchantId());
        loanDetailsV3Response.setIsAggregationFlowApplicable(shopDetailsStateDTO.getIsAggregationFlowApplicable());
        log.info("Basic shop details populated: {}", shopDetailsStateDTO);
        log.info("loanDetailsV3Response: {}", loanDetailsV3Response);
    }

    private static void populateLoanApplicationDetails(ShopDetailsStateDTO shopDetailsStateDTO, LoanDetailsV3Response loanDetailsV3Response) {
        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setApplicationId(shopDetailsStateDTO.getApplicationId());
        applicationDetails.setApplicationStatus(shopDetailsStateDTO.getApplicationStatus());
        applicationDetails.setRejectReason(shopDetailsStateDTO.getResubmitReason());
        loanDetailsV3Response.setLoanApplication(applicationDetails);

        if (shopDetailsStateDTO.getResubmitDone() != null) {
            loanDetailsV3Response.setResubmitDone(shopDetailsStateDTO.getResubmitDone());
        }
    }

    private void populateLoanApplicationAddressDetails(LoanApplicationDetailsV3 loanApplicationDetails, String token, ShopDetailsStateDTO shopDetailsStateDTO) {
        if (loanApplicationDetails == null) {
            log.warn("LoanApplicationDetailsV3 is null. Skipping address details population.");
            return;
        }

        AddressDetails addressDetails = fetchAddressFromLendingApplication(loanApplicationDetails.getApplicationId(), shopDetailsStateDTO.getMerchantId());
        if (ObjectUtils.isEmpty(addressDetails)) {
            log.info("Address details not found in Lending Application. Fetching from Merchant Service for Application ID: {}", loanApplicationDetails.getApplicationId());
            addressDetails = fetchAddressFromMerchantService(loanApplicationDetails.getApplicationId(), token, shopDetailsStateDTO);
        }

        if (addressDetails != null) {
            loanApplicationDetails.setAddressDetails(addressDetails);
            log.info("Address details populated successfully for Application ID: {}", loanApplicationDetails.getApplicationId());
        } else {
            log.warn("No address details found for Application ID: {}", loanApplicationDetails.getApplicationId());
        }
    }

    private AddressDetails fetchAddressFromLendingApplication(Long applicationId, Long merchantId) {
        LendingApplication lendingApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchantId, ApplicationStatus.APPROVED.name().toLowerCase());
        log.info("fetching address from Lending Application for Application ID: {} and lendingApplication:{}", applicationId, lendingApplication);
        if (lendingApplication != null && isAddressComplete(lendingApplication)) {
            AddressDetails addressDetails = new AddressDetails();
            addressDetails.setPincode(String.valueOf(lendingApplication.getPincode()));
            addressDetails.setArea(lendingApplication.getArea());
            addressDetails.setLandmark(lendingApplication.getLandmark());
            addressDetails.setAddress2(lendingApplication.getStreetAddress());
            addressDetails.setAddress1(lendingApplication.getShopNumber());
            addressDetails.setLandmark(lendingApplication.getLandmark());
            addressDetails.setCity(lendingApplication.getCity());
            addressDetails.setState(lendingApplication.getState());
            return addressDetails;
        }
        return null;
    }

    private AddressDetails fetchAddressFromMerchantService(Long applicationId, String token, ShopDetailsStateDTO shopDetailsStateDTO) {
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndStatus(applicationId, ApplicationStatus.DRAFT.name().toLowerCase());
        log.info("Fetching address from Merchant Service for Application ID: {} and lendingApplication:{}", applicationId, lendingApplication);

        if (!ObjectUtils.isEmpty(lendingApplication)) {
            MerchantDto merchantDto = new MerchantDto();
            merchantDto.setMerchantId(lendingApplication.getMerchantId());
            ListMerchantAddressResponseDto addressResponse = merchantService.getAddress(merchantDto, token);
            log.info("Address response from Merchant Service: {}", addressResponse);

            if (!ObjectUtils.isEmpty(addressResponse) && !CollectionUtils.isEmpty(addressResponse.getAddress())) {
                // Filter addresses that have required fields and matching pincode
                Optional<Long> validAddressId = addressResponse.getAddress().stream()
                        .filter(address -> address.getAddress() != null &&
                                address.getAdd2() != null &&
                                address.getArea() != null &&
                                address.getPincode() != null &&
                                address.getPincode().equals(shopDetailsStateDTO.getPincode()))
                        .max(Comparator.comparing(ListMerchantAddressResponseDto.Address::getId))
                        .map(ListMerchantAddressResponseDto.Address::getId);
                log.info("Valid address ID with matching pincode: {}", validAddressId);

                if (!validAddressId.isPresent()) {
                    log.info("No valid address found with matching pincode: {}", shopDetailsStateDTO.getPincode());
                    return null;
                }

                ListMerchantAddressResponseDto.Address latestAddress = addressResponse.getAddress().stream()
                        .filter(address -> address.getId().equals(validAddressId.get()))
                        .findFirst()
                        .orElse(null);
                log.info("Latest address: {}", latestAddress);

                if (!ObjectUtils.isEmpty(latestAddress)) {
                    AddressDetails addressDetails = new AddressDetails();
                    addressDetails.setPincode(latestAddress.getPincode());
                    addressDetails.setAddress2(latestAddress.getAdd2());
                    addressDetails.setAddress1(latestAddress.getAddress());
                    addressDetails.setLandmark(latestAddress.getLandmark());
                    addressDetails.setCity(latestAddress.getCity());
                    addressDetails.setState(latestAddress.getState());
                    addressDetails.setArea(latestAddress.getArea());
                    return addressDetails;
                }
            }
        }
        return null;
    }

    private boolean isAddressComplete(LendingApplication lendingApplication) {
        return lendingApplication.getPincode() != null &&
                lendingApplication.getStreetAddress() != null &&
                lendingApplication.getShopNumber() != null &&
                lendingApplication.getArea() != null &&
                lendingApplication.getCity() != null &&
                lendingApplication.getState() != null;
    }

    /**
     * Sets shop pictures response data based on merchant details and shop pictures state.
     * Handles validation, shop picture skipping logic, and populates response accordingly.
     *
     * @param merchant The merchant's basic details
     * @param shopPicturesStateDTO The shop pictures state data
     * @param loanDetailsV3Response The loan details response to be populated
     */
    private void setShopPicturesResponse(BasicDetailsDto merchant, ShopPicturesStateDTO shopPicturesStateDTO, LoanDetailsV3Response loanDetailsV3Response) {
        if (merchant == null || shopPicturesStateDTO == null || loanDetailsV3Response == null) {
            log.error("Invalid input parameters for shop pictures response. Merchant: {}, ShopPicturesStateDTO: {}, Response: {}",
                    merchant == null ? "null" : merchant.getId(),
                    shopPicturesStateDTO == null ? "null" : shopPicturesStateDTO.getApplicationId(),
                    loanDetailsV3Response == null ? "null" : "not null");
            return;
        }

        Long merchantId = shopPicturesStateDTO.getMerchantId();
        Long applicationId = shopPicturesStateDTO.getApplicationId();

        log.info("Processing shop pictures for merchantId: {}, applicationId: {}", merchantId, applicationId);

        try {
            populateBasicShopDetailsInResponse(shopPicturesStateDTO, loanDetailsV3Response);

            loanDetailsV3Response.setSkipShopPicture(false);
            loanDetailsV3Response.setImageExist(false);

            if (checkExistingImagesForApplication(merchant, shopPicturesStateDTO, loanDetailsV3Response)) {
                log.info("Found valid existing shop images for merchantId: {}, applicationId: {}", merchantId, applicationId);
                return;
            }

            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            Date startOfDate = Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());

            List<LendingApplication> lendingApplications = lendingApplicationDao.findByLenderAndCreatedAtGreaterThanEqual(
                     lendersToSkipShopPicture, startOfDate);

            int todayApplicationsCount = lendingApplications != null ? lendingApplications.size() : 0;
            log.info("Found {} applications for lender {} created today for merchantId: {}",
                    todayApplicationsCount, lendersToSkipShopPicture, merchantId);


            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if (shouldSkipShopPicture && lendingApplication != null &&
                    lendersToSkipShopPicture.contains(lendingApplication.getLender()) && todayApplicationsCount >= skipPictureThreshold) {

                processLenderSpecificShopPictureRules(merchant, shopPicturesStateDTO, loanDetailsV3Response, lendingApplication);
            } else {
                log.info("Shop picture skipping not applicable for merchantId: {}, lender: {}",
                        merchantId, lendingApplication != null ? lendingApplication.getLender() : "unknown");
            }
        } catch (Exception e) {
            loanDetailsV3Response.setSkipShopPicture(false);
            log.error("Exception processing shop pictures for merchantId: {}, applicationId: {}. Error: {}",
                    merchantId, applicationId, e.getMessage(), e);
        } finally {
            updateLendingShopDocumentsIsSkipped(merchantId, applicationId, loanDetailsV3Response);
        }
    }

    /**
     * Populates basic shop picture details in the response
     */
    private void populateBasicShopDetailsInResponse(ShopPicturesStateDTO dto, LoanDetailsV3Response response) {
        response.setDummyMerchant(dto.isDummyMerchant());
        response.setLender(dto.getLender());
        response.setLenderKycPipe(dto.getLenderKycPipe());
        response.setMerchantId(dto.getMerchantId());

        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setApplicationId(dto.getApplicationId());
        applicationDetails.setLenderAssc(dto.getLenderAssc());
        response.setLoanApplication(applicationDetails);
    }

    /**
     * Checks for existing images in the current application
     * @return true if valid images found and response was updated, false otherwise
     */
    private boolean checkExistingImagesForApplication(BasicDetailsDto merchant, ShopPicturesStateDTO dto, LoanDetailsV3Response response) {
        if (merchant == null || dto == null || dto.getApplicationId() == null || dto.getMerchantId() == null) {
            return false;
        }

        ImageProofRequestDto imageProofRequestDto = new ImageProofRequestDto();
        imageProofRequestDto.setApplicationId(String.valueOf(dto.getApplicationId()));

        LendingApplication application = lendingApplicationDao.findByIdAndMerchantId(dto.getApplicationId(), dto.getMerchantId());
        if (application == null) {
            return false;
        }

        List<ImageProofResponseDto.Proof> proofs = imageURLService.fetchImageUrl(merchant, application, imageProofRequestDto);

        log.info("Found {} proof images for merchantId: {}", proofs != null ? proofs.size() : 0, dto.getMerchantId());
        for (ImageProofResponseDto.Proof proof : proofs) {q
            log.info("Image details: proof_type={}, proof_urls={}",
                    proof.getProofType(),
                    proof.getProofUrls());
        }

        List<LendingShopDocuments> lendingShopDocuments = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(
                dto.getMerchantId(), dto.getApplicationId());

        if (!CollectionUtils.isEmpty(proofs) && proofs.size() >= 2 &&
                !CollectionUtils.isEmpty(lendingShopDocuments) && lendingShopDocuments.size() >= 2 &&
                lendingShopDocuments.stream().allMatch(doc -> Boolean.TRUE.equals(doc.getIsSkipped()))) {
            log.info("Valid shop images found for merchantId: {}", dto.getMerchantId());
            response.setSkipShopPicture(true);
            response.setImageExist(true);
            return true;
        }
        return false;
    }

    /**
     * Process lender-specific shop picture rules
     *
     * @return
     */
    public Boolean processLenderSpecificShopPictureRules(BasicDetailsDto merchant, ShopPicturesStateDTO dto,
                                                         LoanDetailsV3Response response, LendingApplication application) {
        // Validate input parameters
        if (merchant == null || dto == null || response == null || application == null) {
            log.warn("Invalid inputs for processLenderSpecificShopPictureRules");
            return false;
        }

        LendingRiskVariablesSnapshot riskSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(application.getId());
        if (riskSnapshot == null || riskSnapshot.getLoanSegment() == null) {
            log.info("No risk variables or loan segment found for applicationId: {}", application.getId());
            return false;
        }

        String loanSegment = riskSnapshot.getLoanSegment();
        log.info("Processing loan segment: {} for merchantId: {}", loanSegment, dto.getMerchantId());

        Optional<LendingApplication> referenceApplication = Optional.empty();

        if (LoanSegment.REPEAT.name().equalsIgnoreCase(loanSegment)) {
            Long latestStpApplicationId = findLatestStpApplication(merchant.getId());
            if (latestStpApplicationId != null) {
                referenceApplication = lendingApplicationDao.findById(latestStpApplicationId);
            }
            else{
                log.info("No STP application found for REPEAT loan segment, using current application");
            }
        } else if (LoanSegment.FRESH.name().equalsIgnoreCase(loanSegment)) {
            // For FRESH loans, check if any past approved applications exist
            List<LendingApplication> approvedApplications = lendingApplicationDao.findAllByMerchantIdAndStatus(
                    merchant.getId(), ApplicationStatus.APPROVED.name().toLowerCase());

            if (!CollectionUtils.isEmpty(approvedApplications)) {
                log.info("Found {} approved applications for FRESH loan segment, merchantId: {}",
                        approvedApplications.size(), merchant.getId());

                // Get the most recent approved application
                Optional<LendingApplication> latestApprovedApp = approvedApplications.stream()
                        .filter(app -> app != null && app.getId() != null && app.getCreatedAt() != null)
                        .max(Comparator.comparing(LendingApplication::getCreatedAt));

                if (latestApprovedApp.isPresent()) {
                    referenceApplication = latestApprovedApp;
                    log.info("Using latest approved application: {} for FRESH loan",
                            latestApprovedApp.get().getId());
                }
            }

            // If no past approved applications, use current application
            if (!referenceApplication.isPresent()) {
                referenceApplication = Optional.of(application);
                log.info("No past approved applications found, using current application");
            }
        }


        Duration validDuration = getDurationBasedOnLoanType(loanSegment);

        if (!referenceApplication.isPresent()) {
            log.info("No reference application found checking from Ckyc for merchantId: {}", merchant.getId());
            checkCKycDocsForShopPictures(merchant, dto, response, loanSegment, validDuration);
            return response.getSkipShopPicture();
        }
        response.setSkipShopPicture(false);

        LendingApplication refApp = referenceApplication.get();

        List<LendingShopDocuments> shopDocs = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(
                refApp.getMerchantId(), refApp.getId());

        if (!CollectionUtils.isEmpty(shopDocs) &&
                isDocumentsRecent(shopDocs, validDuration, loanSegment) &&
                isValidShopDocuments(response,shopDocs, dto, refApp.getId())) {

            RequestDTO<UploadDocumentRequestDTO> uploadRequest = new RequestDTO<>();
            populateUploadDocumentRequest(shopDocs, uploadRequest, refApp.getId(), application.getId());

            if (uploadRequest.getPayload() != null) {
                ApiResponse<UploadDocumentResponseDTO> uploadDocumentResponse = uploadDocumentService.uploadDocument(merchant, uploadRequest, true);
                if (uploadDocumentResponse != null && uploadDocumentResponse.isSuccess() && uploadDocumentResponse.getData() != null) {
                    response.setSkipShopPicture(true);
                    log.info("Shop picture skipped for merchantId: {}, applicationId: {}",
                            dto.getMerchantId(), dto.getApplicationId());
                }
                log.info("Shop picture skipped for merchantId: {}, applicationId: {}",
                        dto.getMerchantId(), dto.getApplicationId());
            }
        }
        // If we get here, either no valid shop documents were found or they failed validation
        // Fall back to checking CKYC documents for both FRESH and REPEAT loans
        if (Boolean.FALSE.equals(response.getSkipShopPicture())) {
            log.info("No valid shop documents found or validation failed, checking CKYC documents");
            checkCKycDocsForShopPictures(merchant, dto, response, loanSegment, validDuration);
        }
        return response.getSkipShopPicture();
    }

    /**
     * Find the latest STP application for a merchant
     *
     * @param merchantId Merchant ID
     * @return application ID or null if not found
     */
    private Long findLatestStpApplication(Long merchantId) {
        if (merchantId == null) {
            log.warn("Cannot find STP application with null merchantId");
            return null;
        }

        List<LendingApplication> approvedApplications = lendingApplicationDao.findAllByMerchantIdAndStatus(
                merchantId, ApplicationStatus.APPROVED.name().toLowerCase());

        if (CollectionUtils.isEmpty(approvedApplications)) {
            log.info("No approved applications found for merchantId: {}", merchantId);
            return null;
        }

        Long latestStpApplicationId = null;
        Date latestTimestamp = null;

        for (LendingApplication app : approvedApplications) {
            if (app == null || app.getId() == null || app.getCreatedAt() == null) {
                continue;
            }

            LendingRiskVariablesSnapshot riskSnapshot =
                    lendingRiskVariablesSnapshotDao.findByApplicationId(app.getId());

            if (riskSnapshot != null && Boolean.TRUE.equals(riskSnapshot.getStpFlag())) {
                // For the first STP application or if this one is more recent
                if (latestTimestamp == null || app.getCreatedAt().after(latestTimestamp)) {
                    latestStpApplicationId = app.getId();
                    latestTimestamp = app.getCreatedAt();
                    log.debug("Found newer STP application: {} with timestamp: {}",
                            latestStpApplicationId, latestTimestamp);
                }
            }
        }

        log.info("Latest STP application for merchantId: {}: {}",
                merchantId, latestStpApplicationId != null ? latestStpApplicationId : "Not found");
        return latestStpApplicationId;
    }

    /**
     * Check CKYC documents for shop pictures
     */
    private void checkCKycDocsForShopPictures(BasicDetailsDto merchant, ShopPicturesStateDTO dto,
                                              LoanDetailsV3Response response, String loanSegment, Duration validDuration) {

        Optional<CKycDocDetailsResponseDto> cKycDocDetails = merchantService.getCKycDocDetails(dto.getMerchantId());
        if (!cKycDocDetails.isPresent() || cKycDocDetails.get().getData() == null ||
                CollectionUtils.isEmpty(cKycDocDetails.get().getData().getDocsList())) {
            log.info("No valid CKYC documents found for merchantId: {}", dto.getMerchantId());
            return;
        }

        List<CKycDocDetailsResponseDto.Docs> docsList = cKycDocDetails.get().getData().getDocsList();
        if (docsList == null || docsList.isEmpty()) {
            log.info("No CKYC documents found for merchantId: {}", dto.getMerchantId());
            return;
        }

        for (CKycDocDetailsResponseDto.Docs doc : docsList) {
            if (doc == null || doc.getCreatedAt() == null ||
                    !isDocumentsRecentCreatedAt(validDuration, loanSegment, doc.getCreatedAt())) {
                log.info("CKYC document not recent enough for merchantId: {}", dto.getMerchantId());
                return;
            }
        }

        List<LendingShopDocuments> tempDocList = new ArrayList<>();
        if (isValidCKycDocs(cKycDocDetails.get(), dto, tempDocList)) {
            RequestDTO<UploadDocumentRequestDTO> requestDTO = new RequestDTO<>();
            populateUploadDocumentRequest(tempDocList, requestDTO, dto.getApplicationId(), dto.getApplicationId());
            ApiResponse<UploadDocumentResponseDTO> uploadDocumentResponse = uploadDocumentService.uploadDocument(merchant, requestDTO, true);
            if (uploadDocumentResponse != null && uploadDocumentResponse.isSuccess() && uploadDocumentResponse.getData() != null) {
                response.setSkipShopPicture(true);
                log.info("Shop picture skipped for merchantId: {}, applicationId: {}",
                        dto.getMerchantId(), dto.getApplicationId());
            }
            log.info("Using CKYC documents for shop pictures for merchantId: {}", dto.getMerchantId());
        }
    }

    public void updateLendingShopDocumentsIsSkipped(Long merchantId, Long applicationId, LoanDetailsV3Response loanDetailsV3Response) {
        try {
            // Only update if skipShopPicture is true and imageExist is false
            if (Boolean.TRUE.equals(loanDetailsV3Response.getSkipShopPicture()) &&
                    Boolean.FALSE.equals(loanDetailsV3Response.getImageExist())) {
                boolean skipValue = true;
                log.info("Updating isSkipped to {} for LendingShopDocuments with applicationId: {}", skipValue, applicationId);
                List<LendingShopDocuments> documents = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchantId, applicationId);
                if (!CollectionUtils.isEmpty(documents)) {
                    for (LendingShopDocuments document : documents) {
                        document.setIsSkipped(skipValue);
                    }
                    lendingShopDocumentsDao.saveAll(documents);
                    log.info("Updated isSkipped to {} for all LendingShopDocuments with applicationId: {}", skipValue, applicationId);
                } else {
                    log.warn("No LendingShopDocuments found for applicationId: {}", applicationId);
                }
            } else {
                log.info("No update required for LendingShopDocuments: skipShopPicture={}, imageExist={}",
                        loanDetailsV3Response.getSkipShopPicture(), loanDetailsV3Response.getImageExist());
            }
        } catch (Exception e) {
            log.error("Error while updating isSkipped for LendingShopDocuments with applicationId: {}. Error: {}", applicationId, e.getMessage(), e);
        }
    }

    private Duration getDurationBasedOnLoanType(String loanType) {
        if (LoanSegment.FRESH.name().equalsIgnoreCase(loanType)) {
            return Duration.ofDays(365); // 12 months
        } else if (LoanSegment.REPEAT.name().equalsIgnoreCase(loanType)) {
            return Duration.ofDays(730); // 24 months
        }
        return Duration.ZERO;
    }

    private boolean isDocumentsRecent(List<LendingShopDocuments> docs, Duration duration, String loanType) {
        if (docs == null || docs.isEmpty()) return false;

        Instant threshold = Instant.now().minus(duration);

        for (LendingShopDocuments doc : docs) {
            Instant updatedAt = doc.getUpdatedAt().toInstant();
            if (updatedAt.isBefore(threshold)) {
                return false;
            }
        }
        return true;
    }

    private boolean isDocumentsRecentCreatedAt(Duration duration, String loanType, Date createdAt) {
        if (createdAt == null || (!LoanSegment.FRESH.name().equalsIgnoreCase(loanType)
                && !LoanSegment.REPEAT.name().equalsIgnoreCase(loanType))) {
            return false;
        }

        Instant timestampToCheck = createdAt.toInstant();
        log.info("loanType, createdAt, timestampToCheck: {}, {}, {}", loanType, createdAt, timestampToCheck);
        return timestampToCheck.isAfter(Instant.now().minus(duration));
    }

    private boolean isValidShopDocuments(LoanDetailsV3Response response, List<LendingShopDocuments> docs, ShopPicturesStateDTO dto, Long approvedApplicationId) {
        if (docs.size() < 2) {
            log.info("Insufficient shop documents for merchantId: {}", dto.getMerchantId());
            response.setSkipShopPicture(false);
            return false;
        }

        boolean isDistanceValid = docs.stream()
                .anyMatch(doc -> {
                    Double distance = imageURLService.calculateDistanceBetweenInferredLocationAndShopDocumentLocation(doc, dto.getMerchantId());
                    return distance != null && distance <= sidThreshold;
                });

        if (!isDistanceValid) {
            log.info("Distance check failed for merchantId: {}", dto.getMerchantId());
            response.setSkipShopPicture(false);
            return false;
        }

        List<Long> fieldIds = Arrays.asList(38L, 39L);

        List<LmsFieldValues> fieldValues = lmsFieldValuesDao.findByLendingApplicationIdAndFieldIdIn(approvedApplicationId, fieldIds);

        if (CollectionUtils.isEmpty(fieldValues)) {
            log.info("No LMS field values found for applicationId: {}, merchantId: {}", approvedApplicationId, dto.getMerchantId());
            response.setSkipShopPicture(false);
            return false;
        }

        boolean hasField38 = fieldValues.stream().anyMatch(f -> f.getFieldId() == 38L);
        boolean hasField39 = fieldValues.stream().anyMatch(f -> f.getFieldId() == 39L);

        boolean isField38Valid = fieldValues.stream()
                .filter(f -> f.getFieldId() == 38L)
                .allMatch(f -> {
                    String val = f.getFieldDropdownValue();
                    log.info("FieldId: 38, Value: {}", val);
                    return val != null && ALLOWED_SHOP_STRUCTURE_TYPES.contains(val.toLowerCase());
                });
        log.info("FieldId: 38 shopStructureType valid: {}", isField38Valid);

        boolean isField39Valid = fieldValues.stream()
                .filter(f -> f.getFieldId() == 39L)
                .allMatch(f -> {
                    String val = f.getFieldDropdownValue();
                    log.info("FieldId: 39, Value: {}", val);
                    return val != null && val.equalsIgnoreCase("yes");
                });
        log.info("FieldId: 39 isShopOperational valid: {}", isField38Valid);

        boolean allFieldsValid = hasField38 && hasField39 && isField38Valid && isField39Valid;
        log.info("All LMS field values valid: {}", allFieldsValid);
        return allFieldsValid;
    }

    private void populateUploadDocumentRequest(List<LendingShopDocuments> docs, RequestDTO<UploadDocumentRequestDTO> uploadDocumentRequestDTORequestDTO, Long approvedApplicationId, Long applicationId) {
        UploadDocumentRequestDTO requestDTO = new UploadDocumentRequestDTO();
        if (docs == null || docs.isEmpty()) {
            log.warn("No documents provided to populate upload document request for applicationId: {}", applicationId);
            return;
        }

        List<LendingShopDocuments> validDocs = docs.stream()
                .filter(doc -> approvedApplicationId.equals(doc.getApplicationId()))
                .collect(Collectors.toList());

        if (validDocs.isEmpty()) {
            log.warn("No valid documents found for applicationId: {}. Provided documents belong to a different application.", approvedApplicationId);
            return;
        }

        List<UploadDocumentRequestDTO.Document> documents = new ArrayList<>();
        log.info("Populating upload document request for approvedApplicationId: {} with valid docs: {} for applicationId:{}", approvedApplicationId, validDocs, applicationId);

        validDocs.stream()
                .filter(doc -> ShopPhotoProofType.FRONT.getValue().equalsIgnoreCase(doc.getProofType()))
                .findFirst()
                .ifPresent(doc -> {
                    UploadDocumentRequestDTO.Document document = new UploadDocumentRequestDTO.Document();
                    document.setProofType(doc.getProofType());
                    document.setProof(Collections.singletonList(doc.getProofFrontSide()));
                    document.setChangeFlag(true);
                    document.setSinglePageDocument(true);
                    documents.add(document);
                });
        log.info("Adding shop-front document for shop-front: {} for applicationId: {}", documents, applicationId);

        validDocs.stream()
                .filter(doc -> ShopPhotoProofType.STOCK.getValue().equalsIgnoreCase(doc.getProofType()))
                .findFirst()
                .ifPresent(doc -> {
                    UploadDocumentRequestDTO.Document document = new UploadDocumentRequestDTO.Document();
                    document.setProofType(doc.getProofType());
                    document.setProof(Collections.singletonList(doc.getProofFrontSide()));
                    document.setChangeFlag(true);
                    document.setSinglePageDocument(true);
                    documents.add(document);
                });

        log.info("Adding shop-stock document for shop-stock: {} for applicationId: {}", documents, applicationId);

        MetaDTO metaDTO = new MetaDTO();
        if (validDocs.stream().allMatch(doc -> doc.getLatitude() != null && doc.getLongitude() != null)) {
            metaDTO.setIp(validDocs.get(0).getIp());
            metaDTO.setLattitude(validDocs.get(0).getLatitude());
            metaDTO.setLongitude(validDocs.get(0).getLongitude());
        } else {
            log.warn("Not all validDocs have latitude and longitude. Skipping MetaDTO population.");
            return;
        }
        uploadDocumentRequestDTORequestDTO.setMeta(metaDTO);
        requestDTO.setApplicationId(applicationId);
        requestDTO.setDocuments(documents);
        log.info("Populated upload document request: {}", requestDTO);
        uploadDocumentRequestDTORequestDTO.setPayload(requestDTO);

        if (documents.isEmpty()) {
            log.warn("No valid documents found to populate requestDTO for applicationId: {}", applicationId);
        } else {
            log.info("Successfully populated requestDTO for applicationId: {}", applicationId);
        }
    }

    private boolean isValidCKycDocs(CKycDocDetailsResponseDto ckyc, ShopPicturesStateDTO dto, List<LendingShopDocuments> lendingShopDocumentsList) {
        CKycDocDetailsResponseDto.DocumentData data = ckyc.getData();
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(dto.getApplicationId(), dto.getMerchantId());
        if (data == null || data.getDocsList() == null || data.getDocsList().size() < 2) {
            log.info("CKYC documents not sufficient for merchantId: {}", dto.getMerchantId());
            return false;
        }

        List<CKycDocDetailsResponseDto.Docs> shopPictureDocs = data.getDocsList().stream()
                .filter(doc -> doc.getDocType() != null &&
                        (doc.getDocType().contains("SHOP_PICTURE_1") || doc.getDocType().contains("SHOP_PICTURE_2")) &&
                        doc.getStatus() != null && "APPROVED".equalsIgnoreCase(doc.getStatus()))
                .sorted(Comparator.comparing(CKycDocDetailsResponseDto.Docs::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        if(shopPictureDocs.isEmpty() || shopPictureDocs.size() < 2) {
            log.info("No shop picture documents found for merchantId: {}", dto.getMerchantId());
            return false;
        }

        log.info("Filtered shop picture documents: {} for merchantId: {}", shopPictureDocs, dto.getMerchantId());

        boolean hasValidDoc = false;
        if (!shopPictureDocs.isEmpty()) {
            hasValidDoc = true;
            for (CKycDocDetailsResponseDto.Docs doc : shopPictureDocs) {
                if (!"yes".equalsIgnoreCase(doc.getIsShopOperational()) ||
                        !ALLOWED_SHOP_STRUCTURE_TYPES.contains(
                                doc.getShopStructureType() != null ? doc.getShopStructureType().toLowerCase() : "")) {
                    log.info("Shop document validation failed: docType={}, isShopOperational={}, shopStructureType={}",
                            doc.getDocType(), doc.getIsShopOperational(), doc.getShopStructureType());
                    hasValidDoc = false;
                    break;
                }
            }
        }

        if (!hasValidDoc) {
            log.info("No valid shop documents found with required properties for merchantId: {}", dto.getMerchantId());
            return false;
        }

        for (CKycDocDetailsResponseDto.Docs doc : shopPictureDocs) {
            if (doc == null) continue;

            LendingShopDocuments lendingShopDocument = new LendingShopDocuments();
            lendingShopDocument.setApplicationId(dto.getApplicationId());
            lendingShopDocument.setMerchantId(dto.getMerchantId());
            lendingShopDocument.setLatitude(doc.getLat());
            lendingShopDocument.setLongitude(doc.getLongDetails());
            lendingShopDocument.setIp(doc.getIp() != null ? doc.getIp() : lendingApplication.getIp());
            lendingShopDocument.setProofFrontSide(doc.getDocFrontImageUrl());
            lendingShopDocument.setProofType(getPhotoDocType(doc.getDocType()));

            log.info("CKYC Doc found: {} for merchantId: {}", doc.getDocType(), dto.getMerchantId());
            log.info("Mapped LendingShopDocument: {} for merchantId: {}", lendingShopDocument, dto.getMerchantId());

            lendingShopDocumentsList.add(lendingShopDocument);
        }

        // Check each document for distance validation until one passes
        for (LendingShopDocuments shopDoc : lendingShopDocumentsList) {
            Double distance = imageURLService.calculateDistanceBetweenInferredLocationAndShopDocumentLocation(shopDoc, dto.getMerchantId());
            log.info("Distance for document type {} with ID {}: {} meters", shopDoc.getProofType(), shopDoc.getApplicationId(), distance);

            if (distance != null && distance <= sidThreshold) {
                log.info("Document passed distance check with distance: {} meters for merchantId: {}", distance, dto.getMerchantId());
                return true;
            }
        }

        log.info("No documents passed distance check for merchantId: {}", dto.getMerchantId());
        return false;
    }

    public String getPhotoDocType(String docType) {
        if (docType == null) {
            return null;
        }
        if ("shop_picture_1".equalsIgnoreCase(docType)) {
            return ShopPhotoProofType.FRONT.getValue();
        } else if ("shop_picture_2".equalsIgnoreCase(docType)) {
            return ShopPhotoProofType.STOCK.getValue();
        }

        return null;
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
        loanDetailsV3Response.setLender(applicationStatusStateDTO.getLender());
        loanDetailsV3Response.setMerchantId(applicationStatusStateDTO.getMerchantId());
        loanDetailsV3Response.setUdyamRegistrationRequired(applicationStatusStateDTO.getUdyamRegistrationRequired());
        loanDetailsV3Response.setUdyamRegistrationLink(applicationStatusStateDTO.getUdyamRegistrationLink());
    }

    private static void setReferencesResponse(ReferenceStateDTO referenceStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setDummyMerchant(referenceStateDTO.isDummyMerchant());
        loanDetailsV3Response.setApplicationStatus(referenceStateDTO.getApplicationStatus());
        loanDetailsV3Response.setLender(referenceStateDTO.getLender());
        loanDetailsV3Response.setMerchantName(referenceStateDTO.getMerchantName());
        loanDetailsV3Response.setMobile(referenceStateDTO.getMobile());
        loanDetailsV3Response.setMerchantId(referenceStateDTO.getMerchantId());
        loanDetailsV3Response.setIsAadhaarAddressVerified(referenceStateDTO.getIsAadhaarAddressVerified());
        loanDetailsV3Response.setLoanPurpose(referenceStateDTO.getLoanPurpose());
    }

    private static void setLenderEvaluationResponse(LenderEvaluationStateDTO lenderEvaluationStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setLender(lenderEvaluationStateDTO.getLender());
        loanDetailsV3Response.setMerchantId(lenderEvaluationStateDTO.getMerchantId());
    }

    private static void setPermissionResponse(PermissionStateDTO permissionStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setErrorString(permissionStateDTO.getErrorString());
        loanDetailsV3Response.setDummyMerchant(permissionStateDTO.getDummyMerchant());
        loanDetailsV3Response.setSmsPermissionIsActive(permissionStateDTO.getSmsPermissionIsActive());
        loanDetailsV3Response.setLocationPermissionIsActive(permissionStateDTO.getLocationPermissionIsActive());
        loanDetailsV3Response.setLocationPermissionDate(permissionStateDTO.getLocationPermissionDate());
        loanDetailsV3Response.setMerchantId(permissionStateDTO.getMerchantId());
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
        loanDetailsV3Response.setOfferIncreased(eligibilityStateDTO.getOfferIncreased());
        loanDetailsV3Response.setPreviousFinalOffer(eligibilityStateDTO.getPreviousFinalOffer());
        loanDetailsV3Response.setMerchantId(eligibilityStateDTO.getMerchantId());
        if(Objects.nonNull(eligibilityStateDTO.getEligibilityExceptionFlag())) {
            loanDetailsV3Response.setEligibilityExceptionFlag(eligibilityStateDTO.getEligibilityExceptionFlag());
        }
        if(!ObjectUtils.isEmpty(eligibilityStateDTO.getRefreshCountDownMinutes())){
            loanDetailsV3Response.setRefreshCountDownMinutes(eligibilityStateDTO.getRefreshCountDownMinutes());
        }
    }

    private static void setPanPinResponse(EligibilityStateDTO eligibilityStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setPancard(eligibilityStateDTO.getPancard());
        loanDetailsV3Response.setPincode(eligibilityStateDTO.getPincode());
        loanDetailsV3Response.setHasExperian(eligibilityStateDTO.isHasExperian());
        loanDetailsV3Response.setMerchantName(eligibilityStateDTO.getMerchantName());
        loanDetailsV3Response.setIsPanNsdlVerified(eligibilityStateDTO.getIsPanNsdlVerified());
        loanDetailsV3Response.setKycMessage(eligibilityStateDTO.getKycMessage());
        loanDetailsV3Response.setMaxCountReached(eligibilityStateDTO.getMaxCountReached());
        loanDetailsV3Response.setDummyMerchant(eligibilityStateDTO.getDummyMerchant());
        loanDetailsV3Response.setFullName(eligibilityStateDTO.getFullName());
        loanDetailsV3Response.setDob(eligibilityStateDTO.getDob());
        loanDetailsV3Response.setMerchantId(eligibilityStateDTO.getMerchantId());
        if(Objects.nonNull(eligibilityStateDTO.getEligibilityExceptionFlag())) {
            loanDetailsV3Response.setEligibilityExceptionFlag(eligibilityStateDTO.getEligibilityExceptionFlag());
        }
        if(!ObjectUtils.isEmpty(eligibilityStateDTO.getRefreshCountDownMinutes())){
            loanDetailsV3Response.setRefreshCountDownMinutes(eligibilityStateDTO.getRefreshCountDownMinutes());
        }
    }

    private static void setEnachResponse(EnachStateDTO enachStateDTO,LoanDetailsV3Response loanDetailsV3Response){
        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        loanDetailsV3Response.setAccountDetails(enachStateDTO.getBankDetails());
        loanDetailsV3Response.setLender(enachStateDTO.getLender());
        applicationDetails.setEnachDeeplink(enachStateDTO.getEnachDeeplink());
        applicationDetails.setEnachDone(enachStateDTO.getEnachDone());
        applicationDetails.setEnachModes(enachStateDTO.getEnachModes());
        applicationDetails.setNachStartedAt(enachStateDTO.getNachStartedAt());
        applicationDetails.setNachSessionStatus(enachStateDTO.getNachSessionStatus());
        applicationDetails.setNachSessionMode(enachStateDTO.getNachSessionMode());
        applicationDetails.setEnachErrorResponse(enachStateDTO.getEnachErrorResponse());
        loanDetailsV3Response.setMerchantId(enachStateDTO.getMerchantId());
        if(enachStateDTO.isTopup())loanDetailsV3Response.setTopupLoanApplication(applicationDetails);
        else loanDetailsV3Response.setLoanApplication(applicationDetails);
    }

    private static void setModifiedOfferResponse(ModifiedOfferStateDTO modifiedOfferStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setEdiAmount(modifiedOfferStateDTO.getEdiAmount());
        loanDetailsV3Response.setEdiCount(modifiedOfferStateDTO.getEdiCount());
        loanDetailsV3Response.setApr(modifiedOfferStateDTO.getApr());
        loanDetailsV3Response.setTenure(modifiedOfferStateDTO.getTenure());
        loanDetailsV3Response.setInterestRate(modifiedOfferStateDTO.getInterestRate());
        loanDetailsV3Response.setArrangerFee(modifiedOfferStateDTO.getArrangerFee());
        loanDetailsV3Response.setLoanOffer(modifiedOfferStateDTO.getLoanOffer());
        loanDetailsV3Response.setApplicationId(modifiedOfferStateDTO.getApplicationId());
        loanDetailsV3Response.setMerchantId(modifiedOfferStateDTO.getMerchantId());
    }

    private static void setLenderAggregationResponse(LenderAggregationResponseDto lenderAggregationResponseDto, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setApplicationId(lenderAggregationResponseDto.getApplicationId());
        loanDetailsV3Response.setMessage(lenderAggregationResponseDto.getMessage());
        loanDetailsV3Response.setEdiAmount(lenderAggregationResponseDto.getEdi());
        loanDetailsV3Response.setInterestRate(lenderAggregationResponseDto.getInterestRate());
        loanDetailsV3Response.setAttemptCount(lenderAggregationResponseDto.getAttemptCount());
        loanDetailsV3Response.setProcessingFee(lenderAggregationResponseDto.getProcessingFee());
        loanDetailsV3Response.setLoanAmount(lenderAggregationResponseDto.getLoanAmount());
        loanDetailsV3Response.setTenure(lenderAggregationResponseDto.getTenure());
        loanDetailsV3Response.setLenders(lenderAggregationResponseDto.getLenders());
        loanDetailsV3Response.setScreenType(lenderAggregationResponseDto.getScreenType());
        loanDetailsV3Response.setLoanType(lenderAggregationResponseDto.getLoanType());
        loanDetailsV3Response.setPreviousLender(lenderAggregationResponseDto.getPreviousLender());
        loanDetailsV3Response.setRepeatLoan(lenderAggregationResponseDto.getRepeatLoan());
        loanDetailsV3Response.setLender(lenderAggregationResponseDto.getLender());
        loanDetailsV3Response.setMerchantId(lenderAggregationResponseDto.getMerchantId());
    }

    private static void setBLDocUploadStageResponse(BLDocUploadStateDTO blDocUploadStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
        applicationDetails.setApplicationId(blDocUploadStateDTO.getApplicationId());
        loanDetailsV3Response.setBlDocUploadUrl(blDocUploadStateDTO.getDeeplink());
        loanDetailsV3Response.setLoanApplication(applicationDetails);
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

    public static void setUdyamRegistrationPageResponse(UdyamRegistrationStateDTO udyamRegistrationStateDTO, LoanDetailsV3Response loanDetailsV3Response){
        loanDetailsV3Response.setLender(udyamRegistrationStateDTO.getLender());
        loanDetailsV3Response.setUdyamRegistrationLink(udyamRegistrationStateDTO.getUdyamRegistrationLink());
        loanDetailsV3Response.setMerchantId(udyamRegistrationStateDTO.getMerchantId());
        loanDetailsV3Response.setApplicationId(udyamRegistrationStateDTO.getApplicationId());
        loanDetailsV3Response.setUdyamRegistrationRequired(udyamRegistrationStateDTO.getIsUdyamRequired());
    }

}