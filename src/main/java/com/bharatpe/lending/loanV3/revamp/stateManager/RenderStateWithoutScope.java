package com.bharatpe.lending.loanV3.revamp.stateManager;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingMerchantPermissionsDao;
import com.bharatpe.lending.common.dao.LendingMerchantReferencesDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingMerchantPermissions;
import com.bharatpe.lending.common.entity.LendingMerchantReferences;
import com.bharatpe.lending.common.enums.RejectionReason;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.scopes.KYCStageDataService;
import com.bharatpe.lending.loanV3.revamp.services.EligibilityV3Service;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class RenderStateWithoutScope implements IRenderStateWithoutScope {
    @Autowired
    LoanUtil loanUtil;
    @Autowired
    LendingApplicationDao lendingApplicationDao;
    @Autowired
    ExperianDao experianDao;
    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;
    @Autowired
    LendingMerchantPermissionsDao lendingMerchantPermissionsDao;
    @Autowired
    EligibilityV3Service eligibilityV3Service;
    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    KYCStageDataService kycStageDataService;

    @Autowired
    LendingMerchantReferencesDao lendingMerchantReferencesDao;

    @Autowired
    LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Autowired
    private LoanUtilV3 loanUtilV3;


    // creditLineDeepLink - redirect to creditLineDeepLink?

    // check for activeLoan - redirect to active loan page - /all-loans

    // !hasExperian || !pancard || !pincode -> /landing -> PAN_PIN_PAGE

    // (!loanApplicationData || loanApplicationData?.applicationStatus === 'draft') && eligibility
    // if (!isAllPermissionsGiven && permissionsConsentGiven !== STATUS_YES_NO.YES)  - '/bp-permissions' - PERMISSION_PAGE

    // loanApplicationData && shouldRedirectToEnach(loanDetails) - '/register-nach' - ENACH_PAGE

    // (!loanApplication || !loanApplication?.applicationId) && eligibility - Eligibility page - OFFER_PAGE

    // loanApplication?.applicationStatus === 'draft'
    // typeof loanApplication?.enachBank === 'boolean' && !loanApplication?.enachBank - '/ineligible' - `/ineligible/${INELIGIBLE.ENACH}` - INELIGIBLE_SCREEN
    // !hasAddressDetails(loanApplication?.addressDetails) - '/address-business-details' - SHOP_ADDRESS_PAGE
    // kycStatus && !IsKycApproved(kycStatus)
    // kycDeepLink - redirect to KYC_PAGE
    // else /address-business-details' - SHOP_ADDRESS_PAGE

    // dummy_merchant - /agreement AGREEMENT_PAGE else '/additional-details' ADDITIONAL_DETAILS_PAGE (Questions around gst)

    // loanApplication?.applicationStatus
    // shouldRedirectToEnach - redirect to ENACH_PAGE else /application-status' APP_STATUS_PAGE

    // ineligible - redirect to ineligible page - INELIGIBLE_SCREEN

    // default - /landing page -> PAN_PIN_PAGE

    @Override
    public LoanDetailsV3Response fetchLendingStateData(ScopeDataArgs scopeDataArgs) {
        LoanDetailsV3Response loanDetailsV3Response = new LoanDetailsV3Response();

        LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
        scopeDataArgs.setOpenApplication(lendingApplication);
        if(!ObjectUtils.isEmpty(lendingApplication) &&
            ("draft".equalsIgnoreCase(lendingApplication.getStatus()) ||
            "pending_verification".equalsIgnoreCase(lendingApplication.getStatus()))
        ){
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            if(!ObjectUtils.isEmpty(lendingApplicationDetails) && !ObjectUtils.isEmpty(lendingApplicationDetails.getApplicationViewState())){
                loanDetailsV3Response.setNextPage(lendingApplicationDetails.getApplicationViewState());
                log.info("returning next page from db for {} : {}", scopeDataArgs.getMerchant().getId(), loanDetailsV3Response.getNextPage());
                return loanDetailsV3Response;
            }

            if (isKycPending(scopeDataArgs,loanDetailsV3Response)) {
                log.info("kyc pending {}", scopeDataArgs);
                return loanDetailsV3Response;
            }

            if (isReferenceSelectionPending(scopeDataArgs,loanDetailsV3Response)) {
                log.info("reference selection pending {}", scopeDataArgs);
                return loanDetailsV3Response;
            }

            if (isAgreementKFSPending(scopeDataArgs, loanDetailsV3Response)){
                log.info("agreement and kfs pending {}", scopeDataArgs);
                return loanDetailsV3Response;
            }

            if (nachNotExist(scopeDataArgs,loanDetailsV3Response)) {
                log.info("nach doesn't exist {}", scopeDataArgs);
                return loanDetailsV3Response;
            }
        }

        if (showApplicationStatus(scopeDataArgs,loanDetailsV3Response)) {
            log.info("show status page {}", scopeDataArgs);
            return loanDetailsV3Response;
        }

        if (experianNotExist(scopeDataArgs, loanDetailsV3Response)) {
            log.info("experian is missing {}", scopeDataArgs);
            return loanDetailsV3Response;
        }
//        if (permissionNotExist(scopeDataArgs, loanDetailsV3Response)) {
//            log.info("permission doesn't exist {}", scopeDataArgs);
//            return loanDetailsV3Response;
//        }
        if (showOfferPage(scopeDataArgs,loanDetailsV3Response)) {
            log.info("eligibility exist {}", scopeDataArgs);
            return loanDetailsV3Response;
        }
        if (hasNonNachableBank(scopeDataArgs,loanDetailsV3Response)) {
            log.info("non nachable bank exist {}", scopeDataArgs);
            return loanDetailsV3Response;
        }

        log.info("returning default state for {}", scopeDataArgs.getMerchant().getId());
        loanDetailsV3Response.setNextPage(LendingViewStates.PAN_PIN_PAGE.name());
        return loanDetailsV3Response;
    }

    public LendingStateDTO<PANPINStateDTO> panPinWorkflow(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<PANPINStateDTO> lendingStateDTO = null;
        Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
        if (experian != null && (ObjectUtils.isEmpty(experian.getPincode())
                || ObjectUtils.isEmpty(experian.getPancardNumber()))) {
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.PAN_PIN_PAGE);
            return lendingStateDTO;
        }
        if (experian == null) {
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.PAN_PIN_PAGE);
        }
        if(!loanUtilV3.isPanNsdlVerified(scopeDataArgs.getMerchant().getId())){
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.PAN_PIN_PAGE);
        }
        return lendingStateDTO;
    }

    public boolean experianNotExist(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for experian for {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<PANPINStateDTO> lendingStateDTO = panPinWorkflow(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }

    public LendingStateDTO<PermissionStateDTO> permissionsworkFlow(ScopeDataArgs scopeDataArgs) {
        LendingMerchantPermissions lendingMerchantPermissions = lendingMerchantPermissionsDao.findByMerchantId(scopeDataArgs.getMerchant().getId());
        LendingStateDTO<PermissionStateDTO> lendingStateDTO = null;
        if (null != lendingMerchantPermissions && (!lendingMerchantPermissions.getLocationPermissionActive())) {
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.PERMISSIONS_PAGE);
            return lendingStateDTO;
        }
        if (null == lendingMerchantPermissions){
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.PERMISSIONS_PAGE);
        }
        return lendingStateDTO;
    }

    public boolean permissionNotExist(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for permission for {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<PermissionStateDTO> lendingStateDTO = permissionsworkFlow(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }


    public LendingStateDTO<EnachStateDTO> registerEnachworkFlow(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<EnachStateDTO> lendingStateDTO = null;
        LendingApplication openApplication = scopeDataArgs.getOpenApplication();
        if ("pending_verification".equalsIgnoreCase(openApplication.getStatus()) &&
                !LoanType.TOPUP.name().equalsIgnoreCase(openApplication.getLoanType()) &&
                !"APPROVED".equalsIgnoreCase(openApplication.getNachStatus())
        ) {
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.ENACH_PAGE);
            return lendingStateDTO;
        }
        return lendingStateDTO;
    }

    public boolean nachNotExist(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for nach for {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<EnachStateDTO> lendingStateDTO = registerEnachworkFlow(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }

    public LendingStateDTO<EligibilityStateDTO> offerWorkflow(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<EligibilityStateDTO> lendingStateDTO = null;
        if (null == scopeDataArgs.getOpenApplication() || "rejected".equalsIgnoreCase(scopeDataArgs.getOpenApplication().getStatus())) {
            EligibilityStateDTO eligibilityStateDTO = new EligibilityStateDTO();
            eligibilityStateDTO.setMerchant(scopeDataArgs.getMerchant());
            eligibilityV3Service.fetchEligibilityWithoutScopeRequest(scopeDataArgs.getLoanDetailsV3Request(), eligibilityStateDTO);
            if (!ObjectUtils.isEmpty(eligibilityStateDTO.getEligibility())) {
                lendingStateDTO = new LendingStateDTO<>();
                lendingStateDTO.setScopeState(LendingViewStates.OFFER_PAGE);
                return lendingStateDTO;
            }
            scopeDataArgs.setEligibilityStateDTO(eligibilityStateDTO);
        }
        return lendingStateDTO;
    }

    public boolean showOfferPage(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for offer for {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<EligibilityStateDTO> lendingStateDTO = offerWorkflow(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }

//    public LendingStateDTO<IneligibleStateDTO> inEligibleWorkflow(ScopeDataArgs scopeDataArgs) {
//        LendingStateDTO<IneligibleStateDTO> lendingStateDTO = null;
//        if (!ObjectUtils.isEmpty(scopeDataArgs.getEligibilityStateDTO().getIneligible())) {
//            lendingStateDTO = new LendingStateDTO<>();
//            lendingStateDTO.setLendingViewStates(LendingViewStates.INELIGIBLE_PAGE);
//            lendingStateDTO.setPartialData(false);
//            lendingStateDTO.setData(IneligibleStateDTO.builder()
//                    .ineligible(scopeDataArgs.getEligibilityStateDTO().getIneligible())
//                    .build());
//        }
//        return lendingStateDTO;
//    }


    public LendingStateDTO<IneligibleStateDTO> inEligibleNonNachableBankWorkflow(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<IneligibleStateDTO> lendingStateDTO = null;
        if (!ObjectUtils.isEmpty(scopeDataArgs.getOpenApplication()) &&
                "draft".equalsIgnoreCase(scopeDataArgs.getOpenApplication().getStatus()) &&
                loanUtil.isEnachBank(scopeDataArgs.getOpenApplication().getMerchantId())
        ) {
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.INELIGIBLE_PAGE);
        }
        return lendingStateDTO;
    }

    public boolean hasNonNachableBank(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for non nachable bank for {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<IneligibleStateDTO> lendingStateDTO = inEligibleNonNachableBankWorkflow(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }

//    public LendingStateDTO<BusinessAddressDTO> businessAddressWorkflow(ScopeDataArgs scopeDataArgs) {
//        LendingStateDTO<BusinessAddressDTO> lendingStateDTO = null;
//        if (!ObjectUtils.isEmpty(scopeDataArgs.getOpenApplication()) &&
//                "draft".equalsIgnoreCase(scopeDataArgs.getOpenApplication().getStatus()) &&
//                addressAbsent(scopeDataArgs.getOpenApplication())
//        ) {
//            lendingStateDTO = new LendingStateDTO<>();
//            lendingStateDTO.setLendingViewStates(LendingViewStates.SHOP_DETAILS_PAGE);
//            lendingStateDTO.setPartialData(false);
//            lendingStateDTO.setData(null);
//        }
//        return lendingStateDTO;
//    }

//    private boolean addressAbsent(LendingApplication openApplication) {
//        return (ObjectUtils.isEmpty(openApplication.getShopNumber()) ||
//                ObjectUtils.isEmpty(openApplication.getStreetAddress()) ||
//                ObjectUtils.isEmpty(openApplication.getState()) ||
//                ObjectUtils.isEmpty(openApplication.getCity()) ||
//                ObjectUtils.isEmpty(openApplication.getPincode())
//        );
//    }


    public LendingStateDTO<KYCStateDTO> kycWorkflow(ScopeDataArgs scopeDataArgs) {
        if ("draft".equalsIgnoreCase(scopeDataArgs.getOpenApplication().getStatus())) {
            LendingStateDTO<KYCStateDTO> lendingStateDTO1 = kycStageDataService.fetchScopedData(scopeDataArgs);
            if(Objects.nonNull(lendingStateDTO1.getData().getShowKycPage()) &&
                    lendingStateDTO1.getData().getShowKycPage() &&
                    Objects.nonNull(lendingStateDTO1.getData().getKycStatus()) &&
                    !KycStatus.APPROVED.equals(lendingStateDTO1.getData().getKycStatus())){
                return lendingStateDTO1;
            }
        }
        return null;
    }


    public boolean isKycPending(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for kyc for {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<KYCStateDTO> lendingStateDTO = kycWorkflow(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }

    public boolean isReferenceSelectionPending(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for reference selection for {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<ReferenceStateDTO> lendingStateDTO = referenceState(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }

    public boolean isAgreementKFSPending(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response){
        log.info("checking for agreement & KFS for {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<AgreementStateDTO> lendingStateDTO = agreementState(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }

    public LendingStateDTO<AgreementStateDTO> agreementState(ScopeDataArgs scopeDataArgs){
        LendingStateDTO<AgreementStateDTO> lendingStateDTO = null;
        if(Objects.isNull(scopeDataArgs.getOpenApplication().getAgreementAt())){
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.AGREEMENT_PAGE);
        }
        return lendingStateDTO;
    }

    public LendingStateDTO<ReferenceStateDTO> referenceState(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<ReferenceStateDTO> lendingStateDTO = null;
        if ("draft".equalsIgnoreCase(scopeDataArgs.getOpenApplication().getStatus()) &&
          showReferencePage(scopeDataArgs.getOpenApplication().getMerchantId(), scopeDataArgs.getOpenApplication().getId())
        ) {
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.REFERENCE_PAGE);
        }
        return lendingStateDTO;
    }

    private boolean showReferencePage(Long merchantId, Long openApplicationId) {
        List<LendingMerchantReferences> referencesList = lendingMerchantReferencesDao.findByMerchantIdAndApplicationId(merchantId, openApplicationId);
        log.info("ReferenceList for applicationId : {} {}", openApplicationId, Arrays.toString(referencesList.toArray()));
        return referencesList.isEmpty();
    }

    public LendingStateDTO<ApplicationStateDTO> appStatusWorkflow(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<ApplicationStateDTO> lendingStateDTO = null;
        boolean showApplicationStatus = false;
        LendingApplication lendingApplication = scopeDataArgs.getOpenApplication();
        if(!ObjectUtils.isEmpty(lendingApplication)){
            if("approved".equalsIgnoreCase(lendingApplication.getStatus()) &&
                    Objects.isNull(lendingApplication.getDisburseTimestamp()) &&
                    !"DISBURSED".equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())
            )showApplicationStatus = true;
            if("pending_verification".equalsIgnoreCase(lendingApplication.getStatus()) &&
                    "APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus())
            )showApplicationStatus = true;
        }
        if(showApplicationStatus){
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.APPLICATION_STATUS_PAGE);
        }
        return lendingStateDTO;
    }

    public boolean showApplicationStatus(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for app status for {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<ApplicationStateDTO> lendingStateDTO = appStatusWorkflow(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }

    private static boolean populateResponseDTO(LoanDetailsV3Response loanDetailsV3Response, LendingStateDTO<?> lendingStateDTO) {
        if (!ObjectUtils.isEmpty(lendingStateDTO)) {
            LoanDetailsV3Response.populateResponseForRequestWithoutScope(lendingStateDTO, loanDetailsV3Response);
            return true;
        }
        return false;
    }
}

// organized merchant check ??
// dummy merchant for what all screeens ?
// isBankLinked - used on which screen ?
// merchant name - what screen ?
// is Bp club member -most likely offer page ?
// isRepeatLoan ? used for which screen ?
// bank acc details for which screen ?
// biz category - biz screen
