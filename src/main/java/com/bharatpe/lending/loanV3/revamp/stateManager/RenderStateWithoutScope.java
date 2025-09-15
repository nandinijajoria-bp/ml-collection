package com.bharatpe.lending.loanV3.revamp.stateManager;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.VkycStatus;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.scopes.KYCStageDataService;
import com.bharatpe.lending.loanV3.revamp.scopes.UpiAutoPayStageHelper;
import com.bharatpe.lending.loanV3.revamp.services.EligibilityV3Service;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.revamp.services.businessLoan.EmiDashboardService;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.loanV3.services.VKycService;
import com.bharatpe.lending.loanV3.utils.EmiUtils;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

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
    AutoPayUPIDao autoPayUPIDao;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    KYCStageDataService kycStageDataService;

    @Autowired
    private LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Autowired
    LendingMerchantReferencesDao lendingMerchantReferencesDao;

    @Autowired
    LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Autowired
    private LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    private LoanUtilV3 loanUtilV3;

    @Autowired
    private EmiDashboardService emiDashboardService;
    @Autowired
    private LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    private EmiUtils emiUtils;

    @Value("${show.pan.pin.page.enabled:true}")
    private boolean showPanPinPage;

    @Autowired
    LendingApplicationVkycDetailsDao lendingApplicationVkycDetailsDao;

    @Autowired
    VKycService vkycService;

    @Autowired
    private UpiAutoPayStageHelper upiAutoPayStageHelper;


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
                if(LendingViewStates.UPI_AUTOPAY_PAGE.name().equals(lendingApplicationDetails.getApplicationViewState())
                    && upiAutoPayStageHelper.isEligibleForFailedForceSkip(lendingApplication.getId(), lendingApplication.getMerchantId())){
                    LendingViewStates nextPage = upiAutoPayStageHelper.forceSkipUpiAutopayAndGetNextPage(lendingApplication, lendingApplicationDetails);
                    loanDetailsV3Response.setNextPage(nextPage.name());
                    return loanDetailsV3Response;
                }
                loanDetailsV3Response.setNextPage(lendingApplicationDetails.getApplicationViewState());
                log.info("returning next page from db for {} : {}", scopeDataArgs.getMerchant().getId(), loanDetailsV3Response.getNextPage());
                return loanDetailsV3Response;
            }
            if(isShopPicture(scopeDataArgs, loanDetailsV3Response)){
                log.info("shop picture page pending: {}", scopeDataArgs);
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

            if(isUpiAutopayPending(scopeDataArgs, loanDetailsV3Response)){
                log.info("Upi Autopay Pending {}", scopeDataArgs);
                return loanDetailsV3Response;
            }

            if (nachNotExist(scopeDataArgs,loanDetailsV3Response)) {
                log.info("nach doesn't exist {}", scopeDataArgs);
                return loanDetailsV3Response;
            }
        }

        if (isVkycPending(scopeDataArgs, loanDetailsV3Response)) {
            log.info("show vkyc pending page {}", scopeDataArgs);
            return loanDetailsV3Response;
        }

        if (showApplicationStatus(scopeDataArgs,loanDetailsV3Response)) {
            log.info("show status page {}", scopeDataArgs);
            return loanDetailsV3Response;
        }
        if (showPanPinPage && panPinForMerchnant(scopeDataArgs, loanDetailsV3Response)) {
            log.info("show pan pin page {}", scopeDataArgs);
            return loanDetailsV3Response;
        }

        if (experianNotExist(scopeDataArgs, loanDetailsV3Response)) {
            log.info("experian is missing {}", scopeDataArgs);
            return loanDetailsV3Response;
        }
        if (showMaskedMobilePage(scopeDataArgs, loanDetailsV3Response)) {
            log.info("masked mobile page {}", scopeDataArgs);
            return loanDetailsV3Response;
        }
        if(emiUtils.isEmiFlowEnabled() && showPlanSelectionPage(scopeDataArgs, loanDetailsV3Response)){
            log.info("emi, edi plan selection page {}", scopeDataArgs);
            return loanDetailsV3Response;
        }
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

    private boolean isUpiAutopayPending(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for upi autopay pending for merchant_id: {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<UpiAutopayStateDTO> lendingStateDTO = registerUpiAutopayWorkflow(scopeDataArgs);
        log.info("upi autopay state for merchant_id: {} is {}", scopeDataArgs.getMerchant().getId(), lendingStateDTO);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }

    private LendingStateDTO<UpiAutopayStateDTO> registerUpiAutopayWorkflow(ScopeDataArgs scopeDataArgs) {
        log.info("registering upi autopay workflow for merchant_id: {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<UpiAutopayStateDTO> lendingStateDTO = null;
        LendingApplication openApplication = scopeDataArgs.getOpenApplication();

        if ("pending_verification".equalsIgnoreCase(openApplication.getStatus()) &&
                !LoanType.TOPUP.name().equalsIgnoreCase(openApplication.getLoanType()) &&
                loanUtil.isEligibleForUpiAutopayDedicatedScreen(openApplication)){
            log.info("upi autopay is eligible for merchant_id: {}", scopeDataArgs.getMerchant().getId());
            AutoPayUPI autoPayUPI = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(openApplication.getId(), openApplication.getLender(), Collections.singletonList("ACTIVE"));
            if(ObjectUtils.isEmpty(autoPayUPI)){
                log.info("upi autopay not registered for merchant_id: {}", scopeDataArgs.getMerchant().getId());
                lendingStateDTO = new LendingStateDTO<>();
                lendingStateDTO.setScopeState(LendingViewStates.UPI_AUTOPAY_PAGE);
                return lendingStateDTO;
            }
            log.info("upi autopay already registered for merchant_id: {}", scopeDataArgs.getMerchant().getId());
        }

        // For Topup, we check if the application is in draft state and if it is eligible for UPI Autopay
        if ("draft".equalsIgnoreCase(openApplication.getStatus()) &&
                LoanType.TOPUP.name().equalsIgnoreCase(openApplication.getLoanType()) &&
                loanUtil.isEligibleForUpiAutopayTopupDedicatedScreen(openApplication)){
            log.info("upi autopay is eligible for topup of merchant_id: {}", scopeDataArgs.getMerchant().getId());
            AutoPayUPI autoPayUPI = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(openApplication.getId(), openApplication.getLender(), Collections.singletonList("ACTIVE"));
            if(ObjectUtils.isEmpty(autoPayUPI)){
                log.info("upi autopay not registered for topup for merchant_id: {}", scopeDataArgs.getMerchant().getId());
                lendingStateDTO = new LendingStateDTO<>();
                lendingStateDTO.setScopeState(LendingViewStates.UPI_AUTOPAY_PAGE);
                return lendingStateDTO;
            }
            log.info("upi autopay already registered for topup for merchant_id: {}", scopeDataArgs.getMerchant().getId());
        }

        log.info("upi autopay not eligible for merchant_id: {}", scopeDataArgs.getMerchant().getId());
        return lendingStateDTO;
    }

    private boolean isShopPicture(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for shop picture page for merchant_id: {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<?> lendingStateDTO = getShopPicturePage(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }

    private LendingStateDTO<?> getShopPicturePage(ScopeDataArgs scopeDataArgs) {
        int hasValidProofTypeResponse = lendingShopDocumentsDao.hasValidProofTypes(
                scopeDataArgs.getMerchant().getId(), scopeDataArgs.getApplicationId());
        if(hasValidProofTypeResponse!=1){
            log.info("shop picture does not exist for application: {}", scopeDataArgs.getApplicationId());
            return LendingStateDTO.builder().scopeState(LendingViewStates.SHOP_PICTURES_PAGE).build();
        }
        return null;
    }

    private boolean showPlanSelectionPage(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for plan selection page for merchant_id: {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<EligibilityStateDTO> lendingStateDTO = getPlanSelectionPage(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
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
        if(experian != null && !ObjectUtils.isEmpty(experian.getPancardNumber())
                && !loanUtilV3.isPanNsdlVerified(scopeDataArgs.getToken(), experian.getPancardNumber(), scopeDataArgs.getMerchant().getId())) {
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.PAN_PIN_PAGE);
        }
        if (experian == null) {
            lendingStateDTO = new LendingStateDTO<>();
            lendingStateDTO.setScopeState(LendingViewStates.PAN_PIN_PAGE);
        }
        return lendingStateDTO;
    }


    public LendingStateDTO<PANPINStateDTO> panPinWorkflowMerchant() {
        LendingStateDTO<PANPINStateDTO> lendingStateDTO = new LendingStateDTO<>();
        lendingStateDTO.setScopeState(LendingViewStates.PAN_PIN_PAGE);
        return lendingStateDTO;
    }

    public boolean panPinForMerchnant(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("Show Pan Pin Page For Merchant {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<PANPINStateDTO> lendingStateDTO = panPinWorkflowMerchant();
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
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
    public LendingStateDTO<EligibilityStateDTO> getPlanSelectionPage(ScopeDataArgs scopeDataArgs) {
        CompletableFuture<EmiDashboardResponse> emiDashboardDataFuture =
                emiDashboardService.getEmiDashboardResponse(scopeDataArgs.getMerchant().getId(), scopeDataArgs.getToken());
        EmiDashboardResponse emiDashboardData = emiDashboardService.getData(emiDashboardDataFuture);
        LendingStateDTO<EligibilityStateDTO> lendingStateDTO;
        EligibilityStateDTO eligibilityStateDTO = new EligibilityStateDTO();
        eligibilityStateDTO.setMerchant(scopeDataArgs.getMerchant());
        lendingStateDTO = new LendingStateDTO<>();
        if(emiUtils.isActive(emiDashboardData)){
            // sending null to refresh the loan-dashboard since there is already a emi active loan
            log.info("invalid state call for merchant: {}",scopeDataArgs.getMerchant().getId());
            lendingStateDTO.setScopeState(null);
            scopeDataArgs.setEligibilityStateDTO(eligibilityStateDTO);
            return lendingStateDTO;
        }
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(scopeDataArgs.getMerchant().getId());
        log.info("emi_dashboard_data and lending_risk_variable for merchant_id: {} are {} , {}",
                scopeDataArgs.getMerchant().getId(), emiDashboardData, lendingRiskVariables);

        if(emiUtils.isEligibleForPlanSelectionPage(emiDashboardData, lendingRiskVariables)){
            lendingStateDTO.setScopeState(LendingViewStates.PLAN_SELECTION_PAGE);
            scopeDataArgs.setEligibilityStateDTO(eligibilityStateDTO);
            return lendingStateDTO;
        }
        return null;
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

    public boolean showMaskedMobilePage(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for masked mobile for {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<MaskedMobileDTO> lendingStateDTO = maskedMobileWorkflow(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }

    private LendingStateDTO<MaskedMobileDTO> maskedMobileWorkflow(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<MaskedMobileDTO> lendingStateDTO = null;

        if(ObjectUtils.isEmpty(scopeDataArgs.getOpenApplication())) {
            try {
                EligibilityStateDTO eligibilityStateDTO = new EligibilityStateDTO();
                eligibilityStateDTO.setMerchant(scopeDataArgs.getMerchant());
                GlobalLimitResponse scienapticGlobalLimit  = eligibilityV3Service.requestForEligibility(scopeDataArgs.getLoanDetailsV3Request(), eligibilityStateDTO);
                if(!ObjectUtils.isEmpty(scienapticGlobalLimit)
                        && !ObjectUtils.isEmpty(scienapticGlobalLimit.getData())
                        && LoanDetailsConstant.UNDERWRITING_MASKED_MOBILE_EXCEPTION.equalsIgnoreCase(scienapticGlobalLimit.getErrorCode())
                        && !ObjectUtils.isEmpty(scienapticGlobalLimit.getData().getMaskedMobiles())
                ) {
                    lendingStateDTO = new LendingStateDTO<>();
                    lendingStateDTO.setScopeState(LendingViewStates.MASKED_MOBILE);
                }
            } catch (BureauCallMaskedApiException e) {
                log.error("bureau call masked api ex {}", e);
            }
        }
        return lendingStateDTO;
    }

    public boolean showOfferPage(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for offer for {}", scopeDataArgs.getMerchant().getId());
        LendingStateDTO<EligibilityStateDTO> lendingStateDTO = offerWorkflow(scopeDataArgs);
        return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
    }


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
            LoanDetailsV3Service.populateResponseForRequestWithoutScope(lendingStateDTO, loanDetailsV3Response);
            return true;
        }
        return false;
    }

    public boolean isVkycPending(ScopeDataArgs scopeDataArgs, LoanDetailsV3Response loanDetailsV3Response) {
        log.info("checking for vkyc status for {}", scopeDataArgs.getMerchant().getId());
        LendingApplication lendingApplication = scopeDataArgs.getOpenApplication();
        if (!ObjectUtils.isEmpty(lendingApplication) && Arrays.asList("pending_verification", "approved").contains(lendingApplication.getStatus())
                && vkycService.isVkycEnabled(lendingApplication.getMerchantId(), lendingApplication.getLender(), LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))) {
            LendingApplicationVkycDetails vkycDetails = lendingApplicationVkycDetailsDao.findByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender()).orElse(null);
            if (!ObjectUtils.isEmpty(vkycDetails) && !VkycStatus.getTerminatedVkycStatusList().contains(vkycDetails.getStatus())) {
                LendingStateDTO<ApplicationStateDTO> lendingStateDTO = new LendingStateDTO<>();
                lendingStateDTO.setScopeState(LendingViewStates.LENDER_VKYC_PAGE);
                return populateResponseDTO(loanDetailsV3Response, lendingStateDTO);
            }
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
