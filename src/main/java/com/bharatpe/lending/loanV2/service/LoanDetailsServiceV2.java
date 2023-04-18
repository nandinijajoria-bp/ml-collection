package com.bharatpe.lending.loanV2.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.dto.NachableBanksDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.RejectionReason;
import com.bharatpe.lending.common.enums.RejectionStage;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.PincodeCityStateMappingDTO;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.Deeplink;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.loanV2.dto.CreditScoreReportDetailDTO;
import com.bharatpe.lending.loanV2.dto.LoanAndCreditCardDetailDTO;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.loanV2.handlers.BureauHandler;
import com.bharatpe.lending.service.*;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.*;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LoanDetailsServiceV2 {

    @Autowired(required = false)
    BureauHandler bureauHandler;

    @Autowired
    LendingMerchantReferencesDao lendingMerchantReferencesDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LendingCityCreditScoreDao lendingCityCreditScoreDao;

    @Autowired
    DsHandler dsHandler;

//    @Autowired
//    MerchantDao merchantDao;
    @Autowired
    ExperianDao experianDao;

//    @Autowired
//    CreditLineMerchantDao creditLineMerchantDao;

    @Autowired
    LendingMerchantPermissionsDao lendingMerchantPermissionsDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LendingResubmitTaskDao lendingResubmitTaskDao;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    EnachErrorHandingService enachErrorHandingService;

    @Autowired
    LendingDisbursalStageDao lendingDisbursalStageDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Autowired
    LendingMerchantDetailsDao lendingMerchantDetailsDao;

    @Autowired
    FosService fosService;

    @Value("${eligibility.refresh.window:1}")
    int eligibilityRefreshWindow;

    @Value("${loan.details.refresh.window:15}")
    int loanDetailsRefreshWindow;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MerchantSummaryHandler merchantSummaryHandler;


    @Value("${club.eligible.loan.cache:true}")
    Boolean clubEligibleLoanCache;

    @Value("${merchant.references.min.score}")
    Integer minScore;

    @Value("${lender.assign.rollout}")
    Integer lenderAssignmentNewFlowRollOutPercent;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    public static List<Long> exceptedMerchantList = Arrays.asList(123455L, 1334555L);
    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Autowired
    FunnelService funnelService;

    @Autowired
    CleverTapEventService cleverTapEventService;

    @Value("${abfl.rollout.percent:10}")
    Integer rolloutAbflPercent;

    private static final List<KycDocType> kycMandatoryDocs = Arrays.asList(KycDocType.PAN_NO, KycDocType.PAN_CARD, KycDocType.SELFIE, KycDocType.POA);

    public ApiResponse<?> getLoanDetails(LoanDetailsRequest request, BasicDetailsDto merchant, String token) {
        try {
            if (Objects.nonNull(request) && Objects.nonNull(request.getPancard()) && Objects.nonNull(request.getPincode())) {
                if (Objects.nonNull(merchant.getId())) {
                    String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
                    log.info("deleting cached key of loan details where pan, pin is not null for merchant: {}", merchant.getId());
                    if (Objects.nonNull(lendingCache.get(loanDetailsCacheKey))) {
                        lendingCache.delete(loanDetailsCacheKey);
                    }
                    String globalDetailsCacheKey = "LENDING_GLOBAL_DETAILS_" + merchant.getId();
                    if (Objects.nonNull(lendingCache.get(globalDetailsCacheKey))) {
                        lendingCache.delete(globalDetailsCacheKey);
                    }
                } else {
                    log.info("merchant id not found in get loan details flow");
                }
            }
            String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
            Object loanDetailsCacheResponse = lendingCache.get(loanDetailsCacheKey);
            String initiateKycCallCacheKey = LendingConstants.INITIATE_KYC_CACHE_KEYWORD + merchant.getId();
            Object initiateKycCallCache = lendingCache.get(initiateKycCallCacheKey);
            if (!ObjectUtils.isEmpty(loanDetailsCacheResponse)
//                    && request.isCachedData()
            ) {
                if(ObjectUtils.isEmpty(initiateKycCallCache)){
                    log.info("returning loan details response from cache for {}", merchant.getId());
                    LoanDetailsResponse loanDetailsResponse = objectMapper.readValue((String) loanDetailsCacheResponse, LoanDetailsResponse.class);
                    loanDetailsResponse.setSource("CACHE");
                    return new ApiResponse<>(loanDetailsResponse);
                }
                else lendingCache.delete(initiateKycCallCacheKey);
            }
            LoanDetailsResponse loanDetailsResponse = new LoanDetailsResponse();
            loanDetailsResponse.setMerchantId(merchant.getId());
//            if (isCreditLineMerchant(merchant)) {
//                log.info("credit line merchant: {}", merchant.getId());
//                loanDetailsResponse.setCreditLineDeeplink("bharatpe://dynamic?key=credit-line");
//                return new ApiResponse<>(loanDetailsResponse);
//            }
            if ("ORGANIZED".equalsIgnoreCase(merchant.getMerchantType())) {
                log.info("organized merchant: {}", merchant.getId());
                return new ApiResponse<>(loanDetailsResponse);
            }
            // dummy merchant flag exposed to FE
            loanDetailsResponse.setDummyMerchant(easyLoanUtil.isDummyMerchant(merchant.getId()));
            loanDetailsResponse.setBankLinked(loanUtil.isBankAccLinked(merchant.getId()));
            loanDetailsResponse.setMerchantName(loanUtil.getBeneficiaryName(merchant.getId()));
            String bpMembershipKey = "BP_CLUB_MEMBERSHIP_" + merchant.getId();
            Object bpCLubResponse = lendingCache.get(bpMembershipKey);
            if (ObjectUtils.isEmpty(bpCLubResponse)) {
                Boolean isBpClubMember = apiGatewayService.eligibleForProcessingFee(merchant.getId());
                loanDetailsResponse.setBpClubMember(isBpClubMember);
                AddCacheDto addCacheDto = new AddCacheDto();
                addCacheDto.setKey(bpMembershipKey);
                addCacheDto.setValue(isBpClubMember);
                addCacheDto.setTtl(7 * 24);
                lendingCache.add(addCacheDto);
            } else {
                loanDetailsResponse.setBpClubMember((Boolean) bpCLubResponse);
            }
            loanDetailsResponse.setRepeatLoan(loanUtil.isRepeatLoan(merchant.getId()));
            loanDetailsResponse.setAccountDetails(loanUtil.getAccountDetails(merchant.getId()));
            populateBusinessDetails(merchant.getId(), loanDetailsResponse);
            if (loanUtil.hasActiveLoan(merchant)) {
                log.info("active loan merchant:{}", merchant.getId());
                LendingApplication topupApplication = lendingApplicationDao.findOpenTopUpApplication(merchant.getId(), "TOPUP");
                if(!ObjectUtils.isEmpty(topupApplication) && !"rejected".equals(topupApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(topupApplication.getLoanDisbursalStatus())){
                    Experian experian = experianDao.getByMerchantId(merchant.getId());
                    boolean isIOS = request != null && request.isIOS();
                    LoanApplicationDetails topupApplicationDetails = setApplicationDetails(loanDetailsResponse, topupApplication, token, isIOS, experian, merchant);
                    loanDetailsResponse.setTopupLoanApplication(topupApplicationDetails);
                }
                loanDetailsResponse.setActiveLoan(true);
                return new ApiResponse<>(loanDetailsResponse);
            }
            Experian experian = experianDao.getByMerchantId(merchant.getId());
            if (experian != null) {
                loanDetailsResponse.setPancard(experian.getPancardNumber());
                loanDetailsResponse.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
                loanDetailsResponse.setHasExperian(true);
            }

            loanDetailsResponse.setEligibleForCallback(checkEligibilityForCallback(merchant.getId()));
            LendingPaymentSchedule lendingPaymentSchedule1 = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), "INACTIVE");
            if (!ObjectUtils.isEmpty(lendingPaymentSchedule1)) {
                loanDetailsResponse.setIneligible(RejectionReason.LOW_TRANSACTION.getReason());
                loanDetailsResponse.setKycStatus(KycStatus.APPROVED);
                return new ApiResponse<>(loanDetailsResponse);
            }
            LendingApplication draftApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchant.getId(),"draft");
            String deReferencesCacheKey = LendingConstants.GET_MERCHANTS_REFERENCES_CACHE_KEY + merchant.getId();
            if(draftApplication != null) {
                if(lendingMerchantReferencesDao.findByMerchantIdAndApplicationId(merchant.getId(), draftApplication.getId()).isEmpty() && Objects.isNull(lendingCache.get(deReferencesCacheKey))) {
                    executorService.submit(() -> {
                        log.info("Again caching MerchantReferences from de of merchantId : {} inside",merchant.getId());
                        loanUtil.callingDeForReferences(merchant.getId(),draftApplication);
                    });
                }
            }
            Optional<LendingPaymentSchedule> lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestClosedLoan(merchant.getId());
            LendingApplication openApplication;
            if (lendingPaymentSchedule.isPresent()) {
                openApplication = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullAndPaymentScheduleStatusClosedOrderByIdDesc(merchant.getId(), lendingPaymentSchedule.get().getCreatedAt());
            } else {
                openApplication = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(merchant.getId());
            }
//            if (ObjectUtils.isEmpty(openApplication)) {
//                openApplication = draftApplication;
//            }
            if (openApplication != null) {
                log.info("open application for merchant:{}", merchant.getId());
                //with validAfter timestamp
                LendingApplicationKycDetails lendingApplicationKycDetails = null;
                if(easyLoanUtil.percentScaleUp(openApplication.getMerchantId(), lenderAssignmentNewFlowRollOutPercent)){
                    lendingApplicationKycDetails=lendingApplicationKycDetailsDao.findSuccessKycDetails(openApplication.getMerchantId(), openApplication.getLender());
                }
                if(!loanUtil.isRepeatLoan(openApplication.getMerchantId()) ||
                        (ObjectUtils.isEmpty(lendingApplicationKycDetails)
                        )){
                    lendingApplicationKycDetails=lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(openApplication.getId());
                } else if("draft".equalsIgnoreCase(openApplication.getStatus()) && !KycStatus.APPROVED.name().equalsIgnoreCase(openApplication.getCkycStatus())) {
                    loanDetailsResponse.setKycDone(true);
                    openApplication.setCkycStatus(KycStatus.APPROVED.name());
                    openApplication.setCkycDate(new Date());
                    lendingApplicationDao.save(openApplication);
                    LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(openApplication.getId());
                    if(ObjectUtils.isEmpty(lendingApplicationDetails)){
                        lendingApplicationDetails = new LendingApplicationDetails();
                        lendingApplicationDetails.setApplicationId(openApplication.getId());
                    }
                    lendingApplicationDetails.setIsKycSkip(Boolean.TRUE);
                    lendingApplicationDetailsDao.save(lendingApplicationDetails);
                }
                Date validAfterDate;
                if(ObjectUtils.isEmpty(lendingApplicationKycDetails)){
                    log.info("Unable to fetch entry from KYC table for {}", openApplication.getId());
                    LendingApplicationKycDetails lendingApplicationKycDetails1 = new LendingApplicationKycDetails();
                    lendingApplicationKycDetails1.setMerchantId(merchant.getId());
                    lendingApplicationKycDetails1.setApplicationId(openApplication.getId());
                    lendingApplicationKycDetails1.setLender(openApplication.getLender());
                    lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails1);
                    validAfterDate = lendingApplicationKycDetails1.getCreatedAt();
                }
                else{
                    validAfterDate = lendingApplicationKycDetails.getCreatedAt();
                }
                List<KycDoc> kycDocs = kycHandler.getKycDoc(merchant.getId(), validAfterDate, LendingConstants.POA_PROVIDER);
                loanDetailsResponse.setKycStatus(kycHandler.getKycStatus(kycDocs, merchant.getId()).getKycStatus());

                if(KycStatus.APPROVED.equals(loanDetailsResponse.getKycStatus())){
                    updateKycDetails(merchant, validAfterDate, LendingConstants.POA_PROVIDER, lendingApplicationKycDetails, kycDocs);
                }

                updateCkycStatus(openApplication, experian);
                if (!ObjectUtils.isEmpty(openApplication.getAgreementAt())) {
                    log.info("Kyc status for application: {} is {}", openApplication.getId(), loanDetailsResponse.getKycStatus());
                    loanDetailsResponse.setKycStatus(KycStatus.APPROVED);
                }
                boolean isIOS = request != null && request.isIOS();
                List<LendingMerchantReferences> referencesList = lendingMerchantReferencesDao.findByMerchantIdAndApplicationId(merchant.getId(),openApplication.getId());
                log.info("ReferenceList: {}",Arrays.toString(referencesList.toArray()));
                if(!referencesList.isEmpty()) {
                    loanDetailsResponse.setShowReferencePage(false);
                }
                LoanApplicationDetails loanApplicationDetails = setApplicationDetails(loanDetailsResponse, openApplication, token, isIOS, experian,merchant);
                loanDetailsResponse.setLoanApplication(loanApplicationDetails);
                if (loanDetailsResponse.getLoanApplication() != null && StringUtils.isEmpty(loanDetailsResponse.getLoanApplication().getReapply())) {
                    //if no reapply then dont check eligibility
                    cacheLoanDetailsData(loanDetailsResponse, loanDetailsCacheKey, loanDetailsRefreshWindow);
                    return new ApiResponse<>(loanDetailsResponse);
                }
            }else{
                loanDetailsResponse.setKycStatus(kycHandler.getKycStatus(merchant.getId()).getKycStatus());
            }


            checkEligibility(loanDetailsResponse, request, experian, merchant);
            cacheLoanDetailsData(loanDetailsResponse, loanDetailsCacheKey, loanDetailsRefreshWindow);
            log.info("returning response from database");
            return new ApiResponse<>(loanDetailsResponse);
        } catch (Exception e) {
            log.error("Exception in loan details service v2 for merchant: {} {} {}", merchant.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    public void updateKycDetails(BasicDetailsDto merchant, Date validAfterDate, String provider, LendingApplicationKycDetails lendingApplicationKycDetails, List<KycDoc> kycDocs){
        boolean selfieValid = false;
        boolean aadharValid = false;
        boolean aadharDigilocker = false;
        boolean panCardApproved = false;
        boolean panNoApproved = false;
        try {
            for (KycDoc kycDoc : kycDocs) {
                // Updating Kyc Details if Doc Type is approved and approved_at is null
                if (kycDoc.getDocType() != null && KycDocType.SELFIE.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                    lendingApplicationKycDetails.setSelfieUrl(kycDoc.getDocFrontImageUrl());
                    if(Objects.isNull(lendingApplicationKycDetails.getSelfieApprovedAt()))lendingApplicationKycDetails.setSelfieApprovedAt(new Date());
                    selfieValid=true;
                    log.info("Selfie is valid for : {}", merchant.getId());
                } else if (kycDoc.getDocType() != null && KycDocType.POA.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                    aadharValid = true;
                    log.info("Aadhar is valid for : {}", merchant.getId());
                    if (kycDoc.getSubDocType() != null && KycDocType.EKYC.equals(kycDoc.getSubDocType())) {
                        lendingApplicationKycDetails.setAadharIdentifier(kycDoc.getDocIdentifier());
                        lendingApplicationKycDetails.setAadharAddress(kycDoc.getAddress());
                        if(Objects.isNull(lendingApplicationKycDetails.getAadharApprovedAt()))lendingApplicationKycDetails.setAadharApprovedAt(new Date());
                        if (!ObjectUtils.isEmpty(kycDoc.getDigioXml())) {
                            lendingApplicationKycDetails.setAadharXml(kycDoc.getDigioXml());
                        }
                        aadharDigilocker = true;
                        log.info("Aadhar is digilocker approved for : {}", merchant.getId());
                    }
                } else if (kycDoc.getDocType() != null && KycDocType.PAN_CARD.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                    lendingApplicationKycDetails.setPanUrl(kycDoc.getDocFrontImageUrl());
                    if(Objects.isNull(lendingApplicationKycDetails.getPanApprovedAt()))lendingApplicationKycDetails.setPanApprovedAt(new Date());
                    panCardApproved = true;
                    log.info("Pan Card is valid for : {}", merchant.getId());
                } else if (kycDoc.getDocType() != null && KycDocType.PAN_NO.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                    lendingApplicationKycDetails.setPan(kycDoc.getDocIdentifier());
                    panNoApproved = true;
                    log.info("Pan No is valid for : {}", merchant.getId());
                }
            }
            if (selfieValid && aadharValid && aadharDigilocker && panCardApproved && panNoApproved) {
                lendingApplicationKycDetails.setConsentDate(new Date());
                lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
                log.info("Kyc details verified for merchant : {}", merchant.getId());
                executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_KYC_VERIFIED_BE.name(), null, merchant.getMid()));
                funnelService.submitEvent(merchant.getId(), null,lendingApplicationKycDetails.getApplicationId(),
                        FunnelEnums.StageId.KYC, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString());
            }
        } catch (Exception e) {
            log.error("Exception in updating kyc details for merchant:{}", merchant.getId(), e);
        }
    }

    public ApiResponse<?> getLoanDashboardDetails(BasicDetailsDto merchant, Boolean isIos) {
        LoanDetailsResponse loanDashBoardDTO = new LoanDetailsResponse();
        try {
            if ("ORGANIZED".equalsIgnoreCase(merchant.getMerchantType())) {
                log.info("organized merchant: {}", merchant.getId());

                return new ApiResponse<>(loanDashBoardDTO);
            }
            loanDashBoardDTO.setKycStatus(kycHandler.getKycStatus(merchant.getId()).getKycStatus());
            loanDashBoardDTO.setDummyMerchant(easyLoanUtil.isDummyMerchant(merchant.getId()));
            loanDashBoardDTO.setBankLinked(loanUtil.isBankAccLinked(merchant.getId()));
            loanDashBoardDTO.setMerchantName(loanUtil.getBeneficiaryName(merchant.getId()));
            loanDashBoardDTO.setRepeatLoan(loanUtil.isRepeatLoan(merchant.getId()));
            loanDashBoardDTO.setAccountDetails(loanUtil.getAccountDetails(merchant.getId()));
            if (loanUtil.hasActiveLoan(merchant)) {
                log.info("active loan merchant:{}", merchant.getId());
                loanDashBoardDTO.setActiveLoan(true);
                return new ApiResponse<>(loanDashBoardDTO);
            }
            Experian experian = experianDao.getByMerchantId(merchant.getId());
            if (experian != null) {
                loanDashBoardDTO.setPancard(experian.getPancardNumber());
                loanDashBoardDTO.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
                loanDashBoardDTO.setHasExperian(true);
            }

            loanDashBoardDTO.setEligibleForCallback(checkEligibilityForCallback(merchant.getId()));

            Optional<LendingPaymentSchedule> lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestClosedLoan(merchant.getId());
            LendingApplication openApplication;
            if (!ObjectUtils.isEmpty(lendingPaymentSchedule)) {
                openApplication = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullAndPaymentScheduleStatusClosedOrderByIdDesc(merchant.getId(), lendingPaymentSchedule.get().getCreatedAt());
            } else {
                openApplication = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(merchant.getId());
            }
            if (openApplication != null) {
                log.info("open application for merchant:{}", merchant.getId());
                //with validAfter timestamp
                LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(openApplication.getId());
                Date validAfterDate;
                if(ObjectUtils.isEmpty(lendingApplicationKycDetails)){
                    log.info("Unable to fetch entry from KYC table for {}", openApplication.getId());
                    LendingApplicationKycDetails lendingApplicationKycDetails1 = new LendingApplicationKycDetails();
                    lendingApplicationKycDetails1.setMerchantId(merchant.getId());
                    lendingApplicationKycDetails1.setApplicationId(openApplication.getId());
                    lendingApplicationKycDetails1.setLender(openApplication.getLender());
                    lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails1);
                    validAfterDate = lendingApplicationKycDetails1.getCreatedAt();
                }
                else{
                    validAfterDate = lendingApplicationKycDetails.getCreatedAt();
                }
                List<KycDoc> kycDocs = kycHandler.getKycDoc(merchant.getId(), validAfterDate, LendingConstants.POA_PROVIDER);
                loanDashBoardDTO.setKycStatus(kycHandler.getKycStatus(kycDocs, merchant.getId()).getKycStatus());

                if(KycStatus.APPROVED.equals(loanDashBoardDTO.getKycStatus())){
                    updateKycDetails(merchant, validAfterDate, LendingConstants.POA_PROVIDER, lendingApplicationKycDetails, kycDocs);
                }

                updateCkycStatus(openApplication, experian);
                if (!ObjectUtils.isEmpty(openApplication.getAgreementAt())) {
                    log.info("Kyc status for application: {} is {}", openApplication.getId(), loanDashBoardDTO.getKycStatus());
                    loanDashBoardDTO.setKycStatus(KycStatus.APPROVED);
                }
                boolean isIOS = isIos != null && isIos;
                setApplicationDetails(loanDashBoardDTO, openApplication, null, isIOS, experian, merchant);
                if (loanDashBoardDTO.getLoanApplication() != null && StringUtils.isEmpty(loanDashBoardDTO.getLoanApplication().getReapply())) {
                    //if no reapply then dont check eligibility
                    return new ApiResponse<>(loanDashBoardDTO);
                }
            }
            else {
                loanDashBoardDTO.setKycStatus(kycHandler.getKycStatus(merchant.getId()).getKycStatus());
            }
            return new ApiResponse<>(loanDashBoardDTO);
        } catch (Exception e) {
            log.error("Exception in loan dashboard service for merchant: {} {} {}", merchant.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    private void cacheLoanDetailsData(LoanDetailsResponse loanDetailsResponse, String key, int ttl) {
        try {
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(key);
            addCacheDto.setValue(objectMapper.writeValueAsString(loanDetailsResponse));
            addCacheDto.setTtl(ttl);
            lendingCache.add(addCacheDto, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("exception occured while caching loan details for {} !!", key);
        }
    }

    private void populateBusinessDetails(Long merchantId, LoanDetailsResponse loanDetailsResponse) {
        LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        if (Objects.nonNull(lendingMerchantDetails)) {
            loanDetailsResponse.setBusinessName(lendingMerchantDetails.getBusinessName());
            loanDetailsResponse.setBusinessCategory(lendingMerchantDetails.getBusinessCategory());
            loanDetailsResponse.setBusinessSubCategory(lendingMerchantDetails.getBusinessSubCategory());
        }
    }

    private void updateCkycStatus(LendingApplication openApplication, Experian experian) {
        if (!StringUtils.isEmpty(openApplication.getCkycId()) && ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())) {
            log.info("Checking verified pan for draft application:{}", openApplication.getId());
            try {
                String pancard = kycHandler.getPanNumber(openApplication.getMerchantId());
                if (pancard != null && experian != null && !experian.getPancardNumber().equalsIgnoreCase(pancard)) {
                    log.info("pancard mismatch for merchant:{}, kyc pancard:{}, experian pancard:{}, rejecting application", experian.getMerchantId(), pancard, experian.getPancardNumber());
                    openApplication.setCkycStatus(KycStatus.REJECTED.name());
                    openApplication.setCkycRejectionReason("PANCARD MISMATCH");
                    openApplication.setCkycDate(new Date());
                    openApplication.setStatus(KycStatus.REJECTED.name().toLowerCase());
                    lendingApplicationDao.save(openApplication);
                    executorService.execute(() -> apiGatewayService.globalLimitTxn(openApplication.getMerchantId(), "CREDIT", openApplication.getLoanAmount()));
                }
            } catch (Exception e) {
                log.error("Exception in updateCkycStatus for application:{}", openApplication.getId());
            }
        }
    }

    private void checkEligibility(LoanDetailsResponse loanDetailsResponse, LoanDetailsRequest request,
                                  Experian experian, BasicDetailsDto merchant) throws Exception {
        String kycPancard = kycHandler.getPanNumber(merchant.getId());
        if (experian == null && (request == null || request.getPancard() == null || request.getPincode() == null)) {
            log.info("Invalid request to eligibility for merchant:{}", merchant.getId());
            loanDetailsResponse.setPancard(kycPancard);
            return;
        }
        MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchant.getId());
        if (ObjectUtils.isEmpty(merchantResponseDTO)) {
            throw new MerchantSummaryExceptionHandler(merchant.getId().toString());
        }
//        MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
        if (experian == null) {
            if (Objects.nonNull(merchant.getId())) {
                String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
                log.info("deleting cached key of loan details in check eligibility flow for merchant: {}", merchant.getId());
                lendingCache.delete(loanDetailsCacheKey);
            } else {
                log.info("merchant id not found in verifyOtp flow");
            }
            experian = experianDao.save(new Experian(merchant.getId(), null, merchant.getLatitude() != null && merchant.getLatitude() <= 90 ? merchant.getLatitude() : null, merchant.getLongitude() != null && merchant.getLongitude() <= 90 ? merchant.getLongitude() : null, 0, request.getPancard(), (merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null) ? merchantResponseDTO.getBpScore() : 0D, 0, Integer.valueOf(request.getPincode())));
        } else if (request != null && request.getPancard() != null && request.getPincode() != null && !experian.getPancardNumber().equalsIgnoreCase(request.getPancard())) {
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
        } else if (request != null && request.getPincode() != null) {
            log.info("updating experian pincode:{} for merchant:{}", request.getPincode(), merchant.getId());
            experian.setPincode(Integer.valueOf(request.getPincode()));
            experianDao.save(experian);
        }
        if (!easyLoanUtil.isDummyMerchant(merchant.getId())) {
            if (!StringUtils.isEmpty(kycPancard) && !kycPancard.equalsIgnoreCase(experian.getPancardNumber())) {
                log.info("Pancard mismatch for merchant:{}, kyc:{}, experian:{}", merchant.getId(), kycPancard, experian.getPancardNumber());
                experian.setPancardNumber(kycPancard);
                experian.setResponse(null);
                experian.setBureau(null);
                experian.setHitId(null);
                experian.setReportDate(null);
                experian.setExperianScore(null);
                experianDao.save(experian);
            }
        }
//        Boolean eligibleToApplyAgain = easyLoanUtil.isEligibleToApplyAgain(experian.getReason());
//        if(!eligibleToApplyAgain) {
//            return;
//        }
//        Integer experianReapplyTimeline = easyLoanUtil.getExperianReapplyLine(experian.getReason());
//        if (experian.getRejected() && experian.getRejectedDate() != null && dateTimeUtil.getDateDiffInDays(experian.getRejectedDate(), new Date()) < experianReapplyTimeline) {
//            log.info("Derog within {} days, rejecting merchant:{}", experianReapplyTimeline, merchant.getId());
//            return ;
//        }
        loanDetailsResponse.setPancard(experian.getPancardNumber());
        loanDetailsResponse.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
        loanDetailsResponse.setHasExperian(true);
        // TODO: 10/11/22 todo final hardcoded this bit
//        loanDetailsResponse.setEdiDaysModel(EdiModel.assignEdiModel().getNoOfEdiDaysInAWeek());
        loanDetailsResponse.setEdiDaysModel(6);
        if (loanUtil.isInternalMerchant(merchant.getId()) || easyLoanUtil.percentScaleUp(merchant.getId(), rolloutAbflPercent)) {
            loanDetailsResponse.setEdiDaysModel(7);
        }
        EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdAndLoanTypeNotTopup(merchant.getId());
        Date dateWindow = dateTimeUtil.getDatePlusDays(dateTimeUtil.getCurrentDate(), -24 * eligibilityRefreshWindow);
        Boolean isClubV2 = apiGatewayService.checkClubV2(merchant.getId());
        log.info("merchant is: {} clubV2 member: {}",merchant.getId(), isClubV2);
        loanDetailsResponse.setClubV2Member(isClubV2);
        Eligibility eligibility = null;
//        log.info("date window: {}, getCreatedAt after date Window: {} for merchant: {}", dateWindow, eligibleLoan.getCreatedAt().after(dateWindow), merchant.getId());
//        log.info("check object eligible loan: {} for merchant: {}", !ObjectUtils.isEmpty(eligibleLoan), merchant.getId());
        log.info("eligibility check begins !!! {}", merchant.getId());
        if (!ObjectUtils.isEmpty(eligibleLoan) && eligibleLoan.getCreatedAt().after(dateWindow) && !(isClubV2 && clubEligibleLoanCache)) {
            log.info("Eligible offers exist for merchant:{}", merchant.getId());
            eligibility = createEligibility(merchant.getId());
            if (eligibility != null) {
                log.info("eligibility is not null for merchant: {}", merchant.getId());
                loanDetailsResponse.setEligibility(eligibility);
                return;
            } else {
                log.info("eligibility is null for merchant: {}", merchant.getId());
            }
        } else {
            log.info("after the date window for merchant: {}", merchant.getId());
        }
        MutableBoolean isDerog = new MutableBoolean(false);
        GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchant.getId(), null, request.getAppVersion(), isClubV2);
        Double eligibleAmount = 0D;
        if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
            log.info("Global limit for merchant:{} is {}", merchant.getId(), globalLimitResponse.getData().getGlobalLimit());
            eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
            isDerog.setValue(globalLimitResponse.getData().isDerog());
        }
        if (eligibleAmount > 0D) {
            log.info("Eligibility found for merchant:{}", merchant.getId());
            recomputeEligibleLoan(globalLimitResponse, null, merchant.getId());
            eligibility = createEligibility(merchant.getId());
        }
        if (eligibility != null) {
            loanDetailsResponse.setEligibility(eligibility);
            return;
        }
        log.info("Eligibility not found for merchant:{}", merchant.getId());
        loanDetailsResponse.setIneligible(getIneligibleReason(merchant.getId(), isDerog, experian.getPincode(), globalLimitResponse));
        loanDetailsResponse.setChangeBankAccount(!loanUtil.isEnachBank(merchant.getId()));
    }


    public void recomputeEligibleLoan(GlobalLimitResponse globalLimitResponse, Double customAmount, Long merchantId) {
        if (Objects.isNull(globalLimitResponse) || Objects.isNull(globalLimitResponse.getData())) {
            log.info("Global Limit not found");
            return;
        }
        Double finalLimit = globalLimitResponse.getData().getGlobalLimit();
        String loanType = globalLimitResponse.getData().getLoanType();
        Double version = globalLimitResponse.getData().getVersion();
        try {
//            eligibleLoanDao.deleteByMerchantId(merchantId);
            List<GlobalLimitResponse.OfferDetail> offerDetails = globalLimitResponse.getData().getOfferDetails();
            offerDetails.sort(Comparator.comparingInt(GlobalLimitResponse.OfferDetail::getTenure));
            for (GlobalLimitResponse.OfferDetail offerDetail : offerDetails) {
                log.info("Tenure: {}, finalLimit: {}, loanAmount: {}, customAmount: {}", offerDetail.getTenure(), finalLimit, offerDetail.getLoanAmount(), customAmount);
                if (Objects.nonNull(customAmount) && customAmount < finalLimit && customAmount <= offerDetail.getLoanAmount()) {
                    loanUtil.calculateLoanBreakup(offerDetail, merchantId, loanType, customAmount, null, version);
                }
                if (finalLimit <= offerDetail.getMaxLoanAmount() && finalLimit <= (offerDetail.getLoanAmount())) {
                    loanUtil.calculateLoanBreakup(offerDetail, merchantId, loanType, finalLimit, null, version);
                }
            }
//            eligibleLoanDao.deleteGreaterOffersByMerchantId(merchantId, finalLimit);
        } catch (Exception e) {
            log.error("Exception while recomputing eligible loan for merchant:{}", merchantId, e);
        }
    }

    private Integer fetchPincode(Long merchantId) {
        log.info("Fetching pincode for merchant:{}", merchantId);
        try {
            MerchantInfoDTO merchantInfoDTO = apiGatewayService.getMerchantAddress(merchantId);
            if (merchantInfoDTO != null && merchantInfoDTO.getData() != null && merchantInfoDTO.getData().get(0).getAddressDetail() != null) {
                for (MerchantInfoDTO.AddressDetail addressDetail : merchantInfoDTO.getData().get(0).getAddressDetail()) {
                    if (!StringUtils.isEmpty(addressDetail.getPinCode()) && !StringUtils.isEmpty(addressDetail.getAddressType()) && addressDetail.getAddressType().equalsIgnoreCase("Shop/Office")) {
                        return Integer.parseInt(addressDetail.getPinCode());
                    }
                }
            }
        } catch (Exception e) {
            log.info("Exception while fetching pincode for merchant:{}", merchantId, e);
        }
        return null;
    }

    private String getIneligibleReason(Long merchantId, MutableBoolean isDerog, Integer pincode, GlobalLimitResponse globalLimitResponse) {
        log.info("Checking ineligible reason for merchant:{}", merchantId);
        try {
            if (Objects.isNull(globalLimitResponse) || Objects.isNull(globalLimitResponse.getData())) {
                log.info("Global limit response is null for merchantId: {} , {}", merchantId, globalLimitResponse);
            }
            if (Objects.nonNull(globalLimitResponse) && Objects.nonNull(globalLimitResponse.getData()) && Objects.nonNull(globalLimitResponse.getData().getRejectionType())) {
                return globalLimitResponse.getData().getRejectionType();
            }
            if (loanUtil.isOGL(pincode)) {
                log.info("OGL merchant:{}", merchantId);
                return IneligibleType.OGL.name();
            }
            if (isDerog.booleanValue()) {
                log.info("Derog merchant:{}", merchantId);
                return IneligibleType.DEROG.name();
            }
        } catch (Exception e) {
            log.error("Exception in getIneligibleReason for merchant:{}", merchantId, e);
        }
        log.info("Ineligible merchant:{}", merchantId);
        return RejectionReason.LOW_TRANSACTION.getReason();
    }


//    private GlobalLimitResponse getEligibility(Merchant merchant, Integer appVersion) {
//        log.info("Checking eligibility for merchant:{}", merchant.getId());
//        try {
//            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchant.getId(), null, appVersion);
//            return globalLimitResponse;
//        } catch (Exception e) {
//            log.error("Exception in getEligibility for merchant:{}", merchant.getId(), e);
//        }
//        return null;
//    }

    private Eligibility createEligibility(Long merchantId) {
        try {
            EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdAndLoanTypeNotTopup(merchantId);
            if (ObjectUtils.isEmpty(eligibleLoan)) {
                return null;
            }
            log.info("Creating eligibility for merchant:{}", merchantId);
            return Eligibility.builder()
              .loanAmount(eligibleLoan.getAmount())
              .arrangerFee(eligibleLoan.getProcessingFee())
              .interestRate(eligibleLoan.getRateOfInterest())
              .repaymentAmount(eligibleLoan.getRepayment())
              .ediCount(eligibleLoan.getEdiCount())
              .ediAmount(eligibleLoan.getEdi())
              .tenure(eligibleLoan.getTenure())
              .category(eligibleLoan.getCategory())
              .loanType(eligibleLoan.getLoanType())
              .clubV2Amount(eligibleLoan.getClubV2Amount())
                    .uniqueKey(eligibleLoan.getId())
              .build();
        } catch (Exception e) {
            log.error("Exception in createEligibility for merchant:{}", merchantId, e);
        }
        return null;
    }

    private LoanApplicationDetails setApplicationDetails
            (LoanDetailsResponse loanDetailsResponse, LendingApplication openApplication,
             String token, boolean isIOS, Experian experian, BasicDetailsDto merchant) {
        try {
            LoanApplicationDetails applicationDetails = new LoanApplicationDetails();
            applicationDetails.setApplicationId(openApplication.getId());
            applicationDetails.setExternalLoanId(openApplication.getExternalLoanId());
            applicationDetails.setLoanAmount(openApplication.getLoanAmount());
            applicationDetails.setApplicationStatus(openApplication.getStatus().toLowerCase());
            LendingApplicationDetails lendingApplicationDetails =
                    lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(openApplication.getId());
            if (!ObjectUtils.isEmpty(lendingApplicationDetails)) {
                log.info("lender assc for {} {}", lendingApplicationDetails.getLenderAssc(), lendingApplicationDetails.getApplicationId());
                applicationDetails.setLenderAssc(Optional.ofNullable(lendingApplicationDetails.getLenderAssc()).orElse(false));
            }
            if ("approved".equalsIgnoreCase(openApplication.getStatus()) || "pending_verification".equalsIgnoreCase(openApplication.getStatus())) {
                LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationIdAndMerchantId(openApplication.getId(), openApplication.getMerchantId());
                if (Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getResubmit() != null && lendingResubmitTask.getResubmit() && (lendingResubmitTask.getResubmitDone() == null || !lendingResubmitTask.getResubmitDone())) {
                    Date currentRequestTimestamp = dateTimeUtil.getCurrentDate();
                    Date resubmitCreatedAt = lendingResubmitTask.getCreatedAt();
                    Date opsStartTimestamp = dateTimeUtil.getDateAtTime(resubmitCreatedAt, 9, 0, 0, 0);
                    Date opsSameDayProcessTimestamp = dateTimeUtil.getDateAtTime(resubmitCreatedAt, 18, 0, 0, 0);
                    Date opsNextDayProcessTimestamp = dateTimeUtil.getDateAtTime(dateTimeUtil.getDatePlusDays(resubmitCreatedAt, 24), 18, 0, 0, 0);
                    if ((resubmitCreatedAt.before(opsStartTimestamp) && currentRequestTimestamp.after(opsSameDayProcessTimestamp)) || ((resubmitCreatedAt.after(opsStartTimestamp)) && (currentRequestTimestamp.after(opsNextDayProcessTimestamp)))) {
                        applicationDetails.setApplicationStatus("RESUBMIT");
                        applicationDetails.setResubmitReason(lendingResubmitTask.getResubmitReason());
                    }
                }
                if (Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getDowngrade() != null && lendingResubmitTask.getDowngrade() && (lendingResubmitTask.getDowngradeDone() == null || !lendingResubmitTask.getDowngradeDone())) {
                    applicationDetails.setApplicationStatus("DOWNGRADE");
                }
                if (Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getResign() != null && lendingResubmitTask.getResign() && (lendingResubmitTask.getResignDone() == null || !lendingResubmitTask.getResignDone())) {
                    applicationDetails.setApplicationStatus("RESIGN");
                }
            }
            applicationDetails.setRejectReason(getRejectionReason(openApplication,merchant));
            applicationDetails.setEnachBank(loanUtil.isEnachBank(openApplication.getMerchantId()));
            
            if (applicationDetails.getEnachBank()) {
                applicationDetails.setEnachDeeplink(getEnachDeeplink(openApplication, token, isIOS));
            }

            if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus())) {
                applicationDetails.setEnachDone("APPROVED".equalsIgnoreCase(openApplication.getNachStatus()));
            }

            if("TOPUP".equalsIgnoreCase(openApplication.getLoanType()) && ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())){
                applicationDetails.setEnachDone("APPROVED".equalsIgnoreCase(openApplication.getNachStatus()));
            }

            
//            if (LoanType.SMALL_TICKET.name().equalsIgnoreCase(openApplication.getLoanType())) {
//                applicationDetails.setSkipEnach(Boolean.TRUE);
//            }
            applicationDetails.setAddressDetails(getShopAddress(openApplication));
            applicationDetails.setProfessionalDetails(getProfessionalDetails(openApplication));
            applicationDetails.setAdditionalDetails(new AdditionalDetails(openApplication.getEmail(), openApplication.getAlternateMobile()));
            applicationDetails.setCurrentAddress(getCurrentAddress(openApplication));
            applicationDetails.setShopPhotoRequired(isShopPhotoRequired(openApplication));
            if (applicationDetails.getEnachDeeplink() == null &&
                    (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase
                            (openApplication.getStatus()) ||
                            ApplicationStatus.APPROVED.name().equalsIgnoreCase(openApplication.getStatus()))) {
                int tat = loanUtil.getApplicationTAT(openApplication.getId());
                applicationDetails.setTransferDays(tat < 1 ? "Soon" : tat + "-" + (tat + 2) + " Days");
            }
            Long reapplyTime = getReapplyTime(openApplication);
            if (Objects.nonNull(reapplyTime)) {
                reapplyTime = reapplyTime > 0 ? reapplyTime : 0;
                applicationDetails.setReapplyTime(reapplyTime);
                applicationDetails.setReapplyTimeEpoch(LoanUtil.addDays(new Date(), reapplyTime).getTime());
            }
            applicationDetails.setReapply(shouldReapply(openApplication, reapplyTime));
            if (!StringUtils.isEmpty(applicationDetails.getEnachDeeplink())) {
                applicationDetails.setEnachErrorResponse(getEnachError(openApplication, experian));
            }
            return applicationDetails;
        } catch (Exception e) {
            log.error("Exception in setApplicationDetails for merchant:{}", openApplication.getMerchantId(), e);
        }
        return null;
    }

    public Long getReapplyTime(LendingApplication lendingApplication) {
        Long reapplyTime = null;
        if ("rejected".equalsIgnoreCase(lendingApplication.getStatus())) {
            Integer reapplyDayDiff = null;
            if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getManualCibilReason(), RejectionStage.CIBIL, lendingApplication.getMerchantId());
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualKyc())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getManualKycReason(), RejectionStage.KYC, lendingApplication.getMerchantId());
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getPhysicalReason(), RejectionStage.QC, lendingApplication.getMerchantId());
            } else {
                reapplyDayDiff = 0;
            }
            if (Objects.nonNull(reapplyDayDiff)) {
                reapplyTime = reapplyDayDiff - LoanUtil.getDateDiffInDays(lendingApplication.getUpdatedAt(), new Date());
                reapplyTime = reapplyTime > 0 ? reapplyTime : 0;
            }
        }
        return reapplyTime;
    }

    public Long getReapplyTime(LendingApplicationSlave lendingApplication) {
        Long reapplyTime = null;
        if ("rejected".equalsIgnoreCase(lendingApplication.getStatus())) {
            Integer reapplyDayDiff = null;
            if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getManualCibilReason(), RejectionStage.CIBIL, lendingApplication.getMerchantId());
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualKyc())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getManualKycReason(), RejectionStage.KYC, lendingApplication.getMerchantId());
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getPhysicalReason(), RejectionStage.QC, lendingApplication.getMerchantId());
            } else {
                reapplyDayDiff = 0;
            }
            if (Objects.nonNull(reapplyDayDiff)) {
                reapplyTime = reapplyDayDiff - LoanUtil.getDateDiffInDays(lendingApplication.getUpdatedAt(), new Date());
                reapplyTime = reapplyTime > 0 ? reapplyTime : 0;
            }
        }
        return reapplyTime;
    }

    private String getRejectionReason(LendingApplication openApplication, BasicDetailsDto merchant) {
        if (!ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getStatus()))
            return null;
        if (!StringUtils.isEmpty(openApplication.getCkycRejectionReason())) {
            return openApplication.getCkycRejectionReason();
        }
        if (!StringUtils.isEmpty(openApplication.getManualKycReason())) {
            return "Please re-apply with correct shop details";
        }
        if (!StringUtils.isEmpty(openApplication.getManualCibilReason())) {
            return "Credit Evaluation Failed";
        }
        if (!StringUtils.isEmpty(openApplication.getPhysicalReason())) {
            return "Incomplete documents submitted during physical visit";
        }
        LendingDisbursalStage lendingDisbursalStage = lendingDisbursalStageDao.findByApplicationId(openApplication.getId());
        boolean disbursalCallingRejected = lendingDisbursalStage != null && ("NO".equalsIgnoreCase(lendingDisbursalStage.getReadyStage()) || "NO".equalsIgnoreCase(lendingDisbursalStage.getCallStage()));
        if (disbursalCallingRejected) {
            return "You were unreachable on " + merchant.getMobile();
        }
        return null;
    }

    private EnachErrorMessageDTO getEnachError(LendingApplication openApplication, Experian experian) {
        try {
            BharatPeEnachResponseDTO bharatPeEnach = enachHandler.findByMerchantIdAndApplicationId(openApplication.getMerchantId(), openApplication.getId());
            if (bharatPeEnach != null) {
                return enachErrorHandingService.enachErrorResponse(bharatPeEnach, openApplication.getMerchantId(),
                        openApplication, experian);
            }
        } catch (Exception e) {
            log.error("Exception in getEnachError for merchant:{}", openApplication.getMerchantId());
        }
        return null;
    }

    private String shouldReapply(LendingApplication openApplication, Long reapplyTime) {

        if (ObjectUtils.isEmpty(reapplyTime)) {
            return null;
        }

        if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getStatus())) {
            if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualCibil())) {
                return Reapply.OFFER.name();
            } else if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualKyc())) {
                return Reapply.OFFER.name();
            } else if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getCkycStatus())) {
                KycStatusDTO kycStatusDTO = kycHandler.getKycStatus(openApplication.getMerchantId());
                if (KycStatus.REJECTED.equals(kycStatusDTO.getKycStatus()) && KycDocType.PAN_NO.equals(kycStatusDTO.getKycDocType())) {
                    return Reapply.PAN.name();
                } else if ("PANCARD MISMATCH".equalsIgnoreCase(openApplication.getCkycRejectionReason())) {
                    return Reapply.PAN.name();
                } else {
                    return Reapply.OFFER.name();
                }
            } else {
                return Reapply.OFFER.name();
            }
        }
        return null;
    }

    private boolean isShopPhotoRequired(LendingApplication openApplication) {
        if (ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())) {
            List<LendingShopDocuments> lendingShopDocumentsList = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(openApplication.getMerchantId(), openApplication.getId());
            return lendingShopDocumentsList.size() < 2;
        }
        return false;
    }

    private String getCurrentAddress(LendingApplication lendingApplication) {
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());
        return lendingGstDetail != null && !StringUtils.isEmpty(lendingGstDetail.getCurrentAddress()) && "Different".equalsIgnoreCase(lendingGstDetail.getAddressType()) ? lendingGstDetail.getCurrentAddress() : null;
    }

    private ProfessionalDetails getProfessionalDetails(LendingApplication openApplication) {
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(openApplication.getId());
        if (lendingGstDetail == null) return null;

        return ProfessionalDetails.builder()
                .profession(lendingGstDetail.getEntityType())
                .gstNumber(lendingGstDetail.getGstNumber())
                .experience(lendingGstDetail.getExperience())
                .salary(String.valueOf(lendingGstDetail.getSalary()))
                .companyName(lendingGstDetail.getCompanyName())
                .addressType(lendingGstDetail.getAddressType())
                .shopType(lendingGstDetail.getShopType())
                .build();
    }

    private AddressDetails getShopAddress(LendingApplication lendingApplication) {
        return AddressDetails.builder()
                .pincode(String.valueOf(lendingApplication.getPincode()))
                .city(lendingApplication.getCity())
                .state(lendingApplication.getState())
                .address1(lendingApplication.getShopNumber())
                .address2(lendingApplication.getStreetAddress())
                .landmark(lendingApplication.getLandmark()).build();
    }

    private String getEnachDeeplink(LendingApplication openApplication, String token, boolean isIOS) {
        if (!"TOPUP".equalsIgnoreCase(openApplication.getLoanType()) && !ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus())) {
            return null;
        }
        if (easyLoanUtil.isDummyMerchant(openApplication.getMerchantId()) || loanUtil.isEnachDone(openApplication.getMerchantId(), openApplication.getId()) ||
                loanUtil.isEligibleForNachSkip(openApplication, openApplication.getLender())) {
            openApplication.setNachStatus("APPROVED");
            openApplication.setNachType("ENACH");
            openApplication.setNachLender(loanUtil.enachServiceLenderMapper(openApplication.getLender()));
            lendingApplicationDao.save(openApplication);
            return null;
        }
//        BharatPeEnach bharatPeEnach = bharatPeEnachDao.findByMerchantIdAndApplicationId(openApplication.getMerchantId(), openApplication.getId());
//        if (bharatPeEnach != null && BooleanUtils.isTrue(bharatPeEnach.getSkip())) {
//            return null;
//        }
        if (isIOS) return Deeplink.TECHPROCESS;
        return apiGatewayService.getEnachProvider(token, openApplication.getLender(),openApplication.getMerchantId());
    }


//    private boolean isCreditLineMerchant(BasicDetailsDto merchant) {
//        CreditLineMerchant creditLineMerchant = creditLineMerchantDao.findByMerchantId(merchant.getId());
//        return creditLineMerchant != null;
//    }

//    private boolean isOrganizedMerchant(Long merchantId) {
//        List<MerchantStore> stores = merchantStoreDao.findByMerchantId(merchantId);
//        return !stores.isEmpty();
//    }

    public ApiResponse<?> getEnachBanks() {
        List<NachableBanksDTO> enachBanks = loanUtil.getEnachBanks();
        if (enachBanks.isEmpty()) {
            return new ApiResponse<>(false, "No Bank Found");
        }
        List<BankAccountDetails> accountDetails = enachBanks.parallelStream().map(b -> BankAccountDetails.builder().bankName(b.getDisplayName()).bankLogo(b.getImageUrl()).build()).collect(Collectors.toList());
        accountDetails.sort(Comparator.comparing(BankAccountDetails::getBankName));
        return new ApiResponse<>(accountDetails);
    }

    public boolean checkEligibilityForCallback(Long merchantId) {
        try {
            ResponseDTO responseDTO = fosService.checkMerchantEligibilty(merchantId, Boolean.FALSE);
            if (responseDTO.isSuccess() && responseDTO.getData() != null) {
                FosMerchantEligibilityDto fosMerchantEligibilityDto = (FosMerchantEligibilityDto) responseDTO.getData();
                if (!"ineligible".equalsIgnoreCase(fosMerchantEligibilityDto.getEligibility())) {
                    log.info("merchant ineligible for callback");
                    return Boolean.TRUE;
                }
            }
        } catch (Exception e) {
            log.error("error occurred while fetching eligibility: {}", e);
        }
        return Boolean.FALSE;
    }

    public ApiResponse<?> getBusinessCategorySubCategory(Long merchantId) {
        try {
            LendingMerchantDetails merchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            BusinessDetailsDTO businessDetailsDTO = new BusinessDetailsDTO();
            if (Objects.nonNull(merchantDetails) && !exceptedMerchantList.contains(merchantId)) {
                businessDetailsDTO.setIsEdit(true);
            }
            if (Objects.nonNull(merchantDetails)) {
                businessDetailsDTO.setBusinessCategory(merchantDetails.getBusinessCategory());
                businessDetailsDTO.setBusinessName(merchantDetails.getBusinessName());
                businessDetailsDTO.setBusinessSubCategory(merchantDetails.getBusinessSubCategory());
                businessDetailsDTO.setMerchantId(merchantDetails.getMerchantId());
            }
            return new ApiResponse<>(businessDetailsDTO);
        } catch (Exception ex) {
            log.error("Exception Occured while fetching business details for merchantId: {} {}", merchantId, ex.getMessage());
        }
        return new ApiResponse<>(false, "Something Went Wrong.");
    }

    public ApiResponse<LatestLoanDetailResponse> getLatestLoanDetails(Long merchantId) {
        try {
//            Optional<Merchant> merchantDetails = merchantDao.findById(merchantId);
            Optional<BasicDetailsDto> merchantDetails = merchantService.fetchMerchantBasicDetails(merchantId);
            if (!merchantDetails.isPresent()) {
                return new ApiResponse<>(false, "Merchant not found");
            }

            LendingApplication loanDisbursedApplication =
                    lendingApplicationDao.getLastDisbursedLoan(merchantId);
            LendingApplication loanApprovedApplication =
                    lendingApplicationDao.findOpenApplication(merchantId);
            LendingApplication loanRejectedApplication =
                    lendingApplicationDao.findByMerchantIdAndStatus(merchantId, "rejected");


            LatestLoanDetailResponse latestLoanDetailResponse = new LatestLoanDetailResponse();
            generateLatestLoanResponse(loanDisbursedApplication, latestLoanDetailResponse, "disbursed");
            generateLatestLoanResponse(loanApprovedApplication, latestLoanDetailResponse, "approved");
            generateLatestLoanResponse(loanRejectedApplication, latestLoanDetailResponse, "rejected");

            return new ApiResponse<>(latestLoanDetailResponse);
        } catch (Exception ex) {
            log.error("Exception Occured while fetching business details for merchantId: {} {}", merchantId, ex.getMessage());
        }
        return new ApiResponse<>(false, "Something Went Wrong.");
    }

    public void generateLatestLoanResponse(
            LendingApplication lendingApplication,
            LatestLoanDetailResponse loanDetailResponse,
            String loanStatus
    ) {
        if (ObjectUtils.isEmpty(lendingApplication))
            return;

        LatestLoanDetailResponse.LoanDetail innerObject =
                LatestLoanDetailResponse.LoanDetail
                        .builder()
                        .loanAmount(lendingApplication.getLoanAmount())
                        .status(lendingApplication.getStatus())
                        .updatedAt(lendingApplication.getUpdatedAt().getTime())
                        .build();

        switch (loanStatus) {
            case "approved":
                loanDetailResponse.setApproved(innerObject);
                break;
            case "disbursed":
                loanDetailResponse.setDisbursed(innerObject);
                break;
            case "rejected":
                loanDetailResponse.setRejected(innerObject);
        }
    }

    public CreditScoreReportDetailDTO.AverageCreditScore getAverageCreditScore(Integer pin_code, BureauResponseDTO bureau) {
        CreditScoreReportDetailDTO creditScoreReportDetailDTO = new CreditScoreReportDetailDTO();
        CreditScoreReportDetailDTO.AverageCreditScore averageCreditScore = new CreditScoreReportDetailDTO.AverageCreditScore();
        try {
            PincodeCityStateMappingDTO pincodeCityState = merchantService.findByPincode(pin_code);
            log.info("pincodecitystate:{}", pincodeCityState);
            if (Objects.nonNull(pincodeCityState) && Objects.nonNull(bureau.getVariables().getBureauScore())) {

                Double averageCountryScore = lendingCityCreditScoreDao.getAverageCreditScoreForCountry();
                Double averageStateScore = lendingCityCreditScoreDao.getAverageCreditScoreForState(pincodeCityState.getState());
                Integer totalMerchantInState = lendingCityCreditScoreDao.getTotalMerchantInStateByPercentile(pincodeCityState.getState());

                if (Objects.isNull(averageStateScore) || totalMerchantInState < 30) {
                    averageStateScore = averageCountryScore == 0 ? averageCountryScore : averageCountryScore - 1;
                }
                Double averageCityScore = lendingCityCreditScoreDao.getAverageCreditScoreForCity(pincodeCityState.getCity());
                Integer totalMerchantInCity = lendingCityCreditScoreDao.getTotalMerchantInCityByPercentile(pincodeCityState.getCity());

                if (Objects.isNull(averageCityScore) || totalMerchantInCity < 30) {
                    averageCityScore = averageStateScore == 0 ? averageStateScore : averageStateScore - 1;
                }

                Double countryPercentileScore = lendingCityCreditScoreDao.getCreditScorePercentileByCountry(bureau.getVariables().getBureauScore());
                Double statePercentileScore = lendingCityCreditScoreDao.getCreditScorePercentileByState(pincodeCityState.getState(), bureau.getVariables().getBureauScore());

                if (Objects.isNull(statePercentileScore) || totalMerchantInState < 30) {
                    statePercentileScore = countryPercentileScore == 0 ? countryPercentileScore : countryPercentileScore - 1;
                }
                Double cityPercentileScore = lendingCityCreditScoreDao.getCreditScorePercentileByCity(pincodeCityState.getCity(), bureau.getVariables().getBureauScore());
                if (Objects.isNull(cityPercentileScore) || totalMerchantInCity < 30) {
                    cityPercentileScore = statePercentileScore == 0 ? statePercentileScore : statePercentileScore - 1;
                }

                log.info("got averageCountryScore: {} averageStateScore: {} averageCityScore: {}", averageCountryScore, averageStateScore, averageCityScore);
                averageCreditScore.setCity(pincodeCityState.getCity());
                averageCreditScore.setState(pincodeCityState.getState());
                averageCreditScore.setCountry("India");
                averageCreditScore.setCityAverageScore(averageCityScore);
                averageCreditScore.setStateAverageScore(averageStateScore);
                averageCreditScore.setCountryAverageScore(averageCountryScore);
                averageCreditScore.setCityPercentile(cityPercentileScore);
                averageCreditScore.setStatePercentile(statePercentileScore);
                averageCreditScore.setCountryPercentile(countryPercentileScore);
                log.info("set averageCountryScore: {} averageStateScore: {} averageCityScore: {}", averageCountryScore, averageStateScore, averageCityScore);
                return averageCreditScore;
            }
        } catch (Exception ex) {
            log.error("Error occured while fetching Average and percentile", ex);
        }
        return null;
    }

    public ApiResponse<?> getCreditScoreReportDetail(BasicDetailsDto merchant, CommonAPIRequest commonAPIRequest) {
        log.info("getCreditScoreReportDetail");
        Integer pin_code = commonAPIRequest.getPayload().get("pin_code") != null ? Integer.parseInt(commonAPIRequest.getPayload().get("pin_code").toString()) : null;
        String pan_card = commonAPIRequest.getPayload().get("pan_card") != null ? commonAPIRequest.getPayload().get("pan_card").toString() : null;
        String mobile = merchant.getMobile().substring(2);
        Long merchantId = merchant.getId();
        log.info("calling bureau handler");
        BureauResponseDTO bureauResponseDTO = bureauHandler.getBureauData(pan_card, merchantId, mobile);
        CreditScoreReportDetailDTO creditScoreDetails;
        if (ObjectUtils.isEmpty(bureauResponseDTO) || Objects.isNull(bureauResponseDTO.getVariables()) || Objects.isNull(bureauResponseDTO.getVariables().getCreditScoreReportDetailDTO()))
            return new ApiResponse<>(false, "Bureau Data not found");
        else {
            creditScoreDetails = bureauResponseDTO.getVariables().getCreditScoreReportDetailDTO();
            creditScoreDetails.setAverageCreditScore(getAverageCreditScore(pin_code, bureauResponseDTO));
        }
        log.info("BureauDetails fetched successfully");
        return new ApiResponse<>(creditScoreDetails);
    }

    public ApiResponse<?> getLoanAndCreditCardDetail(BasicDetailsDto merchant, CommonAPIRequest commonAPIRequest) {
        String pan_card = commonAPIRequest.getPayload().get("pan_card") != null ? commonAPIRequest.getPayload().get("pan_card").toString() : null;
        String mobile = merchant.getMobile().substring(2);
        Long merchantId = merchant.getId();
        BureauResponseDTO bureauResponseDTO = bureauHandler.getBureauData(pan_card, merchantId, mobile);

        LoanAndCreditCardDetailDTO loanAndCreditCardDetailDTO;
        if (ObjectUtils.isEmpty(bureauResponseDTO) || Objects.isNull(bureauResponseDTO.getVariables()) || Objects.isNull(bureauResponseDTO.getVariables().getLoanAndCreditCardDetailDTO()))
            return new ApiResponse<>(false, "Bureau Data not found");

        else {
            loanAndCreditCardDetailDTO = bureauResponseDTO.getVariables().getLoanAndCreditCardDetailDTO();
            if (!ObjectUtils.isEmpty(loanAndCreditCardDetailDTO.getLoanDetail())) {
                for (LoanAndCreditCardDetailDTO.LoanDetail loanDetail : loanAndCreditCardDetailDTO.getLoanDetail()) {
                    int size = loanDetail.getAccountNumber().length();
                    if (size > 4) {
                        char[] arr = new char[size - 4];
                        Arrays.fill(arr, 'x');
                        String masked = String.valueOf(arr);
                        String accountNumber = loanDetail.getAccountNumber().substring(size - 4);
                        loanDetail.setAccountNumber(masked + accountNumber);
                    }
                }
            }

            if (!ObjectUtils.isEmpty(loanAndCreditCardDetailDTO.getCreditCardDetail())) {
                for (LoanAndCreditCardDetailDTO.CreditCardDetail creditCardDetail : loanAndCreditCardDetailDTO.getCreditCardDetail()) {
                    int size = creditCardDetail.getCreditCardNumber().length();
                    if (size > 4) {
                        char[] arr = new char[size - 4];
                        Arrays.fill(arr, 'x');
                        String masked = String.valueOf(arr);
                        String accountNumber = creditCardDetail.getCreditCardNumber().substring(size - 4);
                        creditCardDetail.setCreditCardNumber(masked + accountNumber);
                    }
                }
            }
        }

        return new ApiResponse<>(loanAndCreditCardDetailDTO);
    }


    public ApiResponse<?> getMerchantReferences(BasicDetailsDto merchant) {
        try {
            Long merchantId = merchant.getId();
            LendingApplication lendingApplication = lendingApplicationDao.findBymerchantId(merchantId);

            if (Objects.isNull(lendingApplication) || Objects.isNull(lendingApplication.getId())) {
                log.info("No applicationId found of merchantId: {}", merchantId);
                return new ApiResponse<>(false, "No applicationId found for given merchantId");
            }
            log.info("applicationId: {} found of merchantId: {}", lendingApplication.getId(), merchantId);
            Long referencesLimit = getReferenceLimit(lendingApplication);
            Integer toBeShown = getToBeShownReferences(referencesLimit);
            MerchantReferencesResponseDto responseDto;
            DeGetReferencesResponse deResponse = dsHandler.getMerchantReferences(merchantId, minScore, toBeShown,lendingApplication.getId());
            if(Objects.isNull(deResponse)) {
                rejectingLoanDueToInsufficientReferences(lendingApplication,LendingConstants.REJECTION_REASON_2);
                log.info("Successfully rejected applicationId: {} because of no response from DE api of merchantId: {}", lendingApplication.getId(), merchantId);
                responseDto = MerchantReferencesResponseDto.builder().ineligible(true).build();
                return new ApiResponse<>(responseDto);
            }
            Integer totalContacts = deResponse.getTotalContacts();
            List<MerchantReference> deReferenceList = deResponse.getData().getOutput();

            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            log.info("lendingApplicationDetails of applicationId: {}, {}", lendingApplication.getId(), lendingApplicationDetails);
            if (Objects.isNull(lendingApplicationDetails)) {
                lendingApplicationDetails = new LendingApplicationDetails();
                lendingApplicationDetails.setApplicationId(lendingApplication.getId());
            }
            lendingApplicationDetails.setTotalReferences(totalContacts);
            if (Objects.nonNull(deReferenceList))
                lendingApplicationDetails.setReferencesFromDe(deReferenceList.size());

            lendingApplicationDetailsDao.save(lendingApplicationDetails);

            if (totalContacts < LendingConstants.MINIMUM_CONTACTS_NEEDED) {

                rejectingLoanDueToInsufficientReferences(lendingApplication,LendingConstants.REJECTION_REASON_2);
                log.info("Successfully rejected applicationId: {} because of insufficient references of merchantId: {}", lendingApplication.getId(), merchantId);
                responseDto = MerchantReferencesResponseDto.builder().references(deReferenceList).minScore(minScore).limit(referencesLimit).ineligible(true).build();

            } else {

                int scoreGreaterThan100 = 0, scoreGreaterThan80 = 0;

                if (referencesLimit == 10L) {
                    for (MerchantReference merchantReference : deReferenceList) {
                        if (merchantReference.getScore() >= 100) scoreGreaterThan100++;
                        if (merchantReference.getScore() >= 80) scoreGreaterThan80++;
                    }
                    if (scoreGreaterThan100 >= 2) {
                        deReferenceList.subList(Math.min(13,deReferenceList.size()), deReferenceList.size()).clear();
                    } else if (scoreGreaterThan80 < 4) {
                        deReferenceList.subList(Math.min(10,deReferenceList.size()), deReferenceList.size()).clear();
                    }
                }
                log.info("Successfully fetched references of merchantId: {}", merchantId);
                responseDto = MerchantReferencesResponseDto.builder().references(deReferenceList).minScore(minScore).limit(referencesLimit).ineligible(false).build();

            }
            return new ApiResponse<>(responseDto);
        } catch (Exception e) {
            log.error("Error occurred while fetching merchant references of merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong while getting merchant references!");
    }

    public ApiResponse<?> validateMerchantReferences(BasicDetailsDto merchant, List<ValidateMerchantReferencesRequestDto> referenceList) {
        try {
            Long merchantId = merchant.getId();
            if (Objects.isNull(referenceList) || referenceList.isEmpty()) {
                return new ApiResponse<>(false, "Requested Reference List for validation is empty!");
            }
            List<MerchantReference> validatedData = dsHandler.validateMerchantReferences(merchantId, referenceList);
            if (Objects.isNull(validatedData)) {
                return new ApiResponse<>(false, "Something Went Wrong from DS api while validating merchant references!");
            }
            MerchantReferencesResponseDto responseDto = MerchantReferencesResponseDto.builder().references(validatedData).minScore(minScore).build();

            return new ApiResponse<>(responseDto);
        } catch (Exception e) {
            log.error("Error occurred while validating merchant references of merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong while validating merchant references!");
    }

    public ApiResponse<?> updateMerchantReferences(BasicDetailsDto merchant, UpdateMerchantReferencesRequestDto requestDto) {
        try {
            Long merchantId = merchant.getId();
            LendingApplication lendingApplication = lendingApplicationDao.findBymerchantId(merchantId);
            if (Objects.isNull(lendingApplication) || Objects.isNull(lendingApplication.getId())) {
                log.info("No applicationId found of merchantId: {}", merchantId);
                return new ApiResponse<>(false, "No applicationId found for given merchantId");
            }
            Long applicationId = lendingApplication.getId();
            log.info("applicationId: {} found of merchantId: {}", applicationId, merchantId);
            Boolean isIneligible = requestDto.getIneligible();
            if (Objects.isNull(isIneligible)) {
                return new ApiResponse<>(false, "ineligible field can not be null!");
            }
            if (isIneligible) {
                rejectingLoanDueToInsufficientReferences(lendingApplication,LendingConstants.REJECTION_REASON_1);
                log.info("Successfully rejected applicationId: {} because of insufficient references of merchantId: {}", applicationId, merchantId);
                return new ApiResponse<>(true, "Successfully rejected applicationId because of insufficient references");
            } else {
                List<MerchantReference> requestedReferenceList = requestDto.getReferences();
                if (Objects.isNull(requestedReferenceList)) {
                    return new ApiResponse<>(false, "references field can not be empty!");
                }
                boolean toBeAdded;
                List<LendingMerchantReferences> savedMerchantReferencesList = lendingMerchantReferencesDao.findByMerchantIdAndApplicationId(merchantId, applicationId);
                for (MerchantReference requestedReferences : requestedReferenceList) {
                    toBeAdded = true;
                    for (LendingMerchantReferences savedReference : savedMerchantReferencesList) {
                        if (requestedReferences.getPhoneNumber().equals(savedReference.getReferenceNumber())) {
                            toBeAdded = false;
                            break;
                        }
                    }
                    if (toBeAdded) {
                        LendingMerchantReferences lendingMerchantReferences = new LendingMerchantReferences();
                        lendingMerchantReferences.setReferenceName(requestedReferences.getName());
                        lendingMerchantReferences.setReferenceNumber(requestedReferences.getPhoneNumber());
                        lendingMerchantReferences.setMerchantId(merchantId);
                        lendingMerchantReferences.setApplicationId(applicationId);
                        lendingMerchantReferences.setFraudFlag(requestedReferences.getFraudFlag());
                        lendingMerchantReferences.setInferredCompany(requestedReferences.getInferredCompany());
                        lendingMerchantReferences.setInferredName(requestedReferences.getInferredName());
                        lendingMerchantReferences.setInferredLocation(requestedReferences.getInferredLocation());
                        lendingMerchantReferences.setInferredNameConfidence(requestedReferences.getInferredNameConfidence());
                        lendingMerchantReferences.setInferredOccupation(requestedReferences.getInferredOccupation());
                        lendingMerchantReferences.setInferredRelation(requestedReferences.getInferredRelation());
                        lendingMerchantReferences.setNumHits(requestedReferences.getNumHits());
                        lendingMerchantReferences.setScore(requestedReferences.getScore());
                        lendingMerchantReferencesDao.save(lendingMerchantReferences);
                        log.info("Successfully saved merchant reference: {} of merchantId: {}", requestedReferences, merchantId);
                    }
                }

                LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
                log.info("lendingApplicationDetails of applicationId: {}, {}",applicationId,lendingApplicationDetails);
                if (Objects.nonNull(lendingApplicationDetails) && Objects.nonNull(lendingApplicationDetails.getReferencesFromDe())) {
                    lendingApplicationDetails.setSavedReferences(requestedReferenceList.size());
                    lendingApplicationDetails.setReferencesAddedByMerchant(Math.max(requestedReferenceList.size() - lendingApplicationDetails.getReferencesFromDe(), 0));
                    lendingApplicationDetailsDao.save(lendingApplicationDetails);
                }

                log.info("Successfully saved all references of merchantId: {}", merchantId);
                funnelService.submitEvent(merchant.getId(), null, applicationId,
                        FunnelEnums.StageId.REFERENCE_PAGE, FunnelEnums.StageEvent.SUBMITTED, LocalDateTime.now().toString());
                return new ApiResponse<>(true, "Successfully updated merchant References!");
            }

        } catch (Exception e) {
            log.error("Error occurred while updating merchant references of merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong while updating merchant references!");
    }

    private void rejectingLoanDueToInsufficientReferences(LendingApplication lendingApplication,String rejection_reason) {
        log.info("Started Rejecting application: {} due to insufficient references of merchantId: {}", lendingApplication.getId(), lendingApplication.getMerchantId());
        lendingApplication.setStatus("rejected");
        lendingApplication.setManualCibil("REJECTED");
        lendingApplication.setManualCibilReason(rejection_reason);
        lendingApplication.setCibilApprovedDate(new Date());
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        if(Objects.nonNull(lendingRiskVariablesSnapshot)) {
            lendingRiskVariablesSnapshot.setExperianRejection(rejection_reason);
            lendingRiskVariablesSnapshotDao.save(lendingRiskVariablesSnapshot);
        }
        lendingApplicationDao.save(lendingApplication);
    }

    public Long getReferenceLimit(LendingApplication lendingApplication) {
        Long referencesLimit = null;
        try {
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if (Objects.isNull(lendingRiskVariablesSnapshot)) {
                log.info("lendingRiskVariableSnapshot not found of applicationId: {}", lendingApplication.getId());
            } else {
                referencesLimit = lendingRiskVariablesSnapshot.getReferenceCount();
            }
            if (Objects.isNull(referencesLimit)) {
                log.info("Assigning default value 10 to referencesLimit of applicationId: {}", lendingApplication.getId());
                referencesLimit = LendingConstants.DEFAULT_REFERENCE_LIMIT;
            }
            return referencesLimit;
        } catch (Exception e) {
            log.error("Exception occurred while getting reference limit from lendingRiskVariableSnapshotDao of applicationId: {}, {}", lendingApplication.getId(), e);
        }
        return referencesLimit;
    }

    public Integer getToBeShownReferences(Long referencesLimit) {
        try {
            HashMap<Long, Integer> requiredToShownMap = new HashMap<>();
            requiredToShownMap.put(3L, 10);
            requiredToShownMap.put(5L, 7);
            requiredToShownMap.put(10L, 15);
            return requiredToShownMap.get(referencesLimit);
        } catch (Exception e) {
            log.error("Exception occurred while getting toBeShown value ", e);
        }
        return null;
    }

    public ApiResponse<?> getMerchantPermissions(BasicDetailsDto merchant) {
        try {
            Long merchantId = merchant.getId();
            LendingMerchantPermissions lendingMerchantPermissions = lendingMerchantPermissionsDao.findByMerchantId(merchantId);
            LendingMerchantPermissionsDto dto = new LendingMerchantPermissionsDto();
            if (Objects.isNull(lendingMerchantPermissions)) {
                log.info("lendingMerchantPermissions not found of merchantId: {}", merchantId);
                dto.setLocationPermissionIsActive(false);
                dto.setSmsPermissionIsActive(false);
                dto.setSmsPermissionDate(null);
                dto.setLocationPermissionDate(null);
                return new ApiResponse<>(dto);
            }
            log.info("lendingMerchantPermissions: {} of merchantId: {}", lendingMerchantPermissions, merchantId);
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss");

            dto.setLocationPermissionIsActive(lendingMerchantPermissions.getLocationPermissionActive());
            dto.setSmsPermissionIsActive(lendingMerchantPermissions.getSmsPermissionActive());
            dto.setSmsPermissionDate(sdf.format(lendingMerchantPermissions.getSmsPermissionDate()));
            dto.setLocationPermissionDate(sdf.format(lendingMerchantPermissions.getLocationPermissionDate()));
            return new ApiResponse<>(dto);

        } catch (Exception e) {
            log.error("Error occurred while fetching merchant permissions of merchantId: {} {}", merchant.getId(), e);
        }
        return new ApiResponse<>(false, "Something Went Wrong while getting merchant permissions.");
    }

    public ApiResponse<?> updateMerchantPermissions(BasicDetailsDto merchant, LendingMerchantPermissionsDto dto) {
        try {
            Long merchantId = merchant.getId();
            if (Objects.isNull(dto) || Objects.isNull(dto.getSmsPermissionIsActive()) || Objects.isNull(dto.getLocationPermissionIsActive()) || Objects.isNull(dto.getLocationPermissionDate()) || Objects.isNull(dto.getSmsPermissionDate())) {
                return new ApiResponse<>(false, "Invalid Request Body!");
            }
            Date locationPermissionDate = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(dto.getLocationPermissionDate());
            Date smsPermissionDate = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(dto.getSmsPermissionDate());
            log.info("locationPermissionDate: {} smsPermissionDate: {} for merchantId: {}", locationPermissionDate, smsPermissionDate, merchantId);

            LendingMerchantPermissions lendingMerchantPermissions = lendingMerchantPermissionsDao.findByMerchantId(merchantId);
            if (Objects.isNull(lendingMerchantPermissions)) {
                lendingMerchantPermissions = new LendingMerchantPermissions();
                lendingMerchantPermissions.setMerchantId(merchantId);
            }
            lendingMerchantPermissions.setLocationPermissionActive(dto.getLocationPermissionIsActive());
            lendingMerchantPermissions.setSmsPermissionActive(dto.getSmsPermissionIsActive());
            lendingMerchantPermissions.setLocationPermissionDate(locationPermissionDate);
            lendingMerchantPermissions.setSmsPermissionDate(smsPermissionDate);
            lendingMerchantPermissionsDao.save(lendingMerchantPermissions);
            log.info("Successfully updated merchant permissions of merchantId: {}", merchantId);
            funnelService.submitEvent(merchant.getId(), null, null,
                    FunnelEnums.StageId.PERMISSION_PAGE, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString());
            return new ApiResponse<>(true, "Successfully updated merchant permissions!");

        } catch (Exception e) {
            log.error("Error occurred while updating merchant permissions of merchantId: {} {}", merchant.getId(), e);
        }
        return new ApiResponse<>(false, "Something Went Wrong while updating merchant permissions.");
    }
}
