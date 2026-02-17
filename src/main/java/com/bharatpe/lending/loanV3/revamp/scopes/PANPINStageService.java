package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingPancardDetailsDao;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.dto.PanFetchKYCResponseDto;
import com.bharatpe.lending.entity.LendingPancardDetails;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.EligibilityStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.EmiDashboardResponse;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.EligibilityV3Service;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.services.businessLoan.EmiDashboardService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.loanV3.utils.EmiUtils;
import com.bharatpe.lending.util.CommonUtil;
import com.bharatpe.lending.util.LoanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class PANPINStageService implements IStageDataService<EligibilityStateDTO>{

    private final EligibilityV3Service eligibilityV3Service;
    private final ExperianDao experianDao;
    private final KycHandler kycHandler;
    private final LoanUtil loanUtil;
    private final LoanDashboardService loanDashboardService;
    private final FunnelService funnelService;
    private final EasyLoanUtil easyLoanUtil;
    private final LendingPancardDetailsDao lendingPancardDetailsDao;
    private final LendingRiskVariablesDao lendingRiskVariablesDao;
    private final EmiUtils emiUtils;
    private final EmiDashboardService emiDashboardService;
    private final APIGatewayService apiGatewayService;

    @Value("${panpin.page.revamp.rollout:10}")
    private Integer panPinRevampRolloutPercent;


    @Override
    public LendingStateDTO<EligibilityStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        if (easyLoanUtil.percentScaleUp(scopeDataArgs.getMerchant().getId(), panPinRevampRolloutPercent)) {
            log.info("In PANPINStageService revamp flow for Merchant {} ", scopeDataArgs.getMerchant().getId());
            return getPageResponse(scopeDataArgs);
        }

        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(scopeDataArgs.getMerchant().getId());
        if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
            funnelService.submitEventV3(scopeDataArgs.getMerchant().getId(), null, null,
                    FunnelEnums.StageId.PAN_PIN_PAGE, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
        else{
            funnelService.submitEvent(scopeDataArgs.getMerchant().getId(), null, null,
                    FunnelEnums.StageId.PAN_PIN_PAGE, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString());
        }
        EligibilityStateDTO eligibilityStateDTO = new EligibilityStateDTO();
        try{
            eligibilityStateDTO.setMerchantName(loanUtil.getBeneficiaryName(scopeDataArgs.getMerchant().getId()));
            eligibilityStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
            Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
            String kycPancard = kycHandler.getPanNumber(scopeDataArgs.getMerchant().getId());
            log.info("Fetched PAN from KYC for merchant {} : {} and Experian : {}", scopeDataArgs.getMerchant().getId(), kycPancard, experian);

            eligibilityStateDTO.setPancard(kycPancard);
            if (experian != null) {
                eligibilityStateDTO.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
                eligibilityStateDTO.setHasExperian(true);
            }
            if (kycPancard==null){
                eligibilityStateDTO.setHasExperian(false);
            }

            LendingPancardDetails lendingPancardDetails = lendingPancardDetailsDao.findTop1ByMerchantIdOrderByIdDesc(scopeDataArgs.getMerchant().getId());
            if (!ObjectUtils.isEmpty(lendingPancardDetails)
                    && LendingConstants.PAN_VERIFICATION_VERSION.equals(lendingPancardDetails.getVersion())
                    && !ObjectUtils.isEmpty(lendingPancardDetails.getAadhaarSeedingStatus())
            ) {
                eligibilityStateDTO.setIsPanNsdlVerified(true);
                eligibilityStateDTO.setFullName(lendingPancardDetails.getName());
                eligibilityStateDTO.setDob(lendingPancardDetails.getDob());
            } else {
                PanFetchKYCResponseDto response = null;
                try {
                    response = kycHandler.panFetch(scopeDataArgs.getToken(), eligibilityStateDTO.getPancard(), scopeDataArgs.getMerchant().getId());
                    if (response != null && response.getStatus()) {
                        PanFetchKYCResponseDto.Data data = response.getData();
                        if (data != null) {
                            eligibilityStateDTO.setDob(data.getDateOfBirth());
                            eligibilityStateDTO.setFullName(data.getName());
                            if (data.getIsPanNsdlVerified() != null) {
                                eligibilityStateDTO.setIsPanNsdlVerified(data.getIsPanNsdlVerified());
                                if(data.getIsPanNsdlVerified()){
                                    eligibilityStateDTO.setDob(data.getVerifiedDob());
                                    eligibilityStateDTO.setFullName(data.getVerifiedName());
                                    if (!ObjectUtils.isEmpty(lendingPancardDetails)) {
                                        lendingPancardDetails.setName(data.getVerifiedName());
                                        lendingPancardDetails.setPancardNumber(data.getPanNumber());
                                        lendingPancardDetails.setDob(data.getVerifiedDob());
                                        lendingPancardDetails.setVersion(LendingConstants.PAN_VERIFICATION_VERSION);
                                        lendingPancardDetailsDao.save(lendingPancardDetails);
                                    } else {
                                        lendingPancardDetailsDao.save(new LendingPancardDetails(scopeDataArgs.getMerchant().getId(), data.getPanNumber(), data.getVerifiedName(), null, LendingConstants.PAN_VERIFICATION_VERSION, null, data.getVerifiedDob()));
                                    }
                                }
                            }
                        }
                    }else if (response != null && response.getData() != null && !response.getStatus() && response.getData().getMessage() != null) {
                        eligibilityStateDTO.setKycMessage(response.getData().getMessage());
                    }
                }catch (HttpClientErrorException.TooManyRequests e) {
                    log.error("Too Many requests error");
                    eligibilityStateDTO.setMaxCountReached(true);
                    eligibilityStateDTO.setMessage("You've reached your daily limit for PAN input. Please try again after 24 hours");
                }
            }

        }
        catch (Exception e) {
            log.error("error in getting pan pin stage data for {} : {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            eligibilityStateDTO.setErrorString("Something went wrong");
        }
        return new LendingStateDTO<>(eligibilityStateDTO , LendingViewStates.PAN_PIN_PAGE, LendingViewStates.PAN_PIN_PAGE);
    }

    public LendingStateDTO<EligibilityStateDTO> getPageResponse(ScopeDataArgs scopeDataArgs) {
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(scopeDataArgs.getMerchant().getId());
        if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
            funnelService.submitEventV3(scopeDataArgs.getMerchant().getId(), null, null,
                    FunnelEnums.StageId.PAN_PIN_PAGE, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
        else{
            funnelService.submitEvent(scopeDataArgs.getMerchant().getId(), null, null,
                    FunnelEnums.StageId.PAN_PIN_PAGE, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString());
        }
        EligibilityStateDTO eligibilityStateDTO = new EligibilityStateDTO();
        try {
            eligibilityStateDTO.setMerchantName(loanUtil.getBeneficiaryName(scopeDataArgs.getMerchant().getId()));
            eligibilityStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
            Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
            LendingPancardDetails lendingPancardDetails = lendingPancardDetailsDao.findTop1ByMerchantIdOrderByIdDesc(scopeDataArgs.getMerchant().getId());
            String kycPancard = getMerchantPancardNumber(scopeDataArgs.getMerchant().getId(), lendingPancardDetails);
            log.info("Fetched PAN from KYC for merchant {} : {} and Experian : {}", scopeDataArgs.getMerchant().getId(), kycPancard, experian);

            if (experian != null) {
                eligibilityStateDTO.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
                eligibilityStateDTO.setHasExperian(true);
            }
            if (StringUtils.isEmpty(kycPancard)) {
                eligibilityStateDTO.setHasExperian(false);
                return new LendingStateDTO<>(eligibilityStateDTO , LendingViewStates.PAN_PIN_PAGE, LendingViewStates.PAN_PIN_PAGE);
            }

            eligibilityStateDTO.setPancard(kycPancard);

            setMerchantPanDetailsInResponse(eligibilityStateDTO, scopeDataArgs, lendingPancardDetails);
        }catch (Exception e) {
            log.error("error in getting pan pin stage data for {} : {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            eligibilityStateDTO.setErrorString("Something went wrong");
        }
        return new LendingStateDTO<>(eligibilityStateDTO , LendingViewStates.PAN_PIN_PAGE, LendingViewStates.PAN_PIN_PAGE);

        }

    private void setMerchantPanDetailsInResponse(EligibilityStateDTO eligibilityStateDTO, ScopeDataArgs scopeDataArgs, LendingPancardDetails lendingPancardDetails) {
        try {
            if (!ObjectUtils.isEmpty(lendingPancardDetails)) {
                eligibilityStateDTO.setDob(lendingPancardDetails.getDob());
                eligibilityStateDTO.setFullName(lendingPancardDetails.getName());
            }
            PanFetchKYCResponseDto response = kycHandler.panFetch(scopeDataArgs.getToken(), eligibilityStateDTO.getPancard(), scopeDataArgs.getMerchant().getId());
            if (response != null && response.getStatus() && response.getData() != null) {
                PanFetchKYCResponseDto.Data data = response.getData();
                if (!ObjectUtils.isEmpty(data.getDateOfBirth())) {
                    eligibilityStateDTO.setDob(data.getDateOfBirth());
                } else {
                    log.warn("DOB is empty in PAN fetch response for merchant {}", scopeDataArgs.getMerchant().getId());
                }
                if (!ObjectUtils.isEmpty(data.getName())) {
                    eligibilityStateDTO.setFullName(data.getName());
                } else {
                    log.warn("Name is empty in PAN fetch response for merchant {}", scopeDataArgs.getMerchant().getId());
                }
                eligibilityStateDTO.setIsPanNsdlVerified(data.getIsPanNsdlVerified());
                if (Boolean.TRUE.equals(data.getIsPanNsdlVerified())) {
                    if (!ObjectUtils.isEmpty(data.getVerifiedDob())) {
                        eligibilityStateDTO.setDob(data.getVerifiedDob());
                    } else {
                        log.warn("Verified DOB is empty in PAN fetch response for merchant {}", scopeDataArgs.getMerchant().getId());
                    }
                    if (!ObjectUtils.isEmpty(data.getVerifiedName())) {
                        eligibilityStateDTO.setFullName(data.getVerifiedName());
                    } else {
                        log.warn("Verified Name is empty in PAN fetch response for merchant {}", scopeDataArgs.getMerchant().getId());
                    }

                    savePanDetailsInDB(lendingPancardDetails, data, scopeDataArgs);
                }
            } else {
                eligibilityStateDTO.setIsPanNsdlVerified(!ObjectUtils.isEmpty(lendingPancardDetails.getAadhaarSeedingStatus())
                        && LendingConstants.PAN_VERIFICATION_VERSION.equals(lendingPancardDetails.getVersion()));
                if (response != null && response.getData() != null && !response.getStatus() && response.getData().getMessage() != null) {
                    eligibilityStateDTO.setKycMessage(response.getData().getMessage());
                }
            }
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.error("Too Many requests error");
            eligibilityStateDTO.setMaxCountReached(true);
            eligibilityStateDTO.setMessage("You've reached your daily limit for PAN input. Please try again after 24 hours");
        }
    }

    private void savePanDetailsInDB(LendingPancardDetails lendingPancardDetails, PanFetchKYCResponseDto.Data data, ScopeDataArgs scopeDataArgs) {
        if (!ObjectUtils.isEmpty(lendingPancardDetails)) {
            if (!ObjectUtils.isEmpty(data.getVerifiedName())) {
                CommonUtil.logDataMismatchIfApplicable("Pan Name",lendingPancardDetails.getMerchantId(),  data.getVerifiedName(), lendingPancardDetails.getName());
                lendingPancardDetails.setName(data.getVerifiedName());
            }
            if (!ObjectUtils.isEmpty(data.getPanNumber())) {
                CommonUtil.logDataMismatchIfApplicable("Pan Number",lendingPancardDetails.getMerchantId(), data.getPanNumber(), lendingPancardDetails.getPancardNumber());
                lendingPancardDetails.setPancardNumber(data.getPanNumber());
            }
            if (!ObjectUtils.isEmpty(data.getVerifiedDob())) {
                CommonUtil.logDataMismatchIfApplicable("DOB",lendingPancardDetails.getMerchantId(),data.getVerifiedDob(), lendingPancardDetails.getDob());
                lendingPancardDetails.setDob(data.getVerifiedDob());
            }
            lendingPancardDetails.setVersion(LendingConstants.PAN_VERIFICATION_VERSION);
        } else {
            lendingPancardDetails = new LendingPancardDetails(scopeDataArgs.getMerchant().getId(), data.getPanNumber(), data.getVerifiedName(), null, LendingConstants.PAN_VERIFICATION_VERSION, null, data.getVerifiedDob());
        }

        //Set aadhaarSeedingStatus in lendingPancardDetails
        String aadhaarSeedingStatus = getAadhaarSeedingStatus(scopeDataArgs.getMerchant().getId(), data.getPanNumber());
        if (!ObjectUtils.isEmpty(lendingPancardDetails.getAadhaarSeedingStatus())) {
            CommonUtil.logDataMismatchIfApplicable("Aadhaar Seeding Status", lendingPancardDetails.getMerchantId(), aadhaarSeedingStatus, lendingPancardDetails.getAadhaarSeedingStatus());
        }
        lendingPancardDetails.setAadhaarSeedingStatus(aadhaarSeedingStatus);
        lendingPancardDetailsDao.save(lendingPancardDetails);
    }

    private String getAadhaarSeedingStatus(Long merchantId, String panNumber) {
        List<KycDoc> panDocList = kycHandler.getPan(merchantId);
        if (!CollectionUtils.isEmpty(panDocList)) {
            KycDoc panDoc = panDocList.get(0);
            if (!panNumber.equals(panDoc.getDocIdentifier())) {
                log.error("Different PAN received from KYC for merchant {} : {}", merchantId, Arrays.toString(panDocList.toArray()));
                return null;
            }
            return panDoc.getAadhaarSeedingStatus();
        } else {
            return null;
        }
    }

    private String getMerchantPancardNumber(Long merchantId, LendingPancardDetails lendingPancardDetails) {
        String panCardNumber = kycHandler.getPanNumber(merchantId);
        if (StringUtils.isEmpty(panCardNumber)) {
            panCardNumber = !ObjectUtils.isEmpty(lendingPancardDetails) ? lendingPancardDetails.getPancardNumber() : null;
        }

        return panCardNumber;
    }

    @Override
    public LendingStateDTO<EligibilityStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        // save any request data if needed
        Experian experian = experianDao.getByMerchantId(scopeDataArgs.getMerchant().getId());
        EligibilityStateDTO eligibilityStateDTO = new EligibilityStateDTO();
        eligibilityStateDTO.setMerchant(scopeDataArgs.getMerchant());
        eligibilityStateDTO.setExperian(experian);
        //setting merchant Id
        eligibilityStateDTO.setMerchantId(scopeDataArgs.getMerchant().getId());
        if (eligibilityV3Service.eligibilityBaseChecksSuccess(scopeDataArgs.getLoanDetailsV3Request(), eligibilityStateDTO)) {
            eligibilityV3Service.savePanPinData(scopeDataArgs.getLoanDetailsV3Request(), eligibilityStateDTO);
        }

        GlobalLimitResponse scenapticGlobalLimit = apiGatewayService.getScenapticGlobalLimit(scopeDataArgs.getMerchant().getId(),
                null,scopeDataArgs.getLoanDetailsV3Request().getAppVersion(),
                false, false,false,
                null,false, EligibilityRequestSource.EASY_LOANS);
        if(!ObjectUtils.isEmpty(scenapticGlobalLimit)
                && !ObjectUtils.isEmpty(scenapticGlobalLimit.getData())
                && LoanDetailsConstant.UNDERWRITING_MASKED_MOBILE_EXCEPTION.equalsIgnoreCase(scenapticGlobalLimit.getErrorCode())
                && !ObjectUtils.isEmpty(scenapticGlobalLimit.getData().getMaskedMobiles())
        ){
            return new LendingStateDTO<>(eligibilityStateDTO,
                    LendingViewStates.MASKED_MOBILE, LendingViewStates.PAN_PIN_PAGE);

        }
        boolean isPlanSelectionFlow = false;
        if(emiUtils.isEmiFlowEnabled()){
            EmiDashboardResponse emiDashboardResponse = emiDashboardService.getDashboardResponse(
                    eligibilityStateDTO.getMerchantId(), scopeDataArgs.getToken());
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(
                    eligibilityStateDTO.getMerchantId());
            if(emiUtils.isActive(emiDashboardResponse)){
                log.warn("merchant is in wrong flow, emi loan is active for the merchant: {}", scopeDataArgs.getMerchant().getId());
                loanDashboardService.deleteLoanDashboardCache(scopeDataArgs.getMerchant().getId());
                return new LendingStateDTO<>(null, null, LendingViewStates.PAN_PIN_PAGE);
            }
            isPlanSelectionFlow = emiUtils.isEligibleForPlanSelectionPage(emiDashboardResponse, lendingRiskVariables);
        }

        LendingViewStates lendingViewStates;
        if (loanUtil.isApplicableForAggregationFlowV2(scopeDataArgs.getMerchant().getId(), null)) {
            // When aggregation flow is applicable, use OFFER_EVALUATION_PAGE instead of OFFER_PAGE
            lendingViewStates = isPlanSelectionFlow ? LendingViewStates.PLAN_SELECTION_PAGE : LendingViewStates.OFFER_EVALUATION_PAGE;
        } else {
            // When aggregation flow is not applicable, keep using OFFER_PAGE
            lendingViewStates = isPlanSelectionFlow ? LendingViewStates.PLAN_SELECTION_PAGE : LendingViewStates.OFFER_PAGE;
        }

        LendingStateDTO<EligibilityStateDTO> lendingStateDTO = new LendingStateDTO<>(eligibilityStateDTO, lendingViewStates, LendingViewStates.PAN_PIN_PAGE);
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(scopeDataArgs.getMerchant().getId());
        if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
            funnelService.submitEventV3(scopeDataArgs.getMerchant().getId(), null, null,
                    FunnelEnums.StageId.PAN_PIN_PAGE, FunnelEnums.StageEvent.SUBMITTED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
        else{
            funnelService.submitEvent(scopeDataArgs.getMerchant().getId(), null, null,
                    FunnelEnums.StageId.PAN_PIN_PAGE, FunnelEnums.StageEvent.SUBMITTED, LocalDateTime.now().toString());
        }
        return lendingStateDTO;
    }
}
