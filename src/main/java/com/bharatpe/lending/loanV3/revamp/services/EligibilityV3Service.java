package com.bharatpe.lending.loanV3.revamp.services;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.EligibleLoan;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.enums.RiskSegment;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.enums.CleverTapEvents;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.Eligibility;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;
import com.bharatpe.lending.loanV3.revamp.dto.EligibilityStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LoanDetailsV3Request;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.CleverTapEventService;
import com.bharatpe.lending.service.IEdiModelAssignment;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class EligibilityV3Service {
    @Autowired
    private LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    MerchantSummaryHandler merchantSummaryHandler;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    IEdiModelAssignment iEdiModelAssignment;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Value("${edi.assignment.model:false}")
    Boolean assignEdiModelFromModelAssignmentEngine;

    @Value("${abfl.rollout.percent:10}")
    Integer rolloutAbflPercent;

    @Value("${eligibility.refresh.window:1}")
    int eligibilityRefreshWindow;

    @Value("${new.eligibility.refresh.window:1}")
    int newEligibilityRefreshWindow;

    @Value("${new.eligibility.refresh.window.rollout.percent:0}")
    Integer newEligibilityRefreshWindowRolloutPercent;

    @Value("${club.eligible.loan.cache:true}")
    Boolean clubEligibleLoanCache;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Autowired
    CleverTapEventService cleverTapEventService;

    @Autowired
    LendingCache lendingCache;

    public boolean eligibilityBaseChecksSuccess(LoanDetailsV3Request request, EligibilityStateDTO eligibilityStateDTO) {

        String kycPancard = kycHandler.getPanNumber(eligibilityStateDTO.getMerchant().getId());
        eligibilityStateDTO.setKycPancard(kycPancard);
        if (eligibilityStateDTO.getExperian() == null && (request == null || request.getPancard() == null || request.getPincode() == null)) {
            log.info("Invalid request to eligibility for merchant:{}", eligibilityStateDTO.getMerchant().getId());
            return false;
        }
        MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(eligibilityStateDTO.getMerchant().getId());
        if (ObjectUtils.isEmpty(merchantResponseDTO)) {
            log.info("merchant summary request failed for merchant:{}", eligibilityStateDTO.getMerchant().getId());
            return false;
        }
        eligibilityStateDTO.setMerchantResponseDTO(merchantResponseDTO);
        return true;
    }

    public void savePanPinData(LoanDetailsV3Request request, EligibilityStateDTO eligibilityStateDTO) {
        Experian experian = eligibilityStateDTO.getExperian();
        BasicDetailsDto merchant = eligibilityStateDTO.getMerchant();
        MerchantResponseDTO merchantResponseDTO = eligibilityStateDTO.getMerchantResponseDTO();
        if (experian == null) {
            experian = experianDao.save(new Experian(merchant.getId(),
                    null,
                    merchant.getLatitude() != null && merchant.getLatitude() <= 90 ? merchant.getLatitude() : null,
                    merchant.getLongitude() != null && merchant.getLongitude() <= 90 ? merchant.getLongitude() : null,
                    0,
                    request.getPancard(),
                    (merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null) ? merchantResponseDTO.getBpScore() : 0D,
                    0,
                    Integer.valueOf(request.getPincode())));

            if (Boolean.TRUE.equals(request.getRteFlag())) {
                try {
                    eligibilityStateDTO.setExperian(experian);
                    refreshEligibility(request, eligibilityStateDTO, false);
                    String mileStoneCacheKey = RTEConstants.RTE_PROGRAM_DETAILS_CACHE + merchant.getId();
                    Object mileStoneCacheResponse = lendingCache.get(mileStoneCacheKey);
                    if (!ObjectUtils.isEmpty(mileStoneCacheResponse)) {
                        lendingCache.delete(mileStoneCacheKey);
                    }

                } catch (BureauCallMaskedApiException e) {
                        log.error("exception in setting Experian data for merchantId {} in milestone journey, e{}",merchant.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));                }
            }


        } else if (request != null && request.getPancard() != null
                && request.getPincode() != null
                && !experian.getPancardNumber().equalsIgnoreCase(request.getPancard())) {
            log.info("Found different pancard for merchant:{}, old pancard:{}, new pancard:{}", merchant.getId(), experian.getPancardNumber(), request.getPancard());
            experian.setPancardNumber(request.getPancard());
            experian.setBpScore((merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null) ? merchantResponseDTO.getBpScore() : 0D);
            experian.setPincode(Integer.valueOf(request.getPincode()));
            experian.setResponse(null);
            experian.setBureau(null);
            experian.setHitId(null);
            experian.setReportDate(null);
            experian.setExperianScore(null);
            experianDao.save(experian);
            checkAndSaveIfMerchantISClubV2Member(eligibilityStateDTO);
            try {
                refreshEligibility(request, eligibilityStateDTO, false);
            } catch (BureauCallMaskedApiException e) {
                log.error("bureau call masked api ex {}, {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
            }
        } else if (request != null && request.getPincode() != null) {
            if(Objects.nonNull(experian.getPincode()) && (experian.getPincode().equals(Integer.valueOf(request.getPincode())))){
                log.info("pincode value in request is same as experian for {} : {}, {}", merchant.getId(), request.getPincode(), experian.getPincode());
            }
            else{
                log.info("updating experian pincode:{} for merchant:{}", request.getPincode(), merchant.getId());
                experian.setPincode(Integer.valueOf(request.getPincode()));
                experianDao.save(experian);
                checkAndSaveIfMerchantISClubV2Member(eligibilityStateDTO);
                try {
                    eligibilityStateDTO.setPincodeChanged(true);
                    refreshEligibility(request, eligibilityStateDTO, false);
                } catch (BureauCallMaskedApiException e) {
                    log.error("bureau call masked api ex {}, {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
                }
            }

        }
        if (!easyLoanUtil.isDummyMerchant(merchant.getId())) {
            if (!StringUtils.isEmpty(eligibilityStateDTO.getKycPancard()) && !eligibilityStateDTO.getKycPancard().equalsIgnoreCase(experian.getPancardNumber())) {
                log.info("Pancard mismatch for merchant:{}, kyc:{}, experian:{}", merchant.getId(), eligibilityStateDTO.getKycPancard(), experian.getPancardNumber());
                experian.setPancardNumber(eligibilityStateDTO.getKycPancard());
                experian.setResponse(null);
                experian.setBureau(null);
                experian.setHitId(null);
                experian.setReportDate(null);
                experian.setExperianScore(null);
                experianDao.save(experian);
            }
        }
        eligibilityStateDTO.setHasExperian(true);
    }

    private void setEdiModel(EligibilityStateDTO eligibilityStateDTO) {
        eligibilityStateDTO.setEdiDaysModel(6);
        if (loanUtil.isInternalMerchant(eligibilityStateDTO.getMerchant().getId()) || easyLoanUtil.percentScaleUp(eligibilityStateDTO.getMerchant().getId(), rolloutAbflPercent)) {
            eligibilityStateDTO.setEdiDaysModel(7);
        }

        if (loanUtil.isInternalMerchant(eligibilityStateDTO.getMerchant().getId()) || assignEdiModelFromModelAssignmentEngine) {
            eligibilityStateDTO.setEdiDaysModel(iEdiModelAssignment.assignModel(eligibilityStateDTO.getMerchant().getId()).getNoOfEdiDaysInAWeek());
        }
    }

    private void checkAndSaveIfMerchantISClubV2Member(EligibilityStateDTO eligibilityStateDTO) {
        eligibilityStateDTO.setClubV2Member(apiGatewayService.checkClubV2(eligibilityStateDTO.getMerchant().getId()));
    }

    private void fetchPreComputedEligibility(EligibilityStateDTO eligibilityStateDTO) {
        int updatedEligibilityRefreshWindow = easyLoanUtil.percentScaleUp(eligibilityStateDTO.getMerchant().getId(), newEligibilityRefreshWindowRolloutPercent) ? newEligibilityRefreshWindow : eligibilityRefreshWindow;
        if(updatedEligibilityRefreshWindow <= 0){
            log.info("not picking offer from eligible loan for {}", eligibilityStateDTO.getMerchant().getId());
            return;
        }
        EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdAndLoanTypeNotTopup(eligibilityStateDTO.getMerchant().getId());
        Date dateWindow = dateTimeUtil.getDatePlusDays(dateTimeUtil.getCurrentDate(), -24 * updatedEligibilityRefreshWindow);
        log.info("merchant is: {} clubV2 member: {}", eligibilityStateDTO.getMerchant().getId(), eligibilityStateDTO.getClubV2Member());
        Eligibility eligibility = null;
        if (!ObjectUtils.isEmpty(eligibleLoan) && eligibleLoan.getCreatedAt().after(dateWindow) && !(eligibilityStateDTO.getClubV2Member() && clubEligibleLoanCache)) {
            log.info("Eligible offers exist for merchant:{}", eligibilityStateDTO.getMerchant().getId());
            // make new method and eligible loan
            eligibility = loanDetailsServiceV2.createEligibility(eligibleLoan, eligibilityStateDTO.getMerchant().getId());
            if (eligibility != null) {
                log.info("eligibility is not null for merchant: {}", eligibilityStateDTO.getMerchant().getId());
                eligibilityStateDTO.setEligibility(eligibility);
            } else {
                log.info("eligibility is null for merchant: {}", eligibilityStateDTO.getMerchant().getId());
            }
        } else {
            log.info("after the date window for merchant: {}", eligibilityStateDTO.getMerchant().getId());
        }
    }

    public void refreshEligibility(LoanDetailsV3Request request, EligibilityStateDTO eligibilityStateDTO, boolean skipEligibleLoanDbEntryCreation) throws BureauCallMaskedApiException {
        Eligibility eligibility = null;
        log.info("eligibilityStateDTO.getMerchant().getId() {}", eligibilityStateDTO);
        log.info("request is {}", request);
        GlobalLimitResponse globalLimitResponse = requestForEligibility(request, eligibilityStateDTO);
        MutableBoolean isDerog = new MutableBoolean(false);
        Double eligibleAmount = 0D;
        if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
            log.info("Global limit for merchant:{} is {}", eligibilityStateDTO.getMerchant().getId(), globalLimitResponse.getData().getGlobalLimit());
            eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
            isDerog.setValue(globalLimitResponse.getData().isDerog());
            if(RiskSegment.REPEAT.name().equalsIgnoreCase(globalLimitResponse.getData().getRiskSegment()) &&
                    Objects.nonNull(globalLimitResponse.getData().getPreApprovedLoan()) &&
                            globalLimitResponse.getData().getPreApprovedLoan()
            ){
                eligibilityStateDTO.setIsPreapprovedRepeatLoan(true);
            }
        }
        if (eligibleAmount > 0D) {
            log.info("Eligibility found for merchant:{}", eligibilityStateDTO.getMerchant().getId());
            EligibleLoan eligibleLoan = loanDetailsServiceV2.recomputeEligibleLoan(globalLimitResponse, null, eligibilityStateDTO.getMerchant().getId(), skipEligibleLoanDbEntryCreation);
            eligibility = loanDetailsServiceV2.createEligibility(eligibleLoan, eligibilityStateDTO.getMerchant().getId());
        }
        if (eligibility != null) {
            eligibilityStateDTO.setEligibility(eligibility);
            return;
        }
        fetchInEligibilityData(eligibilityStateDTO, isDerog, globalLimitResponse);
    }

    public void refreshEligibilityWithoutScopeRequest(LoanDetailsV3Request request, EligibilityStateDTO eligibilityStateDTO) throws BureauCallMaskedApiException {
        Eligibility eligibility = null;
        GlobalLimitResponse globalLimitResponse = requestForEligibility(request, eligibilityStateDTO);
        MutableBoolean isDerog = new MutableBoolean(false);
        Double eligibleAmount = 0D;
        if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
            log.info("Global limit for merchant:{} is {}", eligibilityStateDTO.getMerchant().getId(), globalLimitResponse.getData().getGlobalLimit());
            eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
            isDerog.setValue(globalLimitResponse.getData().isDerog());
        }
        if (eligibleAmount > 0D) {
            log.info("Eligibility found for merchant:{}", eligibilityStateDTO.getMerchant().getId());
            EligibleLoan eligibleLoan = loanDetailsServiceV2.recomputeEligibleLoan(globalLimitResponse, null, eligibilityStateDTO.getMerchant().getId(), true);
            eligibility = loanDetailsServiceV2.createEligibility(eligibleLoan, eligibilityStateDTO.getMerchant().getId());
        }
        if (eligibility != null) {
            eligibilityStateDTO.setEligibility(eligibility);
            return;
        }
//        fetchInEligibilityData(eligibilityStateDTO, isDerog, globalLimitResponse);
    }

    public void fetchInEligibilityData(EligibilityStateDTO eligibilityStateDTO, MutableBoolean isDerog, GlobalLimitResponse globalLimitResponse) {
        log.info("Eligibility not found for merchant:{}", eligibilityStateDTO.getMerchant().getId());
        eligibilityStateDTO.setIneligible(loanDetailsServiceV2.getIneligibleReason(eligibilityStateDTO.getMerchant().getId(), isDerog, eligibilityStateDTO.getExperian().getPincode(), globalLimitResponse));
        eligibilityStateDTO.setChangeBankAccount(!loanUtil.isEnachBank(eligibilityStateDTO.getMerchant().getId()));
        eligibilityStateDTO.setBureauExceptionFlag(loanUtil.checkBureauResponse(globalLimitResponse));
    }

    public GlobalLimitResponse requestForEligibility(LoanDetailsV3Request request, EligibilityStateDTO eligibilityStateDTO) throws BureauCallMaskedApiException {
        GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(eligibilityStateDTO.getMerchant().getId(), null,
                request.getAppVersion(), eligibilityStateDTO.getClubV2Member(), request.getMappedMobile(), request.getStageOneHitId(), request.getStageTwoHitId(),
                request.getSkipBureau(), request.getSkipMaskedMobileException(),null,null,true,null, eligibilityStateDTO,false, EligibilityRequestSource.EASY_LOANS);

        if(globalLimitResponse.getData().getPreApprovedLoan()){
            HashMap<String, String> cleverTapEvtData = new HashMap<String, String>() {{
                put("globalLimit", globalLimitResponse.getData().getGlobalLimit().toString());
                put("riskSegment", globalLimitResponse.getData().getRiskSegment());
                put("beneficiaryName", eligibilityStateDTO.getMerchant().getBeneficiaryName());
            }};
            executorService.execute(() -> cleverTapEventService.sendClevertapEvent(
                    CleverTapEvents.LOAN_PREAPPROVED_BE.name(), cleverTapEvtData, eligibilityStateDTO.getMerchant().getMid()
                    ));
        }
        return globalLimitResponse;
    }

    public void runBaseChecksSavePANPINDataAndFetchEligibility(LoanDetailsV3Request request, EligibilityStateDTO eligibilityStateDTO) throws BureauCallMaskedApiException {
        if (!eligibilityBaseChecksSuccess(request, eligibilityStateDTO)) {
            return;
        }
        savePanPinData(request, eligibilityStateDTO);
        checkAndSaveIfMerchantISClubV2Member(eligibilityStateDTO);
        fetchPreComputedEligibility(eligibilityStateDTO);
        if (null != eligibilityStateDTO.getEligibility()) {
            setEdiModel(eligibilityStateDTO);
            return;
        }
        refreshEligibility(request, eligibilityStateDTO, false);
        if (null != eligibilityStateDTO.getEligibility()) {
            setEdiModel(eligibilityStateDTO);
        }
        // TODO: 22/05/23 update pin and pan in response
    }

    public void fetchEligibility(LoanDetailsV3Request request, EligibilityStateDTO eligibilityStateDTO) {
        checkAndSaveIfMerchantISClubV2Member(eligibilityStateDTO);
        fetchPreComputedEligibility(eligibilityStateDTO);
        checkForPreapprovedRepeatOffer(eligibilityStateDTO);
        if (null != eligibilityStateDTO.getEligibility()) {
            setEdiModel(eligibilityStateDTO);
            return;
        }
        try {
            refreshEligibility(request, eligibilityStateDTO, true);
        } catch (BureauCallMaskedApiException e) {
            log.error("bureau call masked api ex {}", e);
        }
        if (null != eligibilityStateDTO.getEligibility()) {
            setEdiModel(eligibilityStateDTO);
        }
    }

    public void fetchEligibilityWithoutScopeRequest(LoanDetailsV3Request request, EligibilityStateDTO eligibilityStateDTO) {
        checkAndSaveIfMerchantISClubV2Member(eligibilityStateDTO);
        fetchPreComputedEligibility(eligibilityStateDTO);
        if (null != eligibilityStateDTO.getEligibility()) {
            setEdiModel(eligibilityStateDTO);
            return;
        }
        try {
            refreshEligibilityWithoutScopeRequest(request, eligibilityStateDTO);
        } catch (BureauCallMaskedApiException e) {
            log.error("bureau call masked api ex {}", e);
        }
        if (null != eligibilityStateDTO.getEligibility()) {
            setEdiModel(eligibilityStateDTO);
        }
    }

    private void checkForPreapprovedRepeatOffer(EligibilityStateDTO eligibilityStateDTO){
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(eligibilityStateDTO.getMerchant().getId());
        if(ObjectUtils.isEmpty(lendingRiskVariables)){
            return;
        }
        String pilotIdentifier = lendingRiskVariables.getPilotIdentifier();
        if(!ObjectUtils.isEmpty(pilotIdentifier) && pilotIdentifier.contains(LoanDetailsConstant.PREAPPROVED_REPEAT_LOAN_IDENTIFIER)){
            log.info("loan request is pre-approved repeat for {}", eligibilityStateDTO.getMerchant().getId());
            eligibilityStateDTO.setIsPreapprovedRepeatLoan(true);
        }
    }
}
