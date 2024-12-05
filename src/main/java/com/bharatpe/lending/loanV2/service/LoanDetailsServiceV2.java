package com.bharatpe.lending.loanV2.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingDisbursalStageDao;
import com.bharatpe.common.dao.LendingPancardDao;
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
import com.bharatpe.lending.common.query.dao.LendingApplicationDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingRiskVariablesDaoSlave;
import com.bharatpe.lending.common.query.dao.MileStoneDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.query.entity.LendingRiskVariablesSlave;
import com.bharatpe.lending.common.query.entity.MileStoneSlave;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.PincodeCityStateMappingDTO;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.Deeplink;
import com.bharatpe.lending.constant.EligibilityIframeConstants;
import com.bharatpe.lending.constant.HomepageCardsConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.loanV2.dto.CreditScoreReportDetailDTO;
import com.bharatpe.lending.loanV2.dto.LoanAndCreditCardDetailDTO;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.loanV2.handlers.BureauHandler;
import com.bharatpe.lending.loanV2.handlers.FinanceUtilsHandler;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.constants.RTEConstants;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.service.*;
import com.bharatpe.lending.util.CommonUtil;
import com.bharatpe.lending.util.LoanUtil;
import com.bharatpe.lending.loanV3.revamp.dto.EnachModeDTO;
import com.bharatpe.lending.util.MongoPublisherUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

import static java.util.Objects.nonNull;

@Data
@Service
@Slf4j
public class LoanDetailsServiceV2 {
    @Autowired
    private LendingRiskVariablesDaoSlave lendingRiskVariablesDaoSlave;
    @Autowired
    private LendingApplicationDaoSlave lendingApplicationDaoSlave;
    @Autowired
    private LendingResubmitReasonCountDao lendingResubmitReasonCountDao;

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

    @Autowired
    CommonUtil commonUtil;

    @Value("${eligibility.refresh.window:1}")
    int eligibilityRefreshWindow;

    @Value("${gst3b.ineligible.source:LOW_TRANSACTION}")
    List<String> gst3bIneligibleSourceList;


    @Value("${loan.details.refresh.window:15}")
    int loanDetailsRefreshWindow;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MerchantSummaryHandler merchantSummaryHandler;

    @Autowired
    LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Value("${club.eligible.loan.cache:true}")
    Boolean clubEligibleLoanCache;

    @Value("${merchant.references.min.score}")
    Integer minScore;

    @Value("${lender.assign.rollout}")
    Integer lenderAssignmentNewFlowRollOutPercent;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    public static List<Long> exceptedMerchantList = Arrays.asList(123455L, 1334555L);

    public static Set<String> restrictedRelations = new HashSet<>(Arrays.asList(ReferenceRelation.MOTHER.name(), ReferenceRelation.FATHER.name(), ReferenceRelation.WIFE.name(), ReferenceRelation.HUSBAND.name()));

    public static final Integer MAX_UNIQUE_RELATION = 2;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Autowired
    FunnelService funnelService;

    @Autowired
    CleverTapEventService cleverTapEventService;

    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    @Value("${abfl.rollout.percent:10}")
    Integer rolloutAbflPercent;

    @Autowired
    BankStatementSessionDetailsDao bankStatementSessionDetailsDao;

    @Value("${eligiblity.iframe.cache.time.minutes:5}")
    Integer eligibilityIframeCachTtl;

    @Value("${eligiblity.iframe.enabled:false}")
    Boolean eligibilityIframeEnabled;

    @Value("${eligiblity.iframe.debug:false}")
    Boolean eligibilityIframeDebug;

    @Value("${edi.assignment.model:false}")
    Boolean assignEdiModelFromModelAssignmentEngine;

    @Value("${aadharNach.rollout.percent:10}")
    Integer aadharNachRolloutPercent;

    @Value("${homepage.cards.enabled:true}")
    Boolean homepageCardsEnabled;

    @Value("${homepage.cards.cache.time.minutes:5}")
    Integer homepageCardsCacheTtl;

    @Autowired
    IEdiModelAssignment iEdiModelAssignment;

    @Autowired
    Gst3bSessionDetailsDao gst3bSessionDetailsDao;

    @Autowired
    BankStatementWhitelistedBanksDao bankStatementWhitelistedBanksDao;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    LmsFieldValuesDao lmsFieldValuesDao;

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Value(("${bankstatement.enabled:false}"))
    boolean bankStatementEnabled;

    @Value("${gst3b.session.tat:3}")
    Integer gst3bSessionTat;

    @Value("${gst3b.rollout.percent:10}")
    Integer gst3bRolloutPercent;

    @Value("${account-aggregator.rollout.percent:10}")
    Integer accountAggregatorRolloutPercent;

    @Value("${bank_statement.session.tat:3}")
    Integer bankStatementSessionTat;

    @Value("${account-aggregator.session.tat:3}")
    Integer accountAggregatorSessionTat;

    @Autowired
    FinanceUtilsHandler financeUtilsHandler;

    @Autowired
    ExcessNachService excessNachService;

    @Autowired
    BankStatementService bankStatementService;

    @Autowired
    LendingPancardDao lendingPancardDao;

    @Value("${gold.loan.merchant.eligibilty.ttl:5}")
    private Integer goldLoanMerchantEligibilityTTL;

    private final String glEligibilityRedisTokenKey = "gl_eligibilty_";


    @Value("${bank-statement.session.limit.for.day:5}")
    Integer bankStatementSessionLimitForDay;

    @Value("${eligibleLoan.creation.skip.rollout:0}")
    Integer eligibleLoanCreationSkipRollout;

    @Value("${upinach.max.loan.amount:50000}")
    Double maxLoanAmountForNachUPI;

    @Autowired
    MileStoneHelperServicev3 mileStoneHelperServicev3;

    @Value("${homepage.widget.deeplink:bharatpe://homev2/loans}")
    String homePageRedirectionDeeplink;

    @Autowired
    MileStoneDaoSlave mileStoneDaoSlave;

    @Autowired
    @Lazy
    MerchantLoansService merchantLoansService;

    @Autowired
    MileStoneProgramService mileStoneProgramService;

    @Value("${upi.nach.rollout.percent:10}")
    Integer upiNachRolloutPercent;

    @Autowired
    MongoPublisherUtil mongoPublisherUtil;

    private static final List<KycDocType> kycMandatoryDocs = Arrays.asList(KycDocType.PAN_NO, KycDocType.PAN_CARD, KycDocType.SELFIE, KycDocType.POA);

    public ApiResponse<?> getLoanDetails(LoanDetailsRequest request, BasicDetailsDto merchant, String token, Boolean flagForUwToSkipCache, EligibilityRequestSource offerCheckedBy) throws BureauCallMaskedApiException {
        try {
            if (Objects.nonNull(request) &&
                    Objects.nonNull(request.getPancard()) && Objects.nonNull(request.getPincode())) {
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
            loanDetailsResponse.setExcessNachAmount(excessNachService.getExcessNachAmount(merchant.getId()));
            if (loanUtil.hasActiveLoan(merchant)) {
                log.info("active loan merchant:{}", merchant.getId());
                LendingApplication topupApplication = lendingApplicationDao.findOpenTopUpApplication(merchant.getId(), "TOPUP");
                if(!ObjectUtils.isEmpty(topupApplication) && !"rejected".equals(topupApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(topupApplication.getLoanDisbursalStatus())){
                    Experian experian = experianDao.getByMerchantId(merchant.getId());
                    boolean isIOS = request != null && request.isIOS();
                    LoanApplicationDetails topupApplicationDetails = setApplicationDetails(loanDetailsResponse, topupApplication, token, isIOS, experian, merchant);
                    loanDetailsResponse.setTopupLoanApplication(topupApplicationDetails);
                    if("draft".equalsIgnoreCase(topupApplication.getStatus()) && Lender.LIQUILOANS_NBFC.name().equalsIgnoreCase(topupApplication.getLender())){
                        checkKycForTopup(loanDetailsResponse, topupApplication, merchant, experian);
                    }
                    else{
                        loanDetailsResponse.setKycStatus(KycStatus.APPROVED);
                        loanDetailsResponse.setKycDone(true);
                    }
                    loanDetailsResponse.setShowReferencePage(false);
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
         // Deprecated due to ML-745
         //   loanDetailsResponse.setEligibleForCallback(checkEligibilityForCallback(merchant.getId()));
            LendingPaymentSchedule lendingPaymentSchedule1 = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), "INACTIVE");
            if (!ObjectUtils.isEmpty(lendingPaymentSchedule1)) {
                loanDetailsResponse.setIneligible(RejectionReason.LOW_TRANSACTION.getReason());
                loanDetailsResponse.setKycStatus(KycStatus.APPROVED);
                return new ApiResponse<>(loanDetailsResponse);
            }
            LendingApplication draftApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchant.getId(),"draft");
            String deReferencesCacheKey = LendingConstants.GET_MERCHANTS_REFERENCES_CACHE_KEY + merchant.getId();
            if(draftApplication != null && !"TOPUP".equalsIgnoreCase(draftApplication.getLoanType())) {
                if(lendingMerchantReferencesDao.findByMerchantIdAndApplicationId(merchant.getId(), draftApplication.getId()).isEmpty()
                        && Objects.isNull(lendingCache.get(deReferencesCacheKey))) {
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
                Integer dateDiff = 731;
                if(LoanType.TOPUP.name().equalsIgnoreCase(openApplication.getLoanType()) && Lender.ABFL.name().equalsIgnoreCase(openApplication.getLender())){
                    dateDiff = 365;
                }
                if(easyLoanUtil.percentScaleUp(openApplication.getMerchantId(), lenderAssignmentNewFlowRollOutPercent)){
                    lendingApplicationKycDetails=lendingApplicationKycDetailsDao.findSuccessKycDetails(openApplication.getMerchantId(), openApplication.getLender(), dateDiff);
                }

                if(!loanUtil.isRepeatLoan(openApplication.getMerchantId()) ||
                        (ObjectUtils.isEmpty(lendingApplicationKycDetails)
                        )){
                    lendingApplicationKycDetails=lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(openApplication.getId());
                } else if("draft".equalsIgnoreCase(openApplication.getStatus())) {
                    loanDetailsResponse.setKycDone(true);
                    if(!KycStatus.APPROVED.name().equalsIgnoreCase(openApplication.getCkycStatus())){
                        openApplication.setCkycStatus(KycStatus.APPROVED.name());
                        openApplication.setCkycDate(new Date());
                        lendingApplicationDao.save(openApplication);
                    }
                    LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(openApplication.getId());
                    if(ObjectUtils.isEmpty(lendingApplicationDetails)){
                        lendingApplicationDetails = new LendingApplicationDetails();
                        lendingApplicationDetails.setApplicationId(openApplication.getId());
                    }
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
                //kyc checks can be removed from here...
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


            checkEligibility(loanDetailsResponse, request, experian, merchant, flagForUwToSkipCache, offerCheckedBy);
            cacheLoanDetailsData(loanDetailsResponse, loanDetailsCacheKey, loanDetailsRefreshWindow);
            log.info("returning response from database");
            return new ApiResponse<>(loanDetailsResponse);
        } catch (BureauCallMaskedApiException e) {
            throw (e);
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
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(lendingApplicationKycDetails.getApplicationId());

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
                        String dob = KycUtils.getDOB(kycDoc);
                        log.info("dob from POA kyc doc for merchant: {}, {}",dob,merchant.getId());
                        lendingApplicationKycDetails.setDob(dob);
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
                funnelService.submitEvent(merchant.getId(), null,lendingApplicationKycDetails.getApplicationId(),lendingApplication.isPresent()?lendingApplication.get().getLoanType():null,
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
                                  Experian experian, BasicDetailsDto merchant, Boolean flagForUwToSkipCache, EligibilityRequestSource offerCheckedBy) throws BureauCallMaskedApiException {
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

        if (loanUtil.isInternalMerchant(merchant.getId()) || assignEdiModelFromModelAssignmentEngine) {
            loanDetailsResponse.setEdiDaysModel(iEdiModelAssignment.assignModel(merchant.getId()).getNoOfEdiDaysInAWeek());
        }

        EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdAndLoanTypeNotTopup(merchant.getId());
        String bureauConsentKey = LendingConstants.BUREAU_CONSENT_KEY_PREFIX+merchant.getId();
        if (Objects.nonNull(lendingCache.get(bureauConsentKey))) {
            eligibilityRefreshWindow = 0;
            lendingCache.delete(bureauConsentKey);
        }
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
            eligibility = createEligibility(eligibleLoan, merchant.getId());
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
        GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchant.getId(), null,
                request.getAppVersion(), isClubV2, request.getMappedMobile(), request.getStageOneHitId(), request.getStageTwoHitId(),
                request.getSkipBureau(), request.getSkipMaskedMobileException(), null, null, true, loanDetailsResponse,null, flagForUwToSkipCache,offerCheckedBy);
        Double eligibleAmount = 0D;
        if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
            log.info("Global limit for merchant:{} is {}", merchant.getId(), globalLimitResponse.getData().getGlobalLimit());
            eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
            isDerog.setValue(globalLimitResponse.getData().isDerog());
        }
        if (eligibleAmount > 0D) {
            log.info("Eligibility found for merchant:{}", merchant.getId());
            EligibleLoan eligibleLoan1 = recomputeEligibleLoan(globalLimitResponse, null, merchant.getId(), false);
            eligibility = createEligibility(eligibleLoan1, merchant.getId());
        }
        if (eligibility != null) {
            loanDetailsResponse.setEligibility(eligibility);
            return;
        }
        log.info("Eligibility not found for merchant:{}", merchant.getId());
        loanDetailsResponse.setIneligible(getIneligibleReason(merchant.getId(), isDerog, experian.getPincode(), globalLimitResponse));
        loanDetailsResponse.setChangeBankAccount(!loanUtil.isEnachBank(merchant.getId()));
    }


    public EligibleLoan recomputeEligibleLoan(GlobalLimitResponse globalLimitResponse, Double customAmount, Long merchantId, boolean skipEligibleLoanDbEntryCreation) {
        if (Objects.isNull(globalLimitResponse) || Objects.isNull(globalLimitResponse.getData())) {
            log.info("Global Limit not found");
            return null;
        }
        Double finalLimit = globalLimitResponse.getData().getGlobalLimit();
        String loanType = globalLimitResponse.getData().getLoanType();
        Double version = globalLimitResponse.getData().getVersion();
        EligibleLoan eligibleLoan = null;
        try {
//            eligibleLoanDao.deleteByMerchantId(merchantId);
            List<GlobalLimitResponse.OfferDetail> offerDetails = globalLimitResponse.getData().getOfferDetails();
            offerDetails.sort(Comparator.comparingInt(GlobalLimitResponse.OfferDetail::getTenure));
            for (GlobalLimitResponse.OfferDetail offerDetail : offerDetails) {
                log.info("Tenure: {}, finalLimit: {}, loanAmount: {}, customAmount: {}", offerDetail.getTenure(), finalLimit, offerDetail.getLoanAmount(), customAmount);
                if (Objects.nonNull(customAmount) && customAmount < finalLimit && customAmount <= offerDetail.getLoanAmount()) {
                    eligibleLoan = loanUtil.calculateLoanBreakup(offerDetail, merchantId, loanType, customAmount, null, version, skipEligibleLoanDbEntryCreation);
                }
                if (finalLimit <= offerDetail.getMaxLoanAmount() && finalLimit <= (offerDetail.getLoanAmount())) {
                    eligibleLoan = loanUtil.calculateLoanBreakup(offerDetail, merchantId, loanType, finalLimit, null, version, skipEligibleLoanDbEntryCreation);
                }
            }
//            eligibleLoanDao.deleteGreaterOffersByMerchantId(merchantId, finalLimit);
        } catch (Exception e) {
            log.error("Exception while recomputing eligible loan for merchant:{}", merchantId, e);
        }

        return eligibleLoan;
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

    public String getIneligibleReason(Long merchantId, MutableBoolean isDerog, Integer pincode, GlobalLimitResponse globalLimitResponse) {
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

    public Eligibility createEligibility(EligibleLoan eligibleLoan, Long merchantId) {
        try {
            if(easyLoanUtil.percentScaleUp(merchantId, eligibleLoanCreationSkipRollout)){
                if (ObjectUtils.isEmpty(eligibleLoan) || "TOPUP".equalsIgnoreCase(eligibleLoan.getLoanType())) {
                    return null;
                }
            }
            else{
                eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdAndLoanTypeNotTopup(merchantId);
            }
            if (ObjectUtils.isEmpty(eligibleLoan)) {
                return null;
            }
            log.info("Creating eligibility for merchant:{}", merchantId);
            return Eligibility.builder()
                    .loanAmount(eligibleLoan.getAmount())
                    .arrangerFee(eligibleLoan.getProcessingFee())
                    .interestRate(eligibleLoan.getRateOfInterest())
                    .initialRoi(eligibleLoan.getInitialRoi())
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
            log.error("Exception in createEligibility for merchant:{}, {}", merchantId, Arrays.asList(e.getStackTrace()));
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
//                    Date currentRequestTimestamp = dateTimeUtil.getCurrentDate();
//                    Date resubmitCreatedAt = lendingResubmitTask.getCreatedAt();
//                    Date opsStartTimestamp = dateTimeUtil.getDateAtTime(resubmitCreatedAt, 9, 0, 0, 0);
//                    Date opsSameDayProcessTimestamp = dateTimeUtil.getDateAtTime(resubmitCreatedAt, 18, 0, 0, 0);
//                    Date opsNextDayProcessTimestamp = dateTimeUtil.getDateAtTime(dateTimeUtil.getDatePlusDays(resubmitCreatedAt, 24), 18, 0, 0, 0);
//                    if ((resubmitCreatedAt.before(opsStartTimestamp) && currentRequestTimestamp.after(opsSameDayProcessTimestamp)) || ((resubmitCreatedAt.after(opsStartTimestamp)) && (currentRequestTimestamp.after(opsNextDayProcessTimestamp)))) {
//                        applicationDetails.setApplicationStatus("RESUBMIT");
//                        applicationDetails.setResubmitReason(lendingResubmitTask.getResubmitReason());
//                    }
                    applicationDetails.setApplicationStatus("RESUBMIT");
                    String pendingResubmitReason = getResubmitReason(openApplication.getId(), openApplication.getMerchantId());
                    applicationDetails.setResubmitReason(Objects.nonNull(pendingResubmitReason) ? pendingResubmitReason : lendingResubmitTask.getResubmitReason());
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
                if(loanUtil.isInternalMerchant(openApplication.getMerchantId()) || easyLoanUtil.percentScaleUp(openApplication.getMerchantId(), aadharNachRolloutPercent)){
                    String lender = openApplication.getLender();
                    if("TOPUP".equalsIgnoreCase(openApplication.getLoanType()) || Lender.LDC.name().equalsIgnoreCase(lender) || Lender.MAMTA.name().equalsIgnoreCase(lender) ||
                            Lender.MAMTA0.name().equalsIgnoreCase(lender) || Lender.MAMTA1.name().equalsIgnoreCase(lender) || Lender.MAMTA2.name().equalsIgnoreCase(lender)){
                        applicationDetails.setEnachMode(EnachMode.NB_DC.name());
                    }
                    else {
                        List<EnachModeDTO> enachModes = loanUtil.getEnachModes(openApplication.getMerchantId());
                        if (openApplication.getLoanAmount() > maxLoanAmountForNachUPI && !easyLoanUtil.percentScaleUp(openApplication.getMerchantId(),upiNachRolloutPercent)) {
                            enachModes.removeIf(mode -> mode.getName().equals(EnachMode.UPI.name()));
                        }
                        if (Objects.nonNull(enachModes)) {
                            applicationDetails.setEnachMode(enachModes.stream().map(EnachModeDTO::getName)
                                    .collect(Collectors.toList()).toString()
                                    .replace("[", "")
                                    .replace("]", ""));
                        }
                    }
                    BharatPeEnachResponseDTO bharatPeEnach = enachHandler.findByMerchantIdAndApplicationId(openApplication.getMerchantId(), openApplication.getId());
                    if(ObjectUtils.isEmpty(bharatPeEnach)){
                        applicationDetails.setNachStartedAt(null);
                        applicationDetails.setNachSessionStatus(null);
                        applicationDetails.setNachSessionMode(null);
                    }
                    else{
                        Long nachStartedAtEpoch = bharatPeEnach.getCreatedAt().getTime();
                        applicationDetails.setNachStartedAt(nachStartedAtEpoch);
                        applicationDetails.setNachSessionStatus(bharatPeEnach.getSessionStatus());
                        applicationDetails.setNachSessionMode(bharatPeEnach.getMode());
                    }
                }
            }

            if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus())) {
                applicationDetails.setEnachDone("APPROVED".equalsIgnoreCase(openApplication.getNachStatus()) || "PENDING_VERIFICATION".equalsIgnoreCase(openApplication.getNachStatus()));
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
//                int tat = loanUtil.getApplicationTAT(openApplication);
                applicationDetails.setTransferDays(loanUtil.getApplicationTatMessage(openApplication));
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

    public String getResubmitReason(Long applicationId, Long merchantId){
        String reason = "";
        List<LendingResubmitReasonCount> lendingResubmitReasonCountList = lendingResubmitReasonCountDao.findByApplicationIdAndMerchantId(applicationId, merchantId);
        if(ObjectUtils.isEmpty(lendingResubmitReasonCountList))return null;
        Integer maxCount = -1;
        for(LendingResubmitReasonCount lendingResubmitReasonCount : lendingResubmitReasonCountList){
            if(lendingResubmitReasonCount.getResubmitCount() > maxCount)maxCount = lendingResubmitReasonCount.getResubmitCount();
        }
        for(LendingResubmitReasonCount lendingResubmitReasonCount : lendingResubmitReasonCountList){
            if(lendingResubmitReasonCount.getResubmitCount() != maxCount)continue;
            if(!ObjectUtils.isEmpty(lendingResubmitReasonCount.getResubmitReason()) && Objects.nonNull(lendingResubmitReasonCount.getResubmitDone())
             && !lendingResubmitReasonCount.getResubmitDone()){
                if("".equals(reason))reason = reason + lendingResubmitReasonCount.getResubmitReason();
                else reason = reason + "," + lendingResubmitReasonCount.getResubmitReason();
            }
        }
        if("".equals(reason))return null;
        return reason;
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
            if(ObjectUtils.isEmpty(openApplication.getNachStatus())){
                loanDashboardService.deleteLoanDashboardCache(openApplication.getMerchantId());
            }
            log.info("marking nach status approved for {}, {}", openApplication.getMerchantId(), openApplication.getId());
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
        Long days = 60L;
        BureauResponseDTO bureauResponseDTO = bureauHandler.getBureauData(pan_card, merchantId, mobile, days);
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

    public ApiResponse<?> getMaskedMobileNos(BasicDetailsDto merchant, CommonAPIRequest commonAPIRequest) {
        log.info("getMaskedMobileNos");
        String pan_card = commonAPIRequest.getPayload().get("pan_card") != null ? commonAPIRequest.getPayload().get("pan_card").toString() : null;
        String stageOneHitId = commonAPIRequest.getPayload().get("stage_one_hit_id") != null ? commonAPIRequest.getPayload().get("stage_one_hit_id").toString() : null;
        String stageTwoHitId = commonAPIRequest.getPayload().get("stage_two_hit_id") != null ? commonAPIRequest.getPayload().get("stage_two_hit_id").toString() : null;
        String mobile = merchant.getMobile().substring(2);
        Long merchantId = merchant.getId();
        log.info("calling bureau handler");
        BureauResponseDTO bureauResponseDTO = bureauHandler.getMaskedMobileNos(pan_card, merchantId, mobile, stageOneHitId, stageTwoHitId);
        if (ObjectUtils.isEmpty(bureauResponseDTO) || Objects.isNull(bureauResponseDTO.getBureauData()))
            return new ApiResponse<>(false, "Masked mobile details not found");
        log.info("Masked Mobile details fetched successfully");
        return new ApiResponse<>(bureauResponseDTO);
    }

    public ApiResponse<?> getLoanAndCreditCardDetail(BasicDetailsDto merchant, CommonAPIRequest commonAPIRequest) {
        String pan_card = commonAPIRequest.getPayload().get("pan_card") != null ? commonAPIRequest.getPayload().get("pan_card").toString() : null;
        String mobile = merchant.getMobile().substring(2);
        Long merchantId = merchant.getId();
        BureauResponseDTO bureauResponseDTO = bureauHandler.getBureauData(pan_card, merchantId, mobile, 30L);

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

            if ("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()))
            {
                log.info("lending application loan Type is {} ",lendingApplication.getLoanType());
                return new ApiResponse<>(false,"Application type is Topup");
            }
            log.info("applicationId: {} found of merchantId: {}", lendingApplication.getId(), merchantId);

            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                log.info("lrvs not found of applicationId: {}", lendingApplication.getId());
                return new ApiResponse<>(false, "LRVS details not found for given merchantId");
            }

            if(lendingRiskVariablesSnapshot.getNewContactReferenceLogic()) {
                //new logic
                return handleNewContactReferenceLogic(merchantId, lendingRiskVariablesSnapshot, lendingApplication);
            }

            Long referencesLimit = getReferenceLimit(lendingApplication);
            if (referencesLimit == 3L) {
                //old version & 3 references - v3 changes
                return handleThreeReferencesLimit(merchantId, lendingApplication);
            }

            log.info("references limit for merchantId:{} {}", merchant.getId(), referencesLimit);
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
                responseDto = MerchantReferencesResponseDto.builder().
                        references(deReferenceList).minScore(minScore).limit(referencesLimit).ineligible(false).build();

            }
            submitFunnelEvent(merchantId, lendingApplication, FunnelEnums.StageId.REFERENCE_PAGE, FunnelEnums.StageEvent.INITIATED);
            return new ApiResponse<>(responseDto);
        } catch (Exception e) {
            log.error("Error occurred while fetching merchant references of merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong while getting merchant references!");
    }

    private ApiResponse<?> handleNewContactReferenceLogic(Long merchantId, LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot, LendingApplication lendingApplication) {
        log.info("New contact reference version for merchantId: {}", merchantId);
        Long referenceCount = lendingRiskVariablesSnapshot.getReferenceCount();
        MerchantReferencesV2ResponseDto responseDto;
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());

        if (!ObjectUtils.isEmpty(referenceCount) && referenceCount > 0) {
            log.info("lendingApplicationDetails of applicationId: {}, {}", lendingApplication.getId(), lendingApplicationDetails);

            if (Objects.isNull(lendingApplicationDetails)) {
                lendingApplicationDetails = new LendingApplicationDetails();
                lendingApplicationDetails.setApplicationId(lendingApplication.getId());
                lendingApplicationDetailsDao.save(lendingApplicationDetails);
            }

            responseDto = MerchantReferencesV2ResponseDto.builder()
                    .limit(referenceCount)
                    .ineligible(false)
                    .build();
        } else {
            responseDto = MerchantReferencesV2ResponseDto.builder()
                    .limit(null)
                    .ineligible(true)
                    .build();
        }

        submitFunnelEvent(merchantId, lendingApplication, FunnelEnums.StageId.REFERENCE_PAGE, FunnelEnums.StageEvent.INITIATED);
        return new ApiResponse<>(responseDto);
    }

    private ApiResponse<?> handleThreeReferencesLimit(Long merchantId, LendingApplication lendingApplication) {
        log.info("3 references required for merchantId: {}", merchantId);

        List<LendingMerchantReferences> savedMerchantReferencesList = lendingMerchantReferencesDao.findByMerchantIdAndApplicationId(merchantId, lendingApplication.getId());
        MerchantReferencesV2ResponseDto responseDto;

        if (!ObjectUtils.isEmpty(savedMerchantReferencesList) && savedMerchantReferencesList.size() == 3) {
            log.info("Populating existing 3 references for merchantId: {}", merchantId);
            List<MerchantReferencesV2ResponseDto.MerchantReferenceData> references = savedMerchantReferencesList.stream()
                    .map(reference -> MerchantReferencesV2ResponseDto.MerchantReferenceData.builder()
                            .name(reference.getReferenceName())
                            .phoneNumber(reference.getReferenceNumber())
                            .relation(reference.getInferredRelation())
                            .build())
                    .collect(Collectors.toList());

            responseDto = MerchantReferencesV2ResponseDto.builder()
                    .limit((long) savedMerchantReferencesList.size())
                    .references(references)
                    .ineligible(false)
                    .build();
        } else {
            responseDto = MerchantReferencesV2ResponseDto.builder()
                    .limit(3L)
                    .ineligible(false)
                    .build();
        }
        submitFunnelEvent(merchantId, lendingApplication, FunnelEnums.StageId.REFERENCE_PAGE, FunnelEnums.StageEvent.INITIATED);
        return new ApiResponse<>(responseDto);
    }


    private void submitFunnelEvent(Long merchantId, LendingApplication lendingApplication, FunnelEnums.StageId stageId, FunnelEnums.StageEvent stageEvent) {
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantId);
        if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
            funnelService.submitEventV3(merchantId, null, lendingApplication.getId(),
                    stageId, stageEvent, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
        else{
            funnelService.submitEvent(merchantId, null, lendingApplication.getId(),
                    stageId, stageEvent, LocalDateTime.now().toString());
        }
    }

    public ApiResponse<?> validateMerchantReferences(BasicDetailsDto merchant, List<ValidateMerchantReferencesRequestDto> referenceList) {
        try {
            Long merchantId = merchant.getId();
            if (Objects.isNull(referenceList) || referenceList.isEmpty()) {
                return new ApiResponse<>(false, "Requested Reference List for validation is empty!");
            }
            List<MerchantReference> validatedData = dsHandler.validateMerchantReferences(merchantId, referenceList);
            if (Objects.isNull(validatedData)) {
                return new ApiResponse<>(false, "Something went wrong. Please retry.");
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

            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(applicationId);
            if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                log.info("lrvs not found of applicationId: {}", applicationId);
                return new ApiResponse<>(false, "LRVS details not found for given merchantId");
            }

            Boolean isIneligible = requestDto.getIneligible();
            if (Objects.isNull(isIneligible)) {
                return new ApiResponse<>(false, "ineligible field can not be null!");
            }
            if (isIneligible) {
                rejectingLoanDueToInsufficientReferences(lendingApplication,LendingConstants.REJECTION_REASON_1);
                log.info("Successfully rejected applicationId: {} because of insufficient references of merchantId: {}", applicationId, merchantId);
                return new ApiResponse<>(true, "Successfully rejected applicationId because of insufficient references");
            }
            List<MerchantReference> requestedReferenceList = requestDto.getReferences();
            if (Objects.isNull(requestedReferenceList)) {
                return new ApiResponse<>(false, "references field can not be empty!");
            }
            if (!hasValidRestrictedRelations(requestedReferenceList)) {
                return new ApiResponse<>(false, "References Relations are not associated correctly!");
            }
            for (MerchantReference reference : requestedReferenceList) {
                if (!isValid(reference, merchant)) {
                    return new ApiResponse<>(false, "references are not valid!");
                }
            }
            List<LendingMerchantReferences> savedMerchantReferencesList = lendingMerchantReferencesDao.findByMerchantIdAndApplicationId(merchantId, applicationId);
            if(!lendingRiskVariablesSnapshot.getNewContactReferenceLogic() && requestedReferenceList.size() == 3) {
                //old version & 3 references - v3 changes.
                processThreeReferences(merchant, lendingApplication, savedMerchantReferencesList, requestedReferenceList);
            }else {
                boolean toBeAdded;
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
                        lendingMerchantReferences.setInferredRelation(requestedReferences.getInferredRelation());
                        lendingMerchantReferences.setMerchantId(merchantId);
                        lendingMerchantReferences.setApplicationId(applicationId);

                        if(!lendingRiskVariablesSnapshot.getNewContactReferenceLogic()) {
                            //For old flow update below parameters for 5/10 contacts, for 3 contacts - only name & number will be saved
                            lendingMerchantReferences.setFraudFlag(requestedReferences.getFraudFlag());
                            lendingMerchantReferences.setInferredCompany(requestedReferences.getInferredCompany());
                            lendingMerchantReferences.setInferredName(requestedReferences.getInferredName());
                            lendingMerchantReferences.setInferredLocation(requestedReferences.getInferredLocation());
                            lendingMerchantReferences.setInferredNameConfidence(requestedReferences.getInferredNameConfidence());
                            lendingMerchantReferences.setInferredOccupation(requestedReferences.getInferredOccupation());
                            lendingMerchantReferences.setInferredRelation(requestedReferences.getInferredRelation());
                            lendingMerchantReferences.setNumHits(requestedReferences.getNumHits());
                            lendingMerchantReferences.setScore(requestedReferences.getScore());
                        }
                        lendingMerchantReferencesDao.save(lendingMerchantReferences);
                        log.info("Successfully saved merchant reference: {} of merchantId: {}", requestedReferences, merchantId);
                    }
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
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantId, lendingApplication);
            if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                funnelService.submitEventV3(merchant.getId(), null, applicationId,
                        FunnelEnums.StageId.REFERENCE_PAGE, FunnelEnums.StageEvent.SUBMITTED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
            }
            else{
                funnelService.submitEvent(merchant.getId(), null, applicationId,
                        FunnelEnums.StageId.REFERENCE_PAGE, FunnelEnums.StageEvent.SUBMITTED, LocalDateTime.now().toString());
            }
            loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, applicationId, LendingViewStates.AGREEMENT_PAGE);
            return new ApiResponse<>(true, "Successfully updated merchant References!");
        } catch (Exception e) {
            log.error("Error occurred while updating merchant references of merchantId: {} {}", merchant.getId(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong while updating merchant references!");
    }

    private void processThreeReferences(BasicDetailsDto merchant, LendingApplication lendingApplication, List<LendingMerchantReferences> savedMerchantReferencesList, List<MerchantReference> requestedReferenceList) {
        log.info("Processing three references for merchantId: {} & applicationId: {}", merchant.getId(), lendingApplication.getId());
        if(ObjectUtils.isEmpty(savedMerchantReferencesList)) {
            log.info("Adding fresh references for merchantId: {}", merchant.getId());
            for(MerchantReference requestedReference : requestedReferenceList) {
                LendingMerchantReferences lendingMerchantReferences = new LendingMerchantReferences();
                lendingMerchantReferences.setReferenceName(requestedReference.getName());
                lendingMerchantReferences.setReferenceNumber(requestedReference.getPhoneNumber());
                lendingMerchantReferences.setInferredRelation(requestedReference.getInferredRelation());
                lendingMerchantReferences.setMerchantId(merchant.getId());
                lendingMerchantReferences.setApplicationId(lendingApplication.getId());
                lendingMerchantReferencesDao.save(lendingMerchantReferences);
                log.info("Successfully saved merchant reference: {} of merchantId: {}", requestedReference.getName(), merchant.getId());
            }
        }else {
            log.info("Updating existing references for merchantId: {}", merchant.getId());
            for(int i = 0 ; i < 3 ; i++) {
                LendingMerchantReferences savedReference = savedMerchantReferencesList.get(i);
                MerchantReference requestedReference = requestedReferenceList.get(i);
                savedReference.setReferenceName(requestedReference.getName());
                savedReference.setReferenceNumber(requestedReference.getPhoneNumber());
                savedReference.setInferredRelation(requestedReference.getInferredRelation());
                lendingMerchantReferencesDao.save(savedReference);
                log.info("Updated reference: {} of merchantId: {}", requestedReference.getName(), merchant.getId());
            }
        }
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
            requiredToShownMap.put(0L, 0);
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

    public ApiResponse<?> getIframeDetails(Long merchantId, String client) {
        try {
            EligibilityIframeResponseDTO responseDTO = new EligibilityIframeResponseDTO();
            if(!eligibilityIframeEnabled){
                responseDTO.setState(EligibilityIframeState.BANNER_NOT_APPLICABLE);
                return new ApiResponse<>(responseDTO);
            }
            String iframeCacheKey = LendingConstants.ELIGIBILITY_IFRAME_KYC_CACHE_KEYWORD + merchantId;
            Object iframeCacheResponse = lendingCache.get(iframeCacheKey);
            if (Objects.nonNull(iframeCacheResponse)) {
                responseDTO = objectMapper.readValue((String) iframeCacheResponse, EligibilityIframeResponseDTO.class);
                if(Objects.nonNull(responseDTO.getState()) && (responseDTO.getState() != EligibilityIframeState.BANNER_NOT_APPLICABLE)
                && !eligibilityIframeDebug){
                    funnelService.submitEvent(merchantId, null, null,
                            FunnelEnums.StageId.IFRAME_BANNER, FunnelEnums.StageEvent.LOADED, responseDTO.getState().name(), client);
                }
                log.info("Iframe responseDTO from cache for {}, {}", merchantId, responseDTO);
                return new ApiResponse<>(responseDTO);
            }
            LendingApplicationSlave lendingApplicationSlave = lendingApplicationDaoSlave.findTop1ByMerchantIdOrderByIdDesc(
                    merchantId);
            if(!ObjectUtils.isEmpty(lendingApplicationSlave)){
                if(ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplicationSlave.getStatus())
                        && Objects.isNull(lendingApplicationSlave.getNachStatus())){
                    responseDTO.setState(EligibilityIframeState.BANNER_PENDING_ENACH);
                    populateEligibilityIframeResponseData(responseDTO, lendingApplicationSlave.getLoanAmount(), lendingApplicationSlave.getInterestRate());
                }
                else if(ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplicationSlave.getStatus())){
                    responseDTO.setState(EligibilityIframeState.BANNER_DRAFT_APPLICATION);
                    populateEligibilityIframeResponseData(responseDTO, lendingApplicationSlave.getLoanAmount(), lendingApplicationSlave.getInterestRate());
                }
                else if(ApplicationStatus.DELETED.name().equalsIgnoreCase(lendingApplicationSlave.getStatus())
                        || ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplicationSlave.getStatus())){
                    checkEligibilityIframeOffer(responseDTO, merchantId);
                }
                else responseDTO.setState(EligibilityIframeState.BANNER_NOT_APPLICABLE);
            }
            else checkEligibilityIframeOffer(responseDTO, merchantId);

            if(Objects.nonNull(responseDTO.getState()) && (responseDTO.getState() != EligibilityIframeState.BANNER_NOT_APPLICABLE)
                    && !eligibilityIframeDebug){
                funnelService.submitEvent(merchantId, null, null,
                        FunnelEnums.StageId.IFRAME_BANNER, FunnelEnums.StageEvent.LOADED, responseDTO.getState().name(), client);
            }
            log.info("Iframe responseDTO for {}, {}", merchantId, responseDTO);
            cacheIframeDetails(merchantId, responseDTO, eligibilityIframeCachTtl);
            return new ApiResponse<>(responseDTO);
        } catch (Exception e) {
            log.error("Error occurred while fetching iframe details for merchantId: {} {} {}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong while getting iframe details.");
    }

    private void checkEligibilityIframeOffer(EligibilityIframeResponseDTO responseDTO, Long merchantId){
        LendingRiskVariablesSlave lendingRiskVariablesSlave = lendingRiskVariablesDaoSlave.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        if(ObjectUtils.isEmpty(lendingRiskVariablesSlave)){
            responseDTO.setState(EligibilityIframeState.BANNER_OFFER_NOT_CHECKED);
            populateEligibilityIframeResponseData(responseDTO, null, null);
        }
        else{
            if(Objects.nonNull(lendingRiskVariablesSlave.getFinalOffer()) && lendingRiskVariablesSlave.getFinalOffer() > 0 &&
                    Objects.isNull(lendingRiskVariablesSlave.getExperianRejection())){
                responseDTO.setState(EligibilityIframeState.BANNER_ELIGIBLE);
                populateEligibilityIframeResponseData(responseDTO, lendingRiskVariablesSlave.getFinalOffer(), lendingRiskVariablesSlave.getRoi());
            }
            else responseDTO.setState(EligibilityIframeState.BANNER_NOT_APPLICABLE);
        }
    }

    private void populateEligibilityIframeResponseData(EligibilityIframeResponseDTO responseDTO, Double loanAmount, Double interestRate){
        String title = null;
        String subtitle = null;
        String buttonText = null;
        String bannerImg = null;
        String loanAmountString = null;
        if(Objects.nonNull(loanAmount))loanAmountString = (loanAmount < 100000) ? String.format("%.0f",loanAmount) : String.format("%.2f", loanAmount/100000) + " Lakh";
        if(EligibilityIframeState.BANNER_OFFER_NOT_CHECKED.equals(responseDTO.getState())){
            title = EligibilityIframeConstants.BANNER_OFFER_NOT_CHECKED.TITLE;
            subtitle = EligibilityIframeConstants.BANNER_OFFER_NOT_CHECKED.SUB_TITLE;
            buttonText = EligibilityIframeConstants.BANNER_OFFER_NOT_CHECKED.BUTTON_TEXT;
            bannerImg = EligibilityIframeConstants.BANNER_OFFER_NOT_CHECKED.BANNER_IMG;
        }
        else if(EligibilityIframeState.BANNER_ELIGIBLE.equals(responseDTO.getState())){
            title = EligibilityIframeConstants.BANNER_ELIGIBLE.TITLE;
            title = title.replace("{{offerAmount}}", loanAmountString);
            subtitle = EligibilityIframeConstants.BANNER_ELIGIBLE.SUB_TITLE;
            subtitle = subtitle.replace("{{offerInterestRate}}", interestRate.toString());
            buttonText = EligibilityIframeConstants.BANNER_ELIGIBLE.BUTTON_TEXT;
            bannerImg = EligibilityIframeConstants.BANNER_ELIGIBLE.BANNER_IMG;
            responseDTO.setOfferAmount(loanAmount);
            responseDTO.setOfferInterestRate(interestRate);
        }
        else if(EligibilityIframeState.BANNER_DRAFT_APPLICATION.equals(responseDTO.getState())){
            title = EligibilityIframeConstants.BANNER_DRAFT_APPLICATION.TITLE;
            title = title.replace("{{loanAmount}}", loanAmountString);
            subtitle = EligibilityIframeConstants.BANNER_DRAFT_APPLICATION.SUB_TITLE;
            subtitle = subtitle.replace("{{interestRate}}", interestRate.toString());
            buttonText = EligibilityIframeConstants.BANNER_DRAFT_APPLICATION.BUTTON_TEXT;
            bannerImg = EligibilityIframeConstants.BANNER_DRAFT_APPLICATION.BANNER_IMG;
            responseDTO.setLoanAmount(loanAmount);
            responseDTO.setInterestRate(interestRate);
        }
        else if(EligibilityIframeState.BANNER_PENDING_ENACH.equals(responseDTO.getState())){
            title = EligibilityIframeConstants.BANNER_PENDING_ENACH.TITLE;
            title = title.replace("{{loanAmount}}", loanAmountString);
            subtitle = EligibilityIframeConstants.BANNER_PENDING_ENACH.SUB_TITLE;
            subtitle = subtitle.replace("{{interestRate}}", interestRate.toString());
            buttonText = EligibilityIframeConstants.BANNER_PENDING_ENACH.BUTTON_TEXT;
            bannerImg = EligibilityIframeConstants.BANNER_PENDING_ENACH.BANNER_IMG;
            responseDTO.setLoanAmount(loanAmount);
            responseDTO.setInterestRate(interestRate);
        }
        responseDTO.setTitle(title);
        responseDTO.setSubTitle(subtitle);
        responseDTO.setButtonText(buttonText);
        responseDTO.setBannerImg(bannerImg);
        responseDTO.setAlertIcon(EligibilityIframeConstants.ALERT_ICON);
        responseDTO.setAlertText(EligibilityIframeConstants.ALERT_TEXT);
        responseDTO.setDeeplink(EligibilityIframeConstants.DEEPLINK);
    }

    private void cacheIframeDetails(Long merchantId, EligibilityIframeResponseDTO iframeResponseDTO, int ttl){
        try{
            AddCacheDto addCacheDto = new AddCacheDto();
            String key = LendingConstants.ELIGIBILITY_IFRAME_KYC_CACHE_KEYWORD + merchantId;
            addCacheDto.setKey(key);
            addCacheDto.setValue(objectMapper.writeValueAsString(iframeResponseDTO));
            addCacheDto.setTtl(ttl);
            lendingCache.add(addCacheDto, TimeUnit.MINUTES);
            log.info("Iframe call cached with Key : {}", key);
        } catch (Exception e) {
            log.error("exception occured while caching loan details for {} {} {}!!", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public ApiResponse<?> iframeBannerConsumption(Long merchantId, EligibilityIframeConsumptionDTO requestDTO) {
        try {
            if(Objects.nonNull(requestDTO.getIframeBanner()) && Objects.nonNull(requestDTO.getClient())){
                if(eligibilityIframeDebug)return new ApiResponse<>(true, "Skipping event posting in debug mode");
                funnelService.submitEvent(merchantId, null, null,
                        FunnelEnums.StageId.IFRAME_BANNER, FunnelEnums.StageEvent.CLICKED, requestDTO.getIframeBanner(), requestDTO.getClient());
                log.info("Iframe consumed successfully for {}", merchantId);
                return new ApiResponse<>(true, "Successfully posted iframe consumption event");
            }
            else return new ApiResponse<>(false, "Invalid request");
        } catch (Exception e) {
            log.error("Error occurred while posting iframe consumption event for : {} {} {}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong while posting iframe consumption event");
    }

    public ApiResponse<?> underwritingDocsEligibility(Long merchantId, String docType, boolean statusCheck, String event,String source) {
        try {
            log.info("UnderWritingDoc eligibility for merchantId : {}, docType : {} ", merchantId, docType);
            UnderwritingDocEligibilityDTO underwritingDocEligibilityDTO = UnderwritingDocEligibilityDTO.builder()
                    .gst3b(new UnderwritingDocEligibilityDTO.GST3b())
                    .bankStatement(new UnderwritingDocEligibilityDTO.BankStatement())
                    .build();
            underwritingDocEligibilityDTO = checkUnderwritingRiskEligibility(merchantId, underwritingDocEligibilityDTO);
            if(!underwritingDocEligibilityDTO.getBankStatement().getActive() && !underwritingDocEligibilityDTO.getGst3b().getActive()) {
                return new ApiResponse<>(underwritingDocEligibilityDTO);
            }
            underwritingDocEligibilityDTO = checkBankStatementEligibility(merchantId, underwritingDocEligibilityDTO, statusCheck, docType);

            underwritingDocEligibilityDTO = checkGst3bEligibility(merchantId, underwritingDocEligibilityDTO, statusCheck, docType);

            if (!ObjectUtils.isEmpty(source) && gst3bIneligibleSourceList.contains(source)) {
                log.info("GST3b is ineligible for source {} for merchant id{} ",source,merchantId);
                underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.FALSE);
            }

            if(!underwritingDocEligibilityDTO.getBankStatement().getActive() && !underwritingDocEligibilityDTO.getGst3b().getActive()) {
                return new ApiResponse<>(underwritingDocEligibilityDTO);
            }

            BankStatementSessionDetails bankStatementSessionDetails = bankStatementSessionDetailsDao.findFirstByMerchantIdOrderByIdDesc(merchantId);
            if (ObjectUtils.isEmpty(bankStatementSessionDetails)) {
                log.error("No BankStatement session found for given merchantId : {}", merchantId);
            }
            Gst3bSessionDetails gst3bSessionDetails = gst3bSessionDetailsDao.findFirstByMerchantIdOrderByIdDesc(merchantId);
            if (ObjectUtils.isEmpty(gst3bSessionDetails)) {
                log.error("No Gst3b session found for given merchantId : {}", merchantId);
            }

            if(ObjectUtils.isEmpty(bankStatementSessionDetails) && ObjectUtils.isEmpty(gst3bSessionDetails)) {
                return new ApiResponse<>(underwritingDocEligibilityDTO);
            }

            if(!ObjectUtils.isEmpty(event) && !ObjectUtils.isEmpty(bankStatementSessionDetails)) {
                bankStatementService.checkAASessionStatus(bankStatementSessionDetails.getOrderId(), event);
            }

            String orderId = null;
            String latestBsSessionStatus = null;
            String latestGst3bSessionStatus = null;
            Date latestBsSessionTime = null;
            Date latestGst3bSessionTime = null;
            String gst3bRejectReason = null;
            String bsRejectReason = null;
            String bsOrderId = null;
            String gstOrderId = null;
            String bsSessionType = null;
            if (!ObjectUtils.isEmpty(bankStatementSessionDetails)) {
                log.info("Latest bankStatement session for merchantId : {},  {}", merchantId, bankStatementSessionDetails);
                Long minutes = TimeUnit.MINUTES.toMinutes(new Date().getTime() - bankStatementSessionDetails.getCreatedAt().getTime()) / 60000;
                if(bankStatementSessionDetails.getStatus().equals(BankStatementSessionStatus.SUBMITTED)
                        || bankStatementSessionDetails.getStatus().equals(BankStatementSessionStatus.INPROCESS)
                        || bankStatementSessionDetails.getStatus().equals(BankStatementSessionStatus.PENDING)) {
                    if(("BANK_STATEMENT").equalsIgnoreCase(bankStatementSessionDetails.getType()) && minutes > bankStatementSessionTat) {
                        bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                        bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.BEYOND_TAT.name());
                    } else if(("ACCOUNT_AGGREGATOR").equalsIgnoreCase(bankStatementSessionDetails.getType()) && minutes > accountAggregatorSessionTat) {
                        bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                        bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.BEYOND_TAT.name());
                    }
                    bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
                }
                underwritingDocEligibilityDTO.getBankStatement().setStatus(bankStatementSessionDetails.getStatus());
                bsSessionType = "BANK_STATEMENT".equalsIgnoreCase(bankStatementSessionDetails.getType()) ? bankStatementSessionDetails.getType() : "AA";
                if (docType.equalsIgnoreCase("BANK_STATEMENT")) {
                    underwritingDocEligibilityDTO.setActivityStartTime(bankStatementSessionDetails.getUpdatedAt());
                    underwritingDocEligibilityDTO.setActivityStatus(bankStatementSessionDetails.getStatus().name());
                    underwritingDocEligibilityDTO.setActivityFailedError(bankStatementSessionDetails.getRejectReason());
                    underwritingDocEligibilityDTO.setActivityType(bsSessionType);
                    orderId = bankStatementSessionDetails.getOrderId();
                }
                latestBsSessionTime = bankStatementSessionDetails.getUpdatedAt();
                latestBsSessionStatus = bankStatementSessionDetails.getStatus().name();
                bsRejectReason = bankStatementSessionDetails.getRejectReason();
                bsOrderId = bankStatementSessionDetails.getOrderId();
            }
            if (!ObjectUtils.isEmpty(gst3bSessionDetails)) {
                log.info("Latest Gst3b session for merchantId : {},  {}", merchantId, gst3bSessionDetails);
                Long minutes = TimeUnit.MINUTES.toMinutes(new Date().getTime() - gst3bSessionDetails.getCreatedAt().getTime()) / 60000;
                if(minutes > gst3bSessionTat && (gst3bSessionDetails.getStatus().equals(Gst3bSessionStatus.SUBMITTED)
                        || gst3bSessionDetails.getStatus().equals(Gst3bSessionStatus.INPROCESS)
                        || gst3bSessionDetails.getStatus().equals(Gst3bSessionStatus.PENDING)
                        || gst3bSessionDetails.getStatus().equals(Gst3bSessionStatus.OTP_INIT))) {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    gst3bSessionDetails.setRejectReason(Gst3bRejectReason.BEYOND_TAT.name());
                    gst3bSessionDetailsDao.save(gst3bSessionDetails);
                }
                underwritingDocEligibilityDTO.getGst3b().setStatus(gst3bSessionDetails.getStatus());
                if (docType.equalsIgnoreCase("GST3B")) {
                    underwritingDocEligibilityDTO.setActivityStartTime(gst3bSessionDetails.getUpdatedAt());
                    underwritingDocEligibilityDTO.setActivityStatus(gst3bSessionDetails.getStatus().name());
                    underwritingDocEligibilityDTO.setActivityFailedError(gst3bSessionDetails.getRejectReason());
                    underwritingDocEligibilityDTO.setActivityType("GST3B");
                    orderId = gst3bSessionDetails.getOrderId();
                }
                latestGst3bSessionStatus = gst3bSessionDetails.getStatus().name();
                latestGst3bSessionTime = gst3bSessionDetails.getUpdatedAt();
                gst3bRejectReason = gst3bSessionDetails.getRejectReason();
                gstOrderId = gst3bSessionDetails.getOrderId();
            }
            if (docType.equalsIgnoreCase("BOTH")) {
                if (ObjectUtils.isEmpty(latestBsSessionStatus)) {
                    underwritingDocEligibilityDTO.setActivityStatus(latestGst3bSessionStatus);
                    underwritingDocEligibilityDTO.setActivityStartTime(latestGst3bSessionTime);
                    underwritingDocEligibilityDTO.setActivityFailedError(gst3bRejectReason);
                    underwritingDocEligibilityDTO.setActivityType("GST3B");
                    orderId = gstOrderId;
                } else if (ObjectUtils.isEmpty(latestGst3bSessionStatus)) {
                    underwritingDocEligibilityDTO.setActivityStartTime(latestBsSessionTime);
                    underwritingDocEligibilityDTO.setActivityStatus(latestBsSessionStatus);
                    underwritingDocEligibilityDTO.setActivityFailedError(bsRejectReason);
                    underwritingDocEligibilityDTO.setActivityType(bsSessionType);
                    orderId = bsOrderId;
                } else if (!ObjectUtils.isEmpty(latestBsSessionStatus) && !ObjectUtils.isEmpty(latestGst3bSessionStatus)) {
                    if (latestBsSessionTime.compareTo(latestGst3bSessionTime) > 0) {
                        underwritingDocEligibilityDTO.setActivityStartTime(latestBsSessionTime);
                        underwritingDocEligibilityDTO.setActivityStatus(latestBsSessionStatus);
                        underwritingDocEligibilityDTO.setActivityFailedError(bsRejectReason);
                        underwritingDocEligibilityDTO.setActivityType(bsSessionType);
                        orderId = bsOrderId;
                    } else {
                        underwritingDocEligibilityDTO.setActivityStatus(latestGst3bSessionStatus);
                        underwritingDocEligibilityDTO.setActivityStartTime(latestGst3bSessionTime);
                        underwritingDocEligibilityDTO.setActivityFailedError(gst3bRejectReason);
                        underwritingDocEligibilityDTO.setActivityType("GST3B");
                        orderId = gstOrderId;
                    }
                }
            }
            if(!ObjectUtils.isEmpty(bankStatementSessionDetails)) {
                bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
            }
            if(!ObjectUtils.isEmpty(gst3bSessionDetails)) {
                gst3bSessionDetailsDao.save(gst3bSessionDetails);
            }
            if (("INPROCESS").equalsIgnoreCase(underwritingDocEligibilityDTO.getActivityStatus())) {
                underwritingDocEligibilityDTO = underWritingAnalysis(bankStatementSessionDetails, gst3bSessionDetails, underwritingDocEligibilityDTO, docType, merchantId, orderId, bsSessionType);
            }
            return new ApiResponse<>(underwritingDocEligibilityDTO);
        } catch (Exception e) {
            log.error("Exception in getting underwriting docs eligibility for merchantId : {}  ", merchantId, e);
            UnderwritingDocEligibilityDTO underwritingDocEligibilityDTO = UnderwritingDocEligibilityDTO.builder()
                    .gst3b(new UnderwritingDocEligibilityDTO.GST3b())
                    .bankStatement(new UnderwritingDocEligibilityDTO.BankStatement())
                    .build();
            return new ApiResponse<>(underwritingDocEligibilityDTO);
        }
    }

    private UnderwritingDocEligibilityDTO checkUnderwritingRiskEligibility(Long merchantId, UnderwritingDocEligibilityDTO underwritingDocEligibilityDTO) {
        log.info("Checking underwriting risk checks for bankStatement and gst3b eligibility");
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);
        if (!ObjectUtils.isEmpty(lendingRiskVariables)) {
            List<PincodeColor> pincodeColors = Arrays.asList(PincodeColor.DARK_GREEN, PincodeColor.GREEN, PincodeColor.YELLOW, PincodeColor.LIGHT_GREEN);
            List<RiskGroup> riskGroups = Arrays.asList(RiskGroup.R4, RiskGroup.R5);
            if (!ObjectUtils.isEmpty(lendingRiskVariables.getPincodeColor()) && !pincodeColors.contains(lendingRiskVariables.getPincodeColor())) {
                log.info("pincode color not allowed for banking based offer: {} {}", lendingRiskVariables.getRiskColor(), merchantId);
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
                underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.FALSE);
                return underwritingDocEligibilityDTO;
            }
            if (!ObjectUtils.isEmpty(lendingRiskVariables.getRiskSegment())
                    && (RiskSegment.NTB_ETB_1.equals(RiskSegment.valueOf(lendingRiskVariables.getRiskSegment()))
                    || RiskSegment.NTB_ETB_2.equals(RiskSegment.valueOf(lendingRiskVariables.getRiskSegment()))
                    || RiskSegment.NTB_PURE.equals(RiskSegment.valueOf(lendingRiskVariables.getRiskSegment()))
                    || RiskSegment.REGULAR_NTC.equals(RiskSegment.valueOf(lendingRiskVariables.getRiskSegment())))
                    && !loanUtil.isInternalMerchant(merchantId)) {
                log.info("risk segment not allowed for banking based offer: {} {}", lendingRiskVariables.getRiskSegment(), merchantId);
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
                underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.FALSE);
                return underwritingDocEligibilityDTO;
            }
            if (!ObjectUtils.isEmpty(lendingRiskVariables.getRiskSegment()) && !ObjectUtils.isEmpty(lendingRiskVariables.getRiskGroup())
                    && (RiskSegment.REPEAT.equals(RiskSegment.valueOf(lendingRiskVariables.getRiskSegment())))
                    && riskGroups.contains(RiskGroup.valueOf(lendingRiskVariables.getRiskGroup()))
                    && !loanUtil.isInternalMerchant(merchantId)) {
                log.info("risk group not allowed for banking based offer: {} {}", lendingRiskVariables.getRiskGroup(), merchantId);
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
                underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.FALSE);
                return underwritingDocEligibilityDTO;
            }
            if (!ObjectUtils.isEmpty(lendingRiskVariables.getRiskSegment()) && !ObjectUtils.isEmpty(lendingRiskVariables.getRiskGroup())
                    && (RiskSegment.REGULAR_ETC.equals(RiskSegment.valueOf(lendingRiskVariables.getRiskSegment())))
                    && (RiskGroup.R5.equals(RiskGroup.valueOf(lendingRiskVariables.getRiskGroup()))
                    || RiskGroup.R4.equals(RiskGroup.valueOf(lendingRiskVariables.getRiskGroup()))
                    || RiskGroup.R3.equals(RiskGroup.valueOf(lendingRiskVariables.getRiskGroup())))
                    && !loanUtil.isInternalMerchant(merchantId)) {
                log.info("risk group not allowed for banking based offer: {} {}", lendingRiskVariables.getRiskGroup(), merchantId);
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
                underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.FALSE);
                return underwritingDocEligibilityDTO;
            }
            if (!ObjectUtils.isEmpty(lendingRiskVariables.getBbs()) && lendingRiskVariables.getBbs() < 650 && !loanUtil.isInternalMerchant(merchantId)) {
                log.info("bbs score is less for banking based offer: {} {}", lendingRiskVariables.getBbs(), merchantId);
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
                underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.FALSE);
                return underwritingDocEligibilityDTO;
            }
            if (!ObjectUtils.isEmpty(lendingRiskVariables.getDrsScore()) && lendingRiskVariables.getDrsScore() <= 10 && !loanUtil.isInternalMerchant(merchantId)) {
                log.info("drs score is less for banking based offer: {} {}", lendingRiskVariables.getDrsScore(), merchantId);
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
                underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.FALSE);
                return underwritingDocEligibilityDTO;
            }
        }
        String shopType = null;
        List<LendingPaymentSchedule> previousLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(merchantId, false);
        if (!ObjectUtils.isEmpty(previousLoans)) {
            LendingPaymentSchedule previousLoan = previousLoans.get(0);
            LmsFieldValues lmsFieldValues = lmsFieldValuesDao.findByFieldIdAndLendingApplicationId(38L, previousLoan.getApplicationId());
            if (!ObjectUtils.isEmpty(lmsFieldValues)) {
                shopType = lmsFieldValues.getFieldDropdownValue();
                log.info("shop type found for merchant: {} from lms fields for last application: {}", shopType, merchantId);
                if (!"PERMANENT".equalsIgnoreCase(shopType) && !loanUtil.isInternalMerchant(merchantId)) {
                    log.info("shop type is not permanent for banking based offer: {} {}", shopType, merchantId);
                    underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
                    underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.FALSE);
                    return underwritingDocEligibilityDTO;
                }
            }
        }
        underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.TRUE);
        underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.TRUE);
        return underwritingDocEligibilityDTO;
    }

    private UnderwritingDocEligibilityDTO checkBankStatementEligibility(Long merchantId, UnderwritingDocEligibilityDTO underwritingDocEligibilityDTO, boolean statusCheck, String docType) {
        log.info("Checking bankStatement eligibility for merchantId : {}", merchantId);
        Pageable pageable = PageRequest.of(0, bankStatementSessionLimitForDay, Sort.by("Id").descending());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date currentDate = calendar.getTime();
        List<BankStatementSessionDetails> bankStatementFailedSessionList = bankStatementSessionDetailsDao.findAllByMerchantIdAndStatusAndCreatedAtGreaterThanEqual(merchantId, BankStatementSessionStatus.FAILED, currentDate, pageable);
        if (bankStatementFailedSessionList.size() >= bankStatementSessionLimitForDay) {
            log.info("{} failed bankStatement session for the day : {}, {}", bankStatementSessionLimitForDay, currentDate, merchantId);
            underwritingDocEligibilityDTO.getBankStatement().setUploadActive(Boolean.FALSE);
            underwritingDocEligibilityDTO.getBankStatement().setAccountAggregatorActive(Boolean.FALSE);
            underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
            return underwritingDocEligibilityDTO;
        }
        BankStatementSessionDetails bankStatementSessionDetails = bankStatementSessionDetailsDao.findFirstByMerchantIdOrderByIdDesc(merchantId);
        if(!ObjectUtils.isEmpty(bankStatementSessionDetails)) {
            calendar.setTime(bankStatementSessionDetails.getCreatedAt());
            calendar.add(Calendar.MONTH, 1);
            boolean statusFlag = !ObjectUtils.isEmpty(statusCheck) && ("BANK_STATEMENT".equalsIgnoreCase(docType) && statusCheck);
            if (new Date().compareTo(calendar.getTime()) < 0
                    && (BankStatementSessionStatus.SUCCESS.equals(bankStatementSessionDetails.getStatus())
                    || (BankStatementSessionStatus.FAILED.equals(bankStatementSessionDetails.getStatus())
                    && BankStatementRejectReason.OFFER_SAME.name().equals(bankStatementSessionDetails.getRejectReason())))
                    && !statusFlag
                    && !loanUtil.isInternalMerchant(merchantId)) {
                log.info("Offer already evaluated on bankStatements less than 1 month ago for merchantId");
                underwritingDocEligibilityDTO.getBankStatement().setUploadActive(Boolean.FALSE);
                underwritingDocEligibilityDTO.getBankStatement().setAccountAggregatorActive(Boolean.FALSE);
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
                return underwritingDocEligibilityDTO;
            }
        }
        List<String> rejectReasons = Arrays.asList("IFRAME_FAILED", "LOGIN_OTP_SENT_FAILED", "SIGNUP_OTP_SENT_FAILED","USER_LOGIN_FAILED",
                "CONSENT_REJECTED_SUCCESS","NO_LINKED_ACCOUNTS_FOUND_ENDING_WITH", "REJECT_CONSENT_BUTTON_CLICKED", "REJECT_CONSENT_MODAL_BACK_BUTTON_CLICKED"
                , "BEYOND_TAT", "INITIATE_API_FAILED", "GLOBAL_LIMIT_EXCEPTION", "GLOBAL_LIMIT_FAILED", "FAILED_CALLBACK_STATUS");
        bankStatementSessionDetails = bankStatementSessionDetailsDao.findFirstByMerchantIdAndTypeOrderByIdDesc(merchantId, "ACCOUNT_AGGREGATOR");
        if(!ObjectUtils.isEmpty(bankStatementSessionDetails)) {
            calendar.setTime(bankStatementSessionDetails.getCreatedAt());
            calendar.add(Calendar.MONTH, 1);
            boolean statusFlag = !ObjectUtils.isEmpty(statusCheck) && ("BANK_STATEMENT".equalsIgnoreCase(docType) && statusCheck);
            if (new Date().compareTo(calendar.getTime()) < 0
                    && BankStatementSessionStatus.FAILED.equals(bankStatementSessionDetails.getStatus())
                    && !rejectReasons.contains(bankStatementSessionDetails.getRejectReason())
                    && !statusFlag && !loanUtil.isInternalMerchant(merchantId)) {
                log.info("AA session failed less than 1 month ago for merchantId");
                underwritingDocEligibilityDTO.getBankStatement().setUploadActive(Boolean.FALSE);
                underwritingDocEligibilityDTO.getBankStatement().setAccountAggregatorActive(Boolean.FALSE);
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
                return underwritingDocEligibilityDTO;
            }
        }
        underwritingDocEligibilityDTO = checkAccountAggregatorEligibility(merchantId, underwritingDocEligibilityDTO);
        if(!loanUtil.isInternalMerchant(merchantId) && !bankStatementEnabled) {
            underwritingDocEligibilityDTO.getBankStatement().setUploadActive(Boolean.FALSE);
            if(!underwritingDocEligibilityDTO.getBankStatement().getAccountAggregatorActive()) {
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
            } else {
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.TRUE);
            }
            return underwritingDocEligibilityDTO;
        }
        final BankDetailsDto bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId).orElse(null);
        if (!ObjectUtils.isEmpty(bankDetailsDtoOptional)) {
            BankStatementWhitelistedBanks bankStatementWhitelistedBanks = null;
            if (!ObjectUtils.isEmpty(bankDetailsDtoOptional.getBankCode())) {
                bankStatementWhitelistedBanks = bankStatementWhitelistedBanksDao.findFirstByBankCode(bankDetailsDtoOptional.getBankCode());
            }
            //TO DO
            /**
             else if(!ObjectUtils.isEmpty(bankDetailsDtoOptional.get().getIfsc())) {
             bankStatementWhitelistedBanks = bankStatementWhitelistedBanksDao.findFirstByIfsc(bankDetailsDtoOptional.get().getIfsc().replaceAll("\\d", ""));
             }
             **/
            if (ObjectUtils.isEmpty(bankStatementWhitelistedBanks)) {
                log.info("bank is not in bankStatement whitelisted banks : {}, {}", bankStatementWhitelistedBanks, merchantId);
                underwritingDocEligibilityDTO.getBankStatement().setUploadActive(Boolean.FALSE);
                if(!underwritingDocEligibilityDTO.getBankStatement().getAccountAggregatorActive()) {
                    underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
                } else {
                    underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.TRUE);
                }
                return underwritingDocEligibilityDTO;
            }
        } else {
            log.info("bank details are not found for merchantId : {}", merchantId);
            underwritingDocEligibilityDTO.getBankStatement().setUploadActive(Boolean.FALSE);
            if(!underwritingDocEligibilityDTO.getBankStatement().getAccountAggregatorActive()) {
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.FALSE);
            } else {
                underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.TRUE);
            }
            return underwritingDocEligibilityDTO;
        }
        underwritingDocEligibilityDTO.getBankStatement().setUploadActive(Boolean.TRUE);
        underwritingDocEligibilityDTO.getBankStatement().setActive(Boolean.TRUE);
        return underwritingDocEligibilityDTO;
    }

    private UnderwritingDocEligibilityDTO checkGst3bEligibility(Long merchantId, UnderwritingDocEligibilityDTO underwritingDocEligibilityDTO, boolean statusCheck, String docType) {
        log.info("Checking gst3b eligibility for merchantId : {}", merchantId);
        if(!easyLoanUtil.percentScaleUp(merchantId, gst3bRolloutPercent) && !loanUtil.isInternalMerchant(merchantId)) {
            underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.FALSE);
            return underwritingDocEligibilityDTO;
        }
        Pageable pageable = PageRequest.of(0, 2, Sort.by("Id").descending());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date currentDate = calendar.getTime();
        List<Gst3bSessionDetails> gst3bSessionDetailsList = gst3bSessionDetailsDao.findAllByMerchantIdAndCreatedAtGreaterThanEqual(merchantId, currentDate, pageable);
        if(gst3bSessionDetailsList.size() >= 2) {
            if(gst3bSessionDetailsList.get(0).getStatus().equals(Gst3bSessionStatus.FAILED) && gst3bSessionDetailsList.get(1).getStatus().equals(Gst3bSessionStatus.FAILED) && !loanUtil.isInternalMerchant(merchantId)) {
                log.info("Two failed gst3b session for the day : {}, {}", currentDate, merchantId);
                underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.FALSE);
                return underwritingDocEligibilityDTO;
            }
        }
        Gst3bSessionDetails gst3bSessionDetails = gst3bSessionDetailsDao.findFirstByMerchantIdOrderByIdDesc(merchantId);
        if(!ObjectUtils.isEmpty(gst3bSessionDetails)) {
            calendar.setTime(gst3bSessionDetails.getCreatedAt());
            calendar.add(Calendar.MONTH, 1);
            boolean statusFlag = !ObjectUtils.isEmpty(statusCheck) && ("GST3B".equalsIgnoreCase(docType) && statusCheck);
            if (new Date().compareTo(calendar.getTime()) < 0
                    && (Gst3bSessionStatus.SUCCESS.equals(gst3bSessionDetails.getStatus())
                    || (Gst3bSessionStatus.FAILED.equals(gst3bSessionDetails.getStatus())
                    && BankStatementRejectReason.OFFER_SAME.name().equals(gst3bSessionDetails.getRejectReason())))
                    && !statusFlag) {
                log.info("Offer already evaluated on gst3b details less than 1 month ago for merchantId");
                underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.FALSE);
                return underwritingDocEligibilityDTO;
            }
        }
        underwritingDocEligibilityDTO.getGst3b().setActive(Boolean.TRUE);
        return underwritingDocEligibilityDTO;
    }

    private UnderwritingDocEligibilityDTO checkAccountAggregatorEligibility(Long merchantId, UnderwritingDocEligibilityDTO underwritingDocEligibilityDTO) {
        log.info("Checking Account-aggregator eligibility for merchantId : {}", merchantId);
        if (!easyLoanUtil.percentScaleUp(merchantId, accountAggregatorRolloutPercent) && !loanUtil.isInternalMerchant(merchantId)) {
            underwritingDocEligibilityDTO.getBankStatement().setAccountAggregatorActive(Boolean.FALSE);
            return underwritingDocEligibilityDTO;
        }


        final BankDetailsDto bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId).orElse(null);
        if (ObjectUtils.isEmpty(bankDetailsDtoOptional)) {
            log.info("Bank details not found for merchantId : {}", merchantId);
            underwritingDocEligibilityDTO.getBankStatement().setAccountAggregatorActive(Boolean.FALSE);
            return underwritingDocEligibilityDTO;
        }
        String bankAccount = bankDetailsDtoOptional.getBankCode();
        String AABankEnabledKey = "AA_BANK_ENABLED_" + merchantId;
        Boolean isBankEnabledForAA = (Boolean) lendingCache.get(AABankEnabledKey);
        if(ObjectUtils.isEmpty(isBankEnabledForAA)) {
            ApiResponse apiResponse = financeUtilsHandler.getAABankList(bankAccount);
            if(ObjectUtils.isEmpty(apiResponse) || !apiResponse.isSuccess() || ObjectUtils.isEmpty(apiResponse.getData())) {
                isBankEnabledForAA = Boolean.FALSE;
            } else  {
                isBankEnabledForAA = Boolean.TRUE;
            }
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(AABankEnabledKey);
            addCacheDto.setValue(isBankEnabledForAA);
            addCacheDto.setTtl(1);
            lendingCache.add(addCacheDto);
        }
        if(!isBankEnabledForAA) {
            log.info("Linked bank : {} of merchantId : {} is not enabled for AA", bankAccount, merchantId);
            underwritingDocEligibilityDTO.getBankStatement().setAccountAggregatorActive(Boolean.FALSE);
            return underwritingDocEligibilityDTO;
        }

        underwritingDocEligibilityDTO.getBankStatement().setAccountAggregatorActive(Boolean.TRUE);
        return underwritingDocEligibilityDTO;
    }

    private UnderwritingDocEligibilityDTO underWritingAnalysis(BankStatementSessionDetails bankStatementSessionDetails, Gst3bSessionDetails gst3bSessionDetails, UnderwritingDocEligibilityDTO underwritingDocEligibilityDTO, String docType, Long merchantId, String orderId, String bsSessionType) {
        try {
            Double currentLimit = 0D;
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);
            if(!ObjectUtils.isEmpty(lendingRiskVariables)) {
                currentLimit = lendingRiskVariables.getFinalOffer();
            }
            String type = "BANK_STATEMENT".equalsIgnoreCase(docType) ? bsSessionType : "GST";
            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchantId, orderId, type,EligibilityRequestSource.EASY_LOANS);
            if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
                if (globalLimitResponse.getData().getBankAffectedOffer() || globalLimitResponse.getData().getGst3bAffectedOffer()) {
                    if (globalLimitResponse.getData().getGlobalLimit() > currentLimit) {
                        Double eligibleAmount = 0D;
                        log.info("Global limit for merchant:{} is {}", merchantId, globalLimitResponse.getData().getGlobalLimit());
                        eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                        if (eligibleAmount > 0D) {
                            log.info("Eligibility found for merchant:{}", merchantId);
                            recomputeEligibleLoan(globalLimitResponse, null, merchantId, false);
                            evictLoanDetailV2Cache(merchantId);
                        }
                        underwritingDocEligibilityDTO.setActivityStatus(BankStatementSessionStatus.SUCCESS.name());
                        if (docType.equalsIgnoreCase("BANK_STATEMENT")) {
                            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.SUCCESS);
                        } else if (docType.equalsIgnoreCase("GST3B")) {
                            gst3bSessionDetails.setStatus(Gst3bSessionStatus.SUCCESS);
                        }
                    } else {
                        underwritingDocEligibilityDTO.setActivityStatus(BankStatementSessionStatus.FAILED.name());
                        if (docType.equalsIgnoreCase("BANK_STATEMENT")) {
                            bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.OFFER_SAME.name());
                            bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                        } else if (docType.equalsIgnoreCase("GST3B")) {
                            gst3bSessionDetails.setRejectReason(Gst3bRejectReason.OFFER_SAME.name());
                            gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                        }
                    }
                } else {
                    underwritingDocEligibilityDTO.setActivityStatus(BankStatementSessionStatus.FAILED.name());
                    if (docType.equalsIgnoreCase("BANK_STATEMENT")) {
                        bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.OFFER_SAME.name());
                        bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                    } else if (docType.equalsIgnoreCase("GST3B")) {
                        gst3bSessionDetails.setRejectReason(Gst3bRejectReason.OFFER_SAME.name());
                        gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    }
                }
            } else {
                underwritingDocEligibilityDTO.setActivityStatus(BankStatementSessionStatus.FAILED.name());
                if (docType.equalsIgnoreCase("BANK_STATEMENT")) {
                    bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                    bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.GLOBAL_LIMIT_FAILED.name());
                } else if (docType.equalsIgnoreCase("GST3B")) {
                    gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                    gst3bSessionDetails.setRejectReason(Gst3bRejectReason.GLOBAL_LIMIT_FAILED.name());
                }
            }
        } catch (Exception e) {
            log.error("Exception getting global limit for merchantId : {}", merchantId);
            underwritingDocEligibilityDTO.setActivityStatus(BankStatementSessionStatus.FAILED.name());
            if (docType.equalsIgnoreCase("BANK_STATEMENT")) {
                bankStatementSessionDetails.setStatus(BankStatementSessionStatus.FAILED);
                bankStatementSessionDetails.setRejectReason(BankStatementRejectReason.GLOBAL_LIMIT_EXCEPTION.name());
            } else if (docType.equalsIgnoreCase("GST3B")) {
                gst3bSessionDetails.setStatus(Gst3bSessionStatus.FAILED);
                gst3bSessionDetails.setRejectReason(BankStatementRejectReason.GLOBAL_LIMIT_EXCEPTION.name());
            }
        }
        if (!ObjectUtils.isEmpty(bankStatementSessionDetails)) {
            bankStatementSessionDetailsDao.save(bankStatementSessionDetails);
        }
        if (!ObjectUtils.isEmpty(gst3bSessionDetails)) {
            gst3bSessionDetailsDao.save(gst3bSessionDetails);
        }
        return underwritingDocEligibilityDTO;
    }

    private void evictLoanDetailV2Cache( Long merchantId) {
        if(Objects.nonNull(merchantId)) {
            String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchantId;
            log.info("deleting cached key of loan details in create application for merchant: {}",merchantId);
            lendingCache.delete(loanDetailsCacheKey);
        } else {
            log.info("no key exists!");
        }
    }

    private void checkKycForTopup(LoanDetailsResponse loanDetailsResponse, LendingApplication openApplication, BasicDetailsDto merchant, Experian experian){
        log.info("open application for merchant:{}", merchant.getId());
        //with validAfter timestamp
        LendingApplicationKycDetails lendingApplicationKycDetails = null;

        if(easyLoanUtil.percentScaleUp(openApplication.getMerchantId(), lenderAssignmentNewFlowRollOutPercent)){
            Integer dateDiff = LendingEnum.LENDER.ABFL.name().equalsIgnoreCase(openApplication.getLender()) ? 365 : 731;
            lendingApplicationKycDetails=lendingApplicationKycDetailsDao.findSuccessKycDetails(openApplication.getMerchantId(), openApplication.getLender(), dateDiff);
        }

        if(!loanUtil.isRepeatLoan(openApplication.getMerchantId()) ||
                (ObjectUtils.isEmpty(lendingApplicationKycDetails)
                )){
            lendingApplicationKycDetails=lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(openApplication.getId());
        } else if("draft".equalsIgnoreCase(openApplication.getStatus())) {
            loanDetailsResponse.setKycDone(true);
            if(!KycStatus.APPROVED.name().equalsIgnoreCase(openApplication.getCkycStatus())){
                openApplication.setCkycStatus(KycStatus.APPROVED.name());
                openApplication.setCkycDate(new Date());
                lendingApplicationDao.save(openApplication);
            }
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(openApplication.getId());
            if(ObjectUtils.isEmpty(lendingApplicationDetails)){
                lendingApplicationDetails = new LendingApplicationDetails();
                lendingApplicationDetails.setApplicationId(openApplication.getId());
            }
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
    }

    public ApiResponse<BureauConsentDTO.Data> getConsent(BasicDetailsDto merchant, String pancard) {
        Experian experian = experianDao.getByMerchantId(merchant.getId());
        if (Objects.isNull(experian)) {
            log.info("no data found in experian table for: {}", merchant.getId());
            return new ApiResponse<>(Boolean.FALSE, "no experian data found");
        }
        BureauConsentDTO.Data bureauConsentDTO = BureauConsentDTO.Data.builder()
                .pincode(experian.getPincode())
                .pan(experian.getPancardNumber())
                .merchantId(merchant.getId())
                .mobile(merchant.getMobile())
                .consent_expired(Boolean.TRUE)
                .build();
        BureauConsentDTO.Data consentResponse = apiGatewayService.getBureauConsent(bureauConsentDTO);
        if (Objects.nonNull(consentResponse)) {
            if(consentResponse.isConsent_expired()) {
                consentResponse.setPincode(experian.getPincode());
                consentResponse.setPan(experian.getPancardNumber());
            }
            consentResponse.setMerchantId(merchant.getId());
            return new ApiResponse<>(consentResponse);
        }
        return new ApiResponse<>(bureauConsentDTO);
    }

    public ApiResponse<BureauConsentDTO.Data> updateConsent(BasicDetailsDto merchant, String pancard, Integer pinCode,Boolean consent) {
        Experian experian = experianDao.getByMerchantId(merchant.getId());
        if (Objects.isNull(consent)) {
            consent = Boolean.TRUE;
        }
        if (Objects.isNull(experian)) {
            log.info("no data found in experian table for: {}", merchant.getId());
            //return new ApiResponse<>(Boolean.FALSE, "no experian data found");
        }
        if(!ObjectUtils.isEmpty(pinCode) && !ObjectUtils.isEmpty(experian)) {
            log.info("updating pinCode to {} for merchant : {} ", pinCode, merchant.getId());
            experian.setPincode(pinCode);
            experianDao.save(experian);
        }
        BureauConsentDTO.Data bureauConsentDTO = BureauConsentDTO.Data.builder()
                .pincode(pinCode)
                .pan(pancard)
                .merchantId(merchant.getId())
                .mobile(merchant.getMobile())
                .consent_expired(!consent)
                .build();
        BureauConsentDTO.Data consentResponse = apiGatewayService.updateConsent(bureauConsentDTO);
        if (Objects.nonNull(consentResponse)) {
            if (!consentResponse.isConsent_expired()) {
                String loanDetailsCacheKey = LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + bureauConsentDTO.getMerchantId();
                log.info("deleting cached key of loan dashboard api for merchant: {}",bureauConsentDTO.getMerchantId());
                lendingCache.delete(loanDetailsCacheKey);

                AddCacheDto addCacheDto = new AddCacheDto();
                addCacheDto.setKey(LendingConstants.BUREAU_CONSENT_KEY_PREFIX + bureauConsentDTO.getMerchantId());
                addCacheDto.setTtl(1);
                addCacheDto.setValue(true);
                lendingCache.add(addCacheDto);
            }
            return new ApiResponse<>(consentResponse);
        }
        return new ApiResponse<>(Boolean.FALSE, "could not update consent, retry!");
    }

    public ApiResponse<MerchantLoanEligibilityResponseDto> fetchMerchantEligibilityForLoan(Long merchantId) {
        try {
            MerchantLoanEligibilityResponseDto response = getMerchantEligibilityResponseFromCache(merchantId);
            if(nonNull(response)){
                return new ApiResponse<>(response);
            }

            response = new MerchantLoanEligibilityResponseDto();
            LendingPaymentScheduleSlave lendingPaymentScheduleSlave = lendingPaymentScheduleDaoSlave.findLatestLendingPaymentScheduleByMerchantId(merchantId);
            log.info("lendingPaymentSchedule for merchantId : {} is {}", merchantId, lendingPaymentScheduleSlave);

            String status = nonNull(lendingPaymentScheduleSlave) ? lendingPaymentScheduleSlave.getStatus() : null;
            response.setIsActive("ACTIVE".equalsIgnoreCase(status) || "INACTIVE".equalsIgnoreCase(status) ||
                    "INACTIVE_TOPUP".equalsIgnoreCase(status));
            if(response.getIsActive()) {
                setMerchantEligibilityResponseInCache(merchantId,response);
                return new ApiResponse<>(response);
            }

            LendingApplicationSlave lendingApplicationSlave = lendingApplicationDaoSlave.getLatestPendingApplication(merchantId);
            log.info("lendingApplication for merchantId : {} is {}", merchantId, lendingApplicationSlave);

            if(nonNull(lendingApplicationSlave)){
                response.setApplicationStatus(lendingApplicationSlave.getStatus());
                response.setLoanAmount(lendingApplicationSlave.getLoanAmount());
            } else {
                response.setEligibleLimit(fetchMerchantEligibleAmount(merchantId));
            }
            setMerchantEligibilityResponseInCache(merchantId,response);
            return new ApiResponse<>(response);
        } catch(Exception e){
            log.error("unable to find eligibility for merchantId : {} {} {} ", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(Boolean.FALSE, "could not fetch eligibility for merchant, retry!");
    }

    private Double fetchMerchantEligibleAmount(Long merchantId){
        try {
            LendingRiskVariablesSlave lendingRiskVariablesSlave= lendingRiskVariablesDaoSlave.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            log.info("lendingRiskVariables for merchantId : {} is {}", merchantId, lendingRiskVariablesSlave);
            if (nonNull(lendingRiskVariablesSlave) && nonNull(lendingRiskVariablesSlave.getFinalOffer())) {
                return lendingRiskVariablesSlave.getFinalOffer();
            }
            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchantId,EligibilityRequestSource.EASY_LOANS);
            log.info("globalLimitResponse for merchantId : {} is {}", merchantId, globalLimitResponse);
            if(nonNull(globalLimitResponse) && nonNull(globalLimitResponse.getData())){
                return globalLimitResponse.getData().getGlobalLimit();
            }
            throw new RuntimeException("error while fetching global limit response for " + merchantId);
        } catch(Exception e){
            throw new RuntimeException("unable to find eligible amount for merchantId : " + merchantId);
        }
    }

    public MerchantLoanEligibilityResponseDto getMerchantEligibilityResponseFromCache(Long merchantId) throws Exception {
        String key = glEligibilityRedisTokenKey + merchantId.toString();
        Object response = lendingCache.get(key);
        MerchantLoanEligibilityResponseDto merchantLoanEligibilityResponseDto = new ObjectMapper().convertValue(response, MerchantLoanEligibilityResponseDto.class);
        if (nonNull(merchantLoanEligibilityResponseDto)) {
            return merchantLoanEligibilityResponseDto;
        } else {
            log.info("response doesn't exist, generating new");
            return null;
        }
    }

    public void setMerchantEligibilityResponseInCache(Long merchantId, MerchantLoanEligibilityResponseDto response) {
        String key = glEligibilityRedisTokenKey + merchantId.toString();
        AddCacheDto addCacheDto = new AddCacheDto();
        addCacheDto.setKey(key);
        addCacheDto.setValue(response);
        addCacheDto.setTtl(goldLoanMerchantEligibilityTTL);
        lendingCache.add(addCacheDto, TimeUnit.MINUTES);
        log.info("setting response into cache {}", addCacheDto);
    }


    public ApiResponse<?> getMerchantRefVersion(Long merchantId) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findBymerchantId(merchantId);
            if (Objects.isNull(lendingApplication) || Objects.isNull(lendingApplication.getId())) {
                log.info("No applicationId found of merchantId: {}", merchantId);
                return new ApiResponse<>(false, "No applicationId found for given merchantId");
            }

            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                log.info("LRVS details not found of merchantId: {}", merchantId);
                return new ApiResponse<>(false, "LRVS Details not found for given merchantId");
            }

            MerchantRedirectToNewRefResponseDto merchantRedirectToNewRefResponseDto = new MerchantRedirectToNewRefResponseDto();
            merchantRedirectToNewRefResponseDto.setVersion(lendingRiskVariablesSnapshot.getNewContactReferenceLogic() ? "v2" : "v1");
            return new ApiResponse<>(merchantRedirectToNewRefResponseDto);
        }catch (Exception e) {
            log.error("Error while fetching if merchant should redirect to new reference logic or not for merchantId: {} {}", merchantId, Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something went wrong");
    }

    public void saveMerchantPspInMongo(RequestDTO<SyncPspDTO> requestDTO, BasicDetailsDto merchantUser){

        try{
            List<PspDTO> pspDTOList = requestDTO.getPayload().getPspList();
            if(pspDTOList == null || pspDTOList.isEmpty()) {
                log.info("PSP List is Empty for Merchant : {}", merchantUser.getId());
                return;
            }
            List<MerchantPSPMongo> psps = new ArrayList<>();

            MerchantPSPMongo merchantPsps = new MerchantPSPMongo();
            List<MerchantPSPMongo.AppDetails> appDetailList = new ArrayList<>();

            merchantPsps.setMerchantId((merchantUser.getId()));
            for (PspDTO pspDTO : pspDTOList) {
                MerchantPSPMongo.AppDetails appDetails = merchantPsps.new AppDetails();
                appDetails.setAppName(pspDTO.getAppName());
                appDetails.setPackageName(pspDTO.getPackageName());
                appDetailList.add(appDetails);
            }

            merchantPsps.setAppDetails(appDetailList);
            psps.add(merchantPsps);
            mongoPublisherUtil.publish("SendMoney", "merchant_psp_dump", merchantUser.getId().toString(), psps);

        }catch (Exception ex){
            log.error("Exception while saving Merchant PSP in Mongo, Exception is :{}", Arrays.asList(ex.getStackTrace()));

        }
    }

    private boolean isValid(MerchantReference reference, BasicDetailsDto merchant) {
        String name = reference.getName();
        if (StringUtils.isEmpty(name)) {
            log.info("reference name is Empty!");
            return false;
        }
        String strippedName = name.replaceAll(" ", "");
        String merchantName = loanUtil.getBeneficiaryName(merchant.getId());
        // Rule 1: Name cannot be the same as the merchant's name
        if (!StringUtils.isEmpty(merchantName) && name.equalsIgnoreCase(merchant.getName())) {
            log.info("reference name matches with merchant name, {}", name);
            return false;
        }

        // Rule 2: Must have at least 3 consecutive characters
        if (!commonUtil.hasAtLeastThreeConsecutiveChars(name)) {
            log.info("reference name is not having atleast three consecutive chars, {}", name);
            return false;
        }

        // Rule 3: Must not contain any numerical or special characters
        if (!strippedName.matches("[a-zA-Z]+")) {
            log.info("reference name is having special or numeric chars, {}", name);
            return false;
        }

        // Rule 4: Must not be entirely consecutive letters
        if (commonUtil.isAllConsecutiveLetters(strippedName)) {
            log.info("reference name having all consecutive letters, {}", name);
            return false;
        }

        String merchantMobile = merchant.getMobile();
        String referenceMobile = reference.getPhoneNumber();
        if (StringUtils.isEmpty(merchantMobile) || StringUtils.isEmpty(referenceMobile) || referenceMobile.length() < 10) {
            log.info("reference mobile is empty or length is less than 10");
            return false;
        }
        merchantMobile = merchantMobile.length() == 12 ? merchantMobile.substring(2) : merchantMobile;
        referenceMobile = referenceMobile.length() == 12 ? referenceMobile.substring(2) : referenceMobile;

        // Rule 1: Mobile number cannot be the same as the merchant's number
        if (referenceMobile.equals(merchantMobile)) {
            log.info("merchant mobile matches with reference mobile, {}", referenceMobile);
            return false;
        }

        // Rule 2: Consecutive numbers for more than 4 values are not allowed
        if (commonUtil.hasMoreThanFourConsecutiveNumbers(referenceMobile)) {
            log.info("reference mobile having more than 4 consecutive numbers, {}", referenceMobile);
            return false;
        }

        // Rule 3: The same digit repeated more than 4 times is not allowed
        if (commonUtil.hasMoreThanFourSameDigits(referenceMobile)) {
            log.info("reference mobile having same digit more than 4 times, {}", referenceMobile);
            return false;
        }
        return true;
    }

    private boolean hasValidRestrictedRelations(List<MerchantReference> references) {
        Map<ReferenceRelation, Integer> relationCount = new HashMap<>();

        ReferenceRelation relation = null;
        for (MerchantReference reference : references) {
            try {
                relation = ReferenceRelation.valueOf(reference.getInferredRelation());
            } catch (Exception e) {
                log.error("Exception while getting relation enum", e);
                return false;
            }
            if (ObjectUtils.isEmpty(relation)) return false;
            relationCount.put(relation, relationCount.getOrDefault(relation, 0) + 1);
            if ((restrictedRelations.contains(relation) && relationCount.get(relation) > 1) || relationCount.get(relation) > MAX_UNIQUE_RELATION) {
                log.info("Relation {} is associated with threshold references!", relation);
                return false;
            }
        }
        return true;
    }

    public ApiResponse<?> additionalLoanDetails(BasicDetailsDto merchant, Long applicationId) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchant.getId());
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("lending application not found for {}", applicationId);
                return new ApiResponse<>(false, "lending application not found for " + applicationId);
            }

            LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
                log.info("lending application kyc details not found for {}", applicationId);
                return new ApiResponse<>(false, "lending application kyc details not found for " + applicationId);
            }
            AdditionalLoanDetailsDTO additionalLoanDetails = AdditionalLoanDetailsDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .build();
            List<AdditionalLoanDetailsDTO.Input> inputs = new ArrayList<>();
            List<String> requiredInputs = getInputsByLender(lendingApplication.getLender());
            if (requiredInputs.contains("FATHER_NAME") && ObjectUtils.isEmpty(lendingApplicationKycDetails.getFatherName())) {
                inputs.add(AdditionalLoanDetailsDTO.Input.builder()
                        .inputType("FATHER_NAME")
                        .editable(true)
                        .build()
                );
            }
            if (requiredInputs.contains("EMAIL") && ObjectUtils.isEmpty(lendingApplicationKycDetails.getEmail())) {
                inputs.add(AdditionalLoanDetailsDTO.Input.builder()
                        .inputType("EMAIL")
                        .editable(true)
                        .build()
                );
            }
            additionalLoanDetails.setInputs(inputs);
            additionalLoanDetails.setShowModal(!ObjectUtils.isEmpty(inputs));
            log.info("get additional details response {} for lending application {}",additionalLoanDetails, lendingApplication.getId());
            funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(), FunnelEnums.StageId.ADDITIONAL_DETAILS_MODAL,
                    FunnelEnums.StageEvent.INITIATED, inputs.stream().map(AdditionalLoanDetailsDTO.Input::getInputType).collect(Collectors.joining(",")));
            return new ApiResponse<>(additionalLoanDetails);
        } catch (Exception e) {
            log.error("exception in getting additional loan details for applicationId {} {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "something went wrong while getting additional loan details");
    }

    private List<String> getInputsByLender(String lender) {
        switch (lender) {
            case "IIFL":
                return Arrays.asList("FATHER_NAME");
            case "SMFG":
                return Arrays.asList("FATHER_NAME","EMAIL");
        }
        return new ArrayList<>();
    }

    public ApiResponse<?> saveAdditionalLoanDetails(BasicDetailsDto merchant, AdditionalLoanDetailsDTO loanDetails) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(loanDetails.getApplicationId(), merchant.getId());
            log.info("additional details data received {} for lending application {}",loanDetails, loanDetails.getApplicationId());
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("lending application not found for {}", loanDetails.getApplicationId());
                return new ApiResponse<>(AdditionalLoanDetailsResponseDTO.builder().message("We are facing technical issues - Please retry after 5 min").errorCode("APP_NOT_FOUND").detailSaved(false).build());
            }
            LendingApplicationKycDetails lendingApplicationKycDetails = null;
            for (AdditionalLoanDetailsDTO.Input input : loanDetails.getInputs()) {
                switch (input.getInputType()) {
                    case "FATHER_NAME":
                    case "EMAIL":
                        lendingApplicationKycDetails = saveAdditionalKycDetails(lendingApplication.getId(), lendingApplicationKycDetails, input);
                        break;
                }
            }
            if (!ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
                lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
            }
            funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(), FunnelEnums.StageId.ADDITIONAL_DETAILS_MODAL,
                    FunnelEnums.StageEvent.COMPLETED, null);
            log.info("additional details successfully saved for lending application {}", loanDetails.getApplicationId());
            return new ApiResponse<>(AdditionalLoanDetailsResponseDTO.builder().message("Successfully saved additional loan details for applicationId + lendingApplication.getId()").detailSaved(true).build());
        } catch (Exception e) {
            log.error("exception in saving additional loan details for applicationId {} {}", loanDetails.getApplicationId(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(AdditionalLoanDetailsResponseDTO.builder().message("We are facing technical issues - Please retry after 5 min").detailSaved(false).errorCode("DATA_NOT_SAVED").build());
    }

    private LendingApplicationKycDetails saveAdditionalKycDetails(Long applicationId, LendingApplicationKycDetails lendingApplicationKycDetails, AdditionalLoanDetailsDTO.Input input) {
        if (ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
            lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
            if (ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
                log.info("lending application kyc details not found for {}", applicationId);
                throw new RuntimeException("lending application kyc details not found for " + applicationId);
            }
        }
        switch (input.getInputType()) {
            case "FATHER_NAME":
                lendingApplicationKycDetails.setFatherName(ObjectUtils.isEmpty(input.getValue()) ? lendingApplicationKycDetails.getFatherName() : input.getValue());
                break;
            case "EMAIL":
                lendingApplicationKycDetails.setEmail(ObjectUtils.isEmpty(input.getValue()) ? lendingApplicationKycDetails.getEmail() : input.getValue());
                break;
        }
        return lendingApplicationKycDetails;
    }

    public ApiResponse<?> getHomePageCardsDetails(Long merchantId) {
        try {
            HomepageCardsDetailsDTO responseDTO = new HomepageCardsDetailsDTO();
            if(!homepageCardsEnabled){
                responseDTO.setState(HomePageCardsState.CARD_NOT_APPLICABLE);
                return new ApiResponse<>(responseDTO);
            }

            String homepageCardsCacheKey = LendingConstants.HOMEPAGE_CARD_CACHE_KEYWORD + merchantId;
            Object homepageCardsCacheResponse = lendingCache.get(homepageCardsCacheKey);
            if (Objects.nonNull(homepageCardsCacheResponse)) {
                responseDTO = objectMapper.readValue((String) homepageCardsCacheResponse, HomepageCardsDetailsDTO.class);
                log.info("Homepage card details responseDTO from cache for {}, {}", merchantId, responseDTO);
                return new ApiResponse<>(responseDTO);
            }

            MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(merchantId);
            BasicDetailsDto merchant = merchantDetailsDto.getMerchantDetail();
            if (ObjectUtils.isEmpty(merchant)) {
                return new ApiResponse<>(false, "Something went wrong");
            }

            //case:1 & case:2
            LendingRiskVariablesSlave lendingRiskVariablesSlave = lendingRiskVariablesDaoSlave.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if (!ObjectUtils.isEmpty(lendingRiskVariablesSlave)
                    && Objects.nonNull(lendingRiskVariablesSlave.getFinalOffer()) && lendingRiskVariablesSlave.getFinalOffer() > 0
                    && ObjectUtils.isEmpty(lendingRiskVariablesSlave.getExperianRejection())) {
                responseDTO.setState(HomePageCardsState.CARD_LOAN_ELIGIBLE);
                populateHomePageIframeResponseData(responseDTO, lendingRiskVariablesSlave.getFinalOffer(), null, null);
            }else {
                MileStoneEligibilityResponseDto rteEligibilityResponse = mileStoneHelperServicev3.calculateEligibility(merchant, !ObjectUtils.isEmpty(lendingCache.get(RTEConstants.RTE_V3_AMOUNT + merchantId)));
                if(Boolean.TRUE.equals(rteEligibilityResponse.getMilStoneEligibility())) {
                    responseDTO.setState(HomePageCardsState.CARD_RTE_ELIGIBLE);
                    String targetDurationDays = RTEProgramType.SLIDER.name().equals(rteEligibilityResponse.getProgramType()) ? "30" : "60";
                    populateHomePageIframeResponseData(responseDTO, null, targetDurationDays, null);
                } else if (ObjectUtils.isEmpty(eligibleLoanDao.findTopByMerchantId(merchantId,Sort.by(Sort.Order.desc("id"))))) {
                    //case:3
                    responseDTO.setState(HomePageCardsState.CARD_LOAN_ELIGIBILITY_NOT_CHECKED);
                    populateHomePageIframeResponseData(responseDTO, null, null, null);
                } else{
                    responseDTO.setState(HomePageCardsState.NO_CARD);
                    populateHomePageIframeResponseData(responseDTO, null, null, null);
                }
            }

            //case: 4 (same as case1)
            if(!ObjectUtils.isEmpty(lendingRiskVariablesSlave)
                    && Objects.nonNull(lendingRiskVariablesSlave.getRiskSegment()) && lendingRiskVariablesSlave.getRiskSegment().equals("REPEAT")
            ){
                responseDTO.setState(HomePageCardsState.CARD_LOAN_ELIGIBLE);
                populateHomePageIframeResponseData(responseDTO, lendingRiskVariablesSlave.getFinalOffer(), null, null);
            }

            //case:6
            MileStoneSlave inactiveMilestone = mileStoneDaoSlave.findByMerchantIdAndSessionStatus(merchantId, "IN_PROGRESS");
            double mileStoneCompletePercent = getMileStoneCompletePercent(merchantId);
            if (!ObjectUtils.isEmpty(inactiveMilestone)) {
                responseDTO.setState(HomePageCardsState.CARD_RTE_ENROLLED);
                populateHomePageIframeResponseData(responseDTO, mileStoneCompletePercent, null, null);
            }

            //case:7
            LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if(!ObjectUtils.isEmpty(lendingApplication) &&
                    !Objects.equals(lendingApplication.getStatus(), "deleted") &&
                    !Objects.equals(lendingApplication.getStatus(), "rejected") &&
                    (ObjectUtils.isEmpty(lendingApplication.getManualKyc()) || ObjectUtils.isEmpty(lendingApplication.getNachStatus())) &&
                    ObjectUtils.isEmpty(lendingApplication.getAgreementAt()) &&
                    Objects.equals(lendingApplication.getStatus(), "draft") &&
                    Objects.equals(lendingApplication.getAgreement(), 0)){
                responseDTO.setState(HomePageCardsState.CARD_APPLICATION_CREATED_BUT_NOT_COMPLETED);
                populateHomePageIframeResponseData(responseDTO, !ObjectUtils.isEmpty(lendingApplication.getLoanAmount()) ? lendingApplication.getLoanAmount() : null, null, null);
            }

            //case:8
            if(!ObjectUtils.isEmpty(lendingApplication) && !ObjectUtils.isEmpty(lendingApplication.getId())){
                LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
                if (!ObjectUtils.isEmpty(lendingApplicationDetails) &&
                        Objects.equals(lendingApplicationDetails.getStage(), "ASSC_COMPLETED") &&
                        Objects.equals(lendingApplication.getStatus(), "draft") &&
                        Objects.equals(lendingApplication.getAgreement(), 0)) {
                    responseDTO.setState(HomePageCardsState.CARD_AGREEMENT_NOT_SIGNED);
                    populateHomePageIframeResponseData(responseDTO, !ObjectUtils.isEmpty(lendingApplication.getLoanAmount()) ? lendingApplication.getLoanAmount() : null, null, null);
                }
            }

            //case:9
            if(!ObjectUtils.isEmpty(lendingApplication) &&
                    Objects.equals(lendingApplication.getAgreement(),1) &&
                    !Objects.nonNull(lendingApplication.getNachStatus()) &&
                    Objects.equals(lendingApplication.getStatus(),"pending_verification")
            ){
                responseDTO.setState(HomePageCardsState.CARD_AGREEMENT_SIGNED_BUT_NACH_NOT_SET);
                populateHomePageIframeResponseData(responseDTO, !ObjectUtils.isEmpty(lendingApplication.getLoanAmount()) ? lendingApplication.getLoanAmount() : null, null, null);
            }

            //case:10
            if(!ObjectUtils.isEmpty(lendingApplication) &&
                    Objects.nonNull(lendingApplication.getNachStatus()) &&
                    Objects.equals(lendingApplication.getStatus(),"pending_verification")
            ){
                responseDTO.setState(HomePageCardsState.CARD_VERIFICATION_IN_PROGRESS);
                populateHomePageIframeResponseData(responseDTO, !ObjectUtils.isEmpty(lendingApplication.getLoanAmount()) ? lendingApplication.getLoanAmount() : null, null, null);
            }

            //case:11
            if(!ObjectUtils.isEmpty(lendingApplication) &&
                    Objects.equals(lendingApplication.getLmsStage(),"PENDING_RESUBMIT_DOCUMENT") &&
                    Objects.equals(lendingApplication.getStatus(),"pending_verification")
            ){
                responseDTO.setState(HomePageCardsState.CARD_REUPLOAD_DOCUMENTS);
                populateHomePageIframeResponseData(responseDTO, !ObjectUtils.isEmpty(lendingApplication.getLoanAmount()) ? lendingApplication.getLoanAmount() : null, null, null);
            }

            //case:12
            if(!ObjectUtils.isEmpty(lendingApplication) &&
                    Objects.nonNull(lendingApplication.getNbfcSendDate()) &&
                    !Objects.nonNull(lendingApplication.getDisburseTimestamp()) &&
                    Objects.equals(lendingApplication.getStatus(), "approved")
            ){
                responseDTO.setState(HomePageCardsState.CARD_DISBURSAL_PENDING);
                populateHomePageIframeResponseData(responseDTO, !ObjectUtils.isEmpty(lendingApplication.getLoanAmount()) ? lendingApplication.getLoanAmount() : null, null, null);
            }

            //case:13
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if(!ObjectUtils.isEmpty(lendingPaymentSchedule) &&
                    Objects.nonNull(lendingPaymentSchedule.getEdiAmount()) && lendingPaymentSchedule.getEdiAmount() > 0 &&
                    Objects.nonNull(lendingPaymentSchedule.getDueAmount()) && lendingPaymentSchedule.getDueAmount() > 0
            ){
                log.info("calculated DPD for merchant_id:{} in homepage: {}",merchantId, LoanUtil.calculateDPD(lendingPaymentSchedule.getEdiAmount(), lendingPaymentSchedule.getDueAmount()));
                if(LoanUtil.calculateDPD(lendingPaymentSchedule.getEdiAmount(), lendingPaymentSchedule.getDueAmount())>0) {
                    responseDTO.setState(HomePageCardsState.CARD_AMOUNT_OVERDUE_DPD);
                    populateHomePageIframeResponseData(responseDTO, !ObjectUtils.isEmpty(lendingApplication.getLoanAmount()) ? lendingApplication.getLoanAmount() : null, null, lendingPaymentSchedule.getEdiAmount());
                }else {
                    //case:15
                    responseDTO.setState(HomePageCardsState.CARD_EDI_AUTO_DEBITED);
                    populateHomePageIframeResponseData(responseDTO, !ObjectUtils.isEmpty(lendingApplication.getLoanAmount()) ? lendingApplication.getLoanAmount() : null, null, lendingPaymentSchedule.getEdiAmount());
                }
            }

            //case:16
            LendingPaymentScheduleSlave lendingPaymentSchedule1 = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(merchantId, "ACTIVE");
            if(!ObjectUtils.isEmpty(lendingPaymentSchedule1)){
                List<LoanEligibilityDTO> topUpcheck = merchantLoansService.topupLoan(lendingPaymentSchedule1, false);
                if(!ObjectUtils.isEmpty(topUpcheck)){
                    responseDTO.setState(HomePageCardsState.CARD_TOPUP_LOAN_OFFER_AMOUNT);
                    populateHomePageIframeResponseData(responseDTO, !ObjectUtils.isEmpty(lendingApplication.getLoanAmount()) ? lendingApplication.getLoanAmount() : null, null, null);
                }
            }

            log.info("Homepage card details responseDTO for {}, {}", merchantId, responseDTO);
            cacheHomePageBannerDetails(merchantId, responseDTO, homepageCardsCacheTtl);
            return new ApiResponse<>(responseDTO);
        } catch (Exception e) {
            log.error("Error occurred while fetching homepage card details for merchantId: {} {} {}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong while getting homepage card details.");
    }

    private void populateHomePageIframeResponseData(HomepageCardsDetailsDTO responseDTO, Double loanAmount, String targetDurationDays, Double ediAmount){
        String deeplink = HomepageCardsConstants.DEEPLINK.replace("{{deeplink}}", homePageRedirectionDeeplink);
        if(HomePageCardsState.CARD_LOAN_ELIGIBLE.equals(responseDTO.getState())){
            //case:1
            HomepageCardsConstants.cardLoanEligible(responseDTO, loanAmount);
        }else if(HomePageCardsState.CARD_RTE_ELIGIBLE.equals(responseDTO.getState())){
            //case:2
            HomepageCardsConstants.cardRTEEligible(responseDTO, targetDurationDays);
        }else if(HomePageCardsState.NO_CARD.equals(responseDTO.getState())) {
            HomepageCardsConstants.noBanner(responseDTO);
        }else if(HomePageCardsState.CARD_LOAN_ELIGIBILITY_NOT_CHECKED.equals(responseDTO.getState())){
            //case:3
            HomepageCardsConstants.cardLoanEligibilityNotChecked(responseDTO);
        }else if(HomePageCardsState.CARD_RTE_ENROLLED.equals(responseDTO.getState())){
            //case:6
            HomepageCardsConstants.cardRTEEnrolled(responseDTO, loanAmount);
        }else if(HomePageCardsState.CARD_APPLICATION_CREATED_BUT_NOT_COMPLETED.equals(responseDTO.getState())){
            //case:7
            HomepageCardsConstants.cardApplicationCreatedButNotCompleted(responseDTO, loanAmount);
        }else if(HomePageCardsState.CARD_AGREEMENT_NOT_SIGNED.equals(responseDTO.getState())){
            //case:8
            HomepageCardsConstants.cardAgreementNotSigned(responseDTO, loanAmount);
        }else if(HomePageCardsState.CARD_AGREEMENT_SIGNED_BUT_NACH_NOT_SET.equals(responseDTO.getState())){
            //case:9
            HomepageCardsConstants.cardAgreementSignedButNachNotSet(responseDTO, loanAmount);
        }else if(HomePageCardsState.CARD_VERIFICATION_IN_PROGRESS.equals(responseDTO.getState())){
            //case:10
            HomepageCardsConstants.cardVerificationInProgress(responseDTO, loanAmount);
        }else if (HomePageCardsState.CARD_REUPLOAD_DOCUMENTS.equals(responseDTO.getState())) {
            //case:11
            HomepageCardsConstants.cardReuploadDocuments(responseDTO, loanAmount);
        } else if (HomePageCardsState.CARD_DISBURSAL_PENDING.equals(responseDTO.getState())) {
            //case:12
            HomepageCardsConstants.cardDisbursalPending(responseDTO, loanAmount);
        }else if (HomePageCardsState.CARD_AMOUNT_OVERDUE_DPD.equals(responseDTO.getState())) {
            //case:13
            HomepageCardsConstants.cardAmountOverdueDPD(responseDTO, loanAmount, ediAmount);
        }else if (HomePageCardsState.CARD_EDI_AUTO_DEBITED.equals(responseDTO.getState())) {
            //case:15
            HomepageCardsConstants.cardEDIAutoDebited(responseDTO, loanAmount, ediAmount);
        }else if (HomePageCardsState.CARD_TOPUP_LOAN_OFFER_AMOUNT.equals(responseDTO.getState())) {
            //case:16
            HomepageCardsConstants.cardTopUpLoanOfferAmount(responseDTO, loanAmount);
        }
        responseDTO.setDeeplink(deeplink);
    }

    private void cacheHomePageBannerDetails(Long merchantId, HomepageCardsDetailsDTO homepageCardsDetailsDTO, int ttl){
        try{
            AddCacheDto addCacheDto = new AddCacheDto();
            String key = LendingConstants.HOMEPAGE_CARD_CACHE_KEYWORD + merchantId;
            addCacheDto.setKey(key);
            addCacheDto.setValue(objectMapper.writeValueAsString(homepageCardsDetailsDTO));
            addCacheDto.setTtl(ttl);
            lendingCache.add(addCacheDto, TimeUnit.MINUTES);
            log.info("Homepage banner details cached with Key : {}", key);
        } catch (Exception e) {
            log.error("exception occured while caching loan details for {} {} {}!!", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    private double getMileStoneCompletePercent(Long merchantId){
        BasicDetailsDto merchant = new BasicDetailsDto();
        merchant.setId(merchantId);
        try {
            ApiResponse<MileStoneDashboardDetails> response = mileStoneProgramService.dashboardDetails(merchant);
            if (response.isSuccess()) {
                MileStoneDashboardDetails details = response.getData();
                if (details != null && details.getMapList() != null) {
                    double minCompletionPercent = 0.0;
                    int minAchiveActiveDays = Integer.MAX_VALUE;
                    int minAchiveUniquePayer = Integer.MAX_VALUE;
                    for (MileStoneDashboardData milestoneData : details.getMapList()) {
                        if (milestoneData.getAchieveMileStoneActiveDays() < minAchiveActiveDays) {
                            minAchiveActiveDays = milestoneData.getAchieveMileStoneActiveDays();
                        }

                        if (milestoneData.getAchieveMileStoneUniquePayer() < minAchiveUniquePayer) {
                            minAchiveUniquePayer = milestoneData.getAchieveMileStoneUniquePayer();
                        }

                        // Calculate completion percentage for Active Days
                        double activeDaysPercent = milestoneData.getTargetActiveDays() > 0 && minAchiveActiveDays != Integer.MAX_VALUE
                                ? (minAchiveActiveDays / (double) milestoneData.getTargetActiveDays()) * 100
                                : 0.0;
                        log.info("Active Days Completion %: {}", activeDaysPercent);

                        // Calculate completion percentage for Unique Payers
                        double uniquePayerPercent = milestoneData.getTargetUniquePayer() > 0 && minAchiveUniquePayer != Integer.MAX_VALUE
                                ? (minAchiveUniquePayer / (double) milestoneData.getTargetUniquePayer()) * 100
                                : 0.0;
                        log.info("Unique Payer Completion %: {}", uniquePayerPercent);

                        minCompletionPercent = Math.min(activeDaysPercent, uniquePayerPercent);
                        log.info("Milestone Least Completion %: {}", minCompletionPercent);
                    }
                    return minCompletionPercent;
                }else {
                    log.info("No milestone data available.");
                }
            }else {
                log.error("Error milestone response: {}", response.getMessage());
            }
        } catch (Exception e) {
            log.error("Exception occurred while calculating milestone completion percent for mid {} {} {}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return 0.0;
    }


}
