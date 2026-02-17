package com.bharatpe.lending.loanV3.revamp.services;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingDisbursalStageDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.annotations.LogExecutionTime;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.entity.LendingEligibleLoan;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.query.dao.LendingApplicationDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingApplicationLenderDetailsDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingRiskVariablesDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationLenderDetailsSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.query.entity.LendingRiskVariablesSlave;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.MileStoneDao;
import com.bharatpe.lending.dto.DSMileStoneResponse;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.entity.MileStoneEntity;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.loanV2.service.ExcessNachService;
import com.bharatpe.lending.loanV2.service.InsuranceService;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.EmiDashboardResponse;
import com.bharatpe.lending.loanV3.revamp.dto.LoanApplicationDetailsV3;
import com.bharatpe.lending.loanV3.revamp.dto.LoanDashboardResponse;
import com.bharatpe.lending.loanV3.revamp.dto.LoanDetailResponseDto;
import com.bharatpe.lending.loanV3.revamp.dto.RejectionStateDto;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.loanV3.revamp.enums.PreApprovedLoanEnums;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.businessLoan.EmiDashboardService;
import com.bharatpe.lending.loanV3.revamp.util.DateUtils;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.IEdiModelAssignment;
import com.bharatpe.lending.service.MileStoneHelperService;
import com.bharatpe.lending.loanV3.utils.EmiUtils;
import com.bharatpe.lending.service.helper.MandateRegistrationHelper;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant.BP_CLUB_MEMBERSHIP_KEY_PREFIX;

@Service
@Slf4j
public class LoanDashboardService {


    @Value("${loan.details.refresh.window:15}")
    int loanDetailsRefreshWindow;

    @Value("${loan.version.api.refresh.window:60}")
    int loanVersionApiRefreshWindow;


    @Autowired
    MileStoneHelperService mileStoneHelperService;
    @Lazy
    @Autowired
    private LoanUtil loanUtil;

    @Autowired
    private EasyLoanUtil easyLoanUtil;

    @Autowired
    private LendingMerchantDetailsDao lendingMerchantDetailsDao;

    @Autowired
    private LendingCache lendingCache;

    @Autowired
    private APIGatewayService apiGatewayService;

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    private LendingApplicationDaoSlave lendingApplicationDaoSlave;

    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    private LendingResubmitTaskDao lendingResubmitTaskDao;

    @Autowired
    private LendingResubmitReasonCountDao lendingResubmitReasonCountDao;

    @Autowired
    private LendingDisbursalStageDao lendingDisbursalStageDao;

    @Autowired
    private LendingGstDao lendingGstDao;

    @Autowired
    private LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
     private KycHandler kycHandler;

    @Autowired
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    private LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Autowired
    private ExperianDao experianDao;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantSummaryHandler merchantSummaryHandler;

    @Autowired
    private LendingEligibleLoanDao eligibleLoanDao;

    @Autowired
    private LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Lazy
    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Value("${eligibility.refresh.window:1}")
    int eligibilityRefreshWindow;

    @Value("${new.eligibility.refresh.window:1}")
    int newEligibilityRefreshWindow;

    @Value("${new.eligibility.refresh.window.rollout.percent:0}")
    Integer newEligibilityRefreshWindowRolloutPercent;

    @Autowired
    private DateTimeUtil dateTimeUtil;

    @Value("${club.eligible.loan.cache:true}")
    Boolean clubEligibleLoanCache;

    @Value("${abfl.rollout.percent:10}")
    Integer rolloutAbflPercent;

    @Value("${edi.assignment.model:false}")
    Boolean assignEdiModelFromModelAssignmentEngine;

    @Value("${screen.redesign.rollout.percent:0}")
    Integer screenRedesignRolloutPercent;

    @Value("${screen.redesign.one.percent.rollout.date:}")
    String screenRedesignOnePercentRolloutDate;

    @Value("${screen.redesign.five.percent.rollout.date:}")
    String screenRedesignFivePercentRolloutDate;

    @Value("${screen.redesign.ten.percent.rollout.date:}")
    String screenRedesignTenPercentRolloutDate;

    @Value("${screen.redesign.twenty.percent.rollout.date:}")
    String screenRedesignTwentyPercentRolloutDate;

    @Value("${screen.redesign.fifty.percent.rollout.date:}")
    String screenRedesignFiftyPercentRolloutDate;

    @Value("${screen.redesign.hundred.percent.rollout.date:}")
    String screenRedesignHundredPercentRolloutDate;

    @Value("${enable.diwali.banner:false}")
    boolean enableDiwaliBanner;

    @Value("${diwali.banner.one.rollout.date:}")
    String diwaliBannerOneRolloutDate;

    @Value("${diwali.banner.two.rollout.date:}")
    String diwaliBannerTwoRolloutDate;

    @Value("${diwali.banner.one.end.date:}")
    String diwaliBannerOneEndDate;

    @Value("${diwali.banner.two.end.date:}")
    String diwaliBannerTwoEndDate;

    @Value("${loan.dashboard.rollout:0}")
    Integer loanDashboardSyncRollout;

    @Autowired
    IEdiModelAssignment iEdiModelAssignment;

    @Autowired
    MerchantService merchantService;

    @Autowired
    private ExcessNachService excessNachService;

    @Autowired
    private FunnelService funnelService;

    @Autowired
    LendingRiskVariablesDaoSlave lendingRiskVariablesDaoSlave;

    @Autowired
    LendingPincodesDao lendingPincodesDao;

    @Autowired
    DsHandler dsHandler;

    @Autowired
    MileStoneDao mileStoneDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationLenderDetailsDaoSlave lendingApplicationLenderDetailsDaoSlave;

    @Autowired
    private EmiUtils emiUtils;

    @Autowired
    private EmiDashboardService emiDashboardService;

    @Autowired
    InsuranceService insuranceService;

    @Autowired
    private LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    private MerchantMetadataDao merchantMetadataDao;

    @Autowired
    private MandateRegistrationHelper mandateHelper;

    private final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    @Value("${emi.default.loan.amount:1500000}")
    private double emiDefaultLoanAmount;
    /*
    This method gives the api version to frontend,so that FE can decide which flow to trigger for loan application corresponding to merchant
    currently we are deciding this feature on the basis of internal/external merchant only
     */
    public LoanDashboardApiVersion getLoanDashboardApiVersion(Long merchantId) {
        LoanDashboardApiVersion loanDashboardApiVersion = new LoanDashboardApiVersion();
        if(easyLoanUtil.isDummyMerchant(merchantId)){
            loanDashboardApiVersion.setApiVersion("v1");
            return loanDashboardApiVersion;
        }
        loanDashboardApiVersion.setApiVersion("v2");
        return loanDashboardApiVersion;
    }

    public LoanDashboardApiVersion getLoanDashboardApiVersion(Long merchantId, LendingApplication lendingApplication) {
        log.info("Getting loan dashboard api version details for merchantId:{}", merchantId);
        LoanDashboardApiVersion loanDashboardApiVersion = new LoanDashboardApiVersion();
        try{
            // hardcoding value for some testing
            if(merchantId==9987300){
                loanDashboardApiVersion.setApiVersion("v1");
            }
            else if (loanUtil.isInternalMerchant(merchantId)){
                loanDashboardApiVersion.setApiVersion("v2");
            }
            else if(percentScaleUp(merchantId, screenRedesignRolloutPercent)){
                if(!ObjectUtils.isEmpty(lendingApplication) && ("draft".equalsIgnoreCase(lendingApplication.getStatus()) ||
                        "pending_verification".equalsIgnoreCase(lendingApplication.getStatus()))
                ){
                    Date thresholdDate = getThresholdDate(merchantId);
                    if(lendingApplication.getCreatedAt().after(thresholdDate))loanDashboardApiVersion.setApiVersion("v2");
                    else loanDashboardApiVersion.setApiVersion("v1");
                }
                else loanDashboardApiVersion.setApiVersion("v2");
            }
            else
                loanDashboardApiVersion.setApiVersion("v1");
            log.info("Returning loan dashboard api version detail for merchantId:{}, details:{}", merchantId, loanDashboardApiVersion);
            return loanDashboardApiVersion;
        }
        catch(Exception e){
            log.error("Exception in fetching version for merchant:{}, {}, {}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            loanDashboardApiVersion.setApiVersion("v1");
            cacheVersionDetails(loanDashboardApiVersion, merchantId);
            return loanDashboardApiVersion;
        }
    }

    public ApiResponse<LoanDetailResponseDto> getLastLoanDetails(BasicDetailsDto merchant){
        LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        if(lendingApplication==null){
            return new ApiResponse<>(null, "404", "no application found");
        }
        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(lendingApplication.getId());
        LoanDetailResponseDto.LoanDetailResponseDtoBuilder responseBuilder = LoanDetailResponseDto.builder();
        responseBuilder.applicationId(lendingApplication.getId());
        if(lendingPaymentSchedule!=null){
            responseBuilder.activeLoan(LoanStatus.ACTIVE.name().equalsIgnoreCase(lendingPaymentSchedule.getStatus()));
        }
        responseBuilder.status(lendingApplication.getStatus());
        return new ApiResponse<>(responseBuilder.build());
    }

    @LogExecutionTime
    public LoanDashboardResponse getLoanDashboardDetailsV2(BasicDetailsDto merchantDetails, String token, boolean isIOS,Integer appVersion) {
        log.info("Fetching loan dashboard details with new flow for merchantId: {} ", merchantDetails.getId());
        String loanDetailsCacheKey = LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + merchantDetails.getId();
        LoanDashboardResponse cachedResponse = getCachedLoanDetails(loanDetailsCacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        LoanDashboardResponse loanDashboardResponse = initializeLoanDashboardResponse(merchantDetails);

        CompletableFuture<EmiDashboardResponse> emiDataCompletableFuture = fetchEmiDashboardData(merchantDetails, token);

        List<LendingPaymentScheduleSlave> paymentSchedules = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatuses(
                merchantDetails.getId(), Arrays.asList(LoanStatus.INACTIVE.name(), LoanStatus.CLOSED.name(), LoanStatus.ACTIVE.name(), LoanStatus.DECEASED.name())
        );

        if (handleInactiveLoan(paymentSchedules, loanDashboardResponse, emiDataCompletableFuture)) {
            return loanDashboardResponse;
        }

        loanDashboardResponse.setRepeatLoan(hasClosedLoan(paymentSchedules));

        Optional<LendingPaymentScheduleSlave> activeLoan = getActiveLoan(paymentSchedules);
        if (activeLoan.isPresent()) {
            handleActiveLoan(activeLoan.get(), merchantDetails, loanDashboardResponse, emiDataCompletableFuture, isIOS, appVersion);
            return loanDashboardResponse;
        }

        LendingApplicationSlave openApplication = fetchOpenApplication(merchantDetails, paymentSchedules);
        if (openApplication != null && handleOpenApplication(merchantDetails, loanDashboardResponse, openApplication, emiDataCompletableFuture)) {
            return loanDashboardResponse;
        }

        EmiDashboardResponse emiDashboardData = emiDashboardService.getData(emiDataCompletableFuture);
        if (emiUtils.isActive(emiDashboardData)) {
            return handleEmiLoanDashboard(merchantDetails, emiDashboardData.getResult());
        }

        checksForRTE(merchantDetails, loanDashboardResponse);
        if(loanDashboardResponse.isShowRTEPage()) {
            return loanDashboardResponse;
        }

        checkEligibility(loanDashboardResponse, merchantDetails);
        handleEligibility(merchantDetails, loanDashboardResponse, emiDashboardData);

        cacheLoanDetailsData(loanDashboardResponse);
        log.info("Returning loan dashboard response on new version for merchantId: {}", merchantDetails.getId());
        return loanDashboardResponse;
    }

    private void checksForRTE(BasicDetailsDto merchantDetails, LoanDashboardResponse response) {
        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchantDetails.getId());
        if (ObjectUtils.isEmpty(entity)) {
            return;
        }

        DSMileStoneResponse dsMileStoneResponse = mileStoneHelperService.fetchTarget(entity);
        if (ObjectUtils.isEmpty(dsMileStoneResponse)) {
            log.info("Empty targets for merchant: {}", merchantDetails.getId());
            return;
        }

        if (isEligibleForRTE(dsMileStoneResponse, RTEProgramType.CASHBACK.name(), Boolean.TRUE.equals(entity.getShowSummaryPage()))) {
            log.info("returning rte cashback flow from loan dashboard page for {}", merchantDetails.getId());
            response.setShowRTEPage(true);
            return;
        }

        if (isEligibleForRTE(dsMileStoneResponse, RTEProgramType.SLIDER.name(), RTESessionStatus.IN_PROGRESS.name().equalsIgnoreCase(entity.getSessionStatus()))) {
            log.info("merchant is enrolled in slider program: {}", merchantDetails.getId());
            response.setSliderEnrolled(true);
            return;
        }

        if (RTESessionStatus.IN_PROGRESS.name().equalsIgnoreCase(entity.getSessionStatus())) {
            log.info("rte session in progress: {}", merchantDetails.getId());
            response.setRteEnrolled(true);
        }
    }

    private boolean isEligibleForRTE(DSMileStoneResponse dsMileStoneResponse, String programType, boolean sessionInProgress) {
        return programType.equals(dsMileStoneResponse.getProgram_type()) && sessionInProgress;
    }

    private LoanDashboardResponse getCachedLoanDetails(String cacheKey) {
        try {
            Object cacheResponse = lendingCache.get(cacheKey);
            if (!ObjectUtils.isEmpty(cacheResponse)) {
                log.info("Returning loan details response from cache for {}", cacheKey);
                LoanDashboardResponse loanDashboardResponse = objectMapper.readValue((String) cacheResponse, LoanDashboardResponse.class);
                loanDashboardResponse.setSource("CACHE");
                return loanDashboardResponse;
            }
        } catch (IOException e) {
            log.error("IOException occurred while reading cache for key: {}, error: {}", cacheKey, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected exception occurred while fetching cache for key: {}, error: {}", cacheKey, e.getMessage(), e);
        }
        return null;
    }

    private LoanDashboardResponse initializeLoanDashboardResponse(BasicDetailsDto merchantDetails) {
        LoanDashboardResponse response = new LoanDashboardResponse();
        response.setMerchantId(merchantDetails.getId());
        response.setDummyMerchant(easyLoanUtil.isDummyMerchant(merchantDetails.getId()));
        return response;
    }

    private boolean isRTEEligible(BasicDetailsDto merchantDetails, LoanDashboardResponse response) {
        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchantDetails.getId());
        if (isEligibleForRTE(entity)) {
            log.info("returning rte cashback flow from loan dashboard page for {}", merchantDetails.getId());
            response.setShowRTEPage(true);
            return true;
        }
        return false;
    }

    private CompletableFuture<EmiDashboardResponse> fetchEmiDashboardData(BasicDetailsDto merchantDetails, String token) {
        if (emiUtils.isEmiFlowEnabled()) {
            return emiDashboardService.getEmiDashboardResponse(merchantDetails.getId(), token);
        }
        return CompletableFuture.completedFuture(null);
    }

    private Optional<LendingPaymentScheduleSlave> getActiveLoan(List<LendingPaymentScheduleSlave> paymentSchedules) {
        return paymentSchedules.stream()
                .filter(schedule -> Arrays.asList(LoanStatus.ACTIVE.name(), LoanStatus.DECEASED.name()).contains(schedule.getStatus()))
                .findFirst();
    }

    private void handleActiveLoan(LendingPaymentScheduleSlave activeLoan, BasicDetailsDto merchantDetails, LoanDashboardResponse response,
                                  CompletableFuture<EmiDashboardResponse> emiDataFuture, boolean isIOS,Integer appVersion) {
        log.info("Active loan found for merchantId: {}", merchantDetails.getId());
        response.setInsuranceEligibility(insuranceService.checkInsuranceEligibility(activeLoan));
        funnelService.submitEvent(merchantDetails.getId(), null, null, FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.ACTIVE_LOAN, LocalDateTime.now().toString());
        setBankAccountDetails(merchantDetails.getId(), response);
        handleTopUpApplication(merchantDetails, response);
        response.setActiveLoan(true);
        excessNachService.setExcessCollectionDetails(merchantDetails.getId(), response);
        response.setAutopayRequiredForActiveLoan(mandateHelper.isAutopayRequiredForActiveApplication(activeLoan));
        response.setNachMandateRequiredForActiveLoan(mandateHelper.isDigioUpiAutopayRequiredForActiveApplication(activeLoan, isIOS, appVersion));
        response.setApplicationId(activeLoan.getApplicationId());
        cacheLoanDetailsData(response);
        emiDashboardService.skipData(emiDataFuture);
    }

    private boolean handleInactiveLoan(List<LendingPaymentScheduleSlave> paymentSchedules, LoanDashboardResponse response, CompletableFuture<EmiDashboardResponse> emiDataFuture) {
        Optional<LendingPaymentScheduleSlave> inactiveLoan = paymentSchedules.stream()
                .filter(schedule -> LoanStatus.INACTIVE.name().equalsIgnoreCase(schedule.getStatus()) && !schedule.getCreditLoan())
                .findFirst();
        if (inactiveLoan.isPresent()) {
            response.setIneligible(RejectionReason.LOW_TRANSACTION.getReason());
            response.setKycStatus(KycStatus.APPROVED);
            emiDashboardService.skipData(emiDataFuture);
            return true;
        }
        return false;
    }

    private boolean hasClosedLoan(List<LendingPaymentScheduleSlave> paymentSchedules) {
        return paymentSchedules.stream()
                .anyMatch(schedule -> "CLOSED".equalsIgnoreCase(schedule.getStatus()));
    }

    private void handleTopUpApplication(BasicDetailsDto merchantDetails, LoanDashboardResponse response) {
        LendingApplicationSlave topupApplication = lendingApplicationDaoSlave.findOpenTopUpApplication(merchantDetails.getId(), "TOPUP");
        if (Objects.nonNull(topupApplication)) {
            LoanApplicationDetailsV3 topUpApplicationDetails = setApplicationDetails(topupApplication, merchantDetails);
            response.setTopupLoanApplication(topUpApplicationDetails);
            response.getTopupLoanApplication().setAnnualRoi(getAnnualROI(topupApplication));
        }
    }

    private LendingApplicationSlave fetchOpenApplication(BasicDetailsDto merchantDetails, List<LendingPaymentScheduleSlave> paymentSchedules) {
        Optional<LendingPaymentScheduleSlave> closedLoan = paymentSchedules.stream()
                .filter(schedule -> LoanStatus.CLOSED.name().equalsIgnoreCase(schedule.getStatus()))
                .findFirst();
        if (closedLoan.isPresent()) {
            return lendingApplicationDaoSlave.findTopByMerchantIdAndLoanDisbursalStatusNullAndPaymentScheduleStatusClosedOrderByIdDesc(
                    merchantDetails.getId(), closedLoan.get().getCreatedAt()
            );
        }
        return lendingApplicationDaoSlave.findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(merchantDetails.getId());
    }

    private boolean handleOpenApplication(BasicDetailsDto merchantDetails, LoanDashboardResponse response, LendingApplicationSlave openApplication, CompletableFuture<EmiDashboardResponse> emiDataCompletableFuture) {
        LoanApplicationDetailsV3 loanApplicationDetails = setApplicationDetails(openApplication, merchantDetails);
        if (!ObjectUtils.isEmpty(loanApplicationDetails)) {
            response.setLoanApplication(loanApplicationDetails);
            response.getLoanApplication().setAnnualRoi(getAnnualROI(openApplication));
        }
        if (response.getLoanApplication() != null && StringUtils.isEmpty(response.getLoanApplication().getReapply())) {
            cacheLoanDetailsData(response);
            emiDashboardService.skipData(emiDataCompletableFuture);
            return true;
        }
        return false;
    }

    private void handleEligibility(BasicDetailsDto merchantDetails, LoanDashboardResponse response, EmiDashboardResponse emiDashboardData) {
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantDetails.getId());
        if (emiUtils.isEligible(emiDashboardData, lendingRiskVariables) && response.getEligibility() != null) {
            log.info("eligible for loan merchant:{}", merchantDetails.getId());
            response.getEligibility().setEmiLoanAmount(emiDefaultLoanAmount);
        }
        if (Objects.nonNull(response.getIneligible())) {
            response.setLoanApplication(null);
            funnelService.submitEvent(merchantDetails.getId(), null, null, FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.INELIGIBLE, LocalDateTime.now().toString());
        } else if (Objects.isNull(response.getEligibility())) {
            funnelService.submitEvent(merchantDetails.getId(), null, null, FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.TEN_LAKH_LOAN_PAGE, LocalDateTime.now().toString());
        }
    }

    public LoanDashboardResponse fetchLoanDashboardDetailsResponse(BasicDetailsDto merchantDetails, String token, boolean isIOS,Integer appVersion) throws IOException {
        LoanDashboardResponse loanDashboardResponse = easyLoanUtil.percentScaleUp(merchantDetails.getId(), loanDashboardSyncRollout)
                ? getLoanDashboardDetailsV2(merchantDetails, token, isIOS, appVersion)
                : getLoanDashboardDetails(merchantDetails, token);
        log.info("loan dashboard response for merchantId: {} is {}", merchantDetails.getId(), loanDashboardResponse);
        return loanDashboardResponse;
    }


    public LoanDashboardResponse getLoanDashboardDetails(BasicDetailsDto merchantDetails, String token) throws IOException {
        // in previous  version we  are using cache first build the data.
        String loanDetailsCacheKey = LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + merchantDetails.getId();
        Object loanDetailsCacheResponse = lendingCache.get(loanDetailsCacheKey);
        if (!ObjectUtils.isEmpty(loanDetailsCacheResponse)
        ) {
            log.info("returning loan details response from cache for {}", merchantDetails.getId());
            LoanDashboardResponse loanDashboardResponse = objectMapper.readValue((String) loanDetailsCacheResponse, LoanDashboardResponse.class);
            loanDashboardResponse.setSource("CACHE");
            return loanDashboardResponse;
        }
        LoanDashboardResponse loanDashboardResponse = new LoanDashboardResponse();
        loanDashboardResponse.setMerchantId(merchantDetails.getId());
        //set dummy merchant
        loanDashboardResponse.setDummyMerchant(easyLoanUtil.isDummyMerchant(merchantDetails.getId()));

        MileStoneEntity entity = mileStoneDao.findTop1ByMerchantIdOrderByIdDesc(merchantDetails.getId());
        if(isEligibleForRTE(entity)) {
            //add one condition on pageviewed as false
            log.info("returning rte cashback flow from loan dashboard page for {}", merchantDetails.getId());
            loanDashboardResponse.setShowRTEPage(true);
            return loanDashboardResponse;
        }
        CompletableFuture<EmiDashboardResponse> emiDataCompletableFuture = CompletableFuture.completedFuture(null);
        if(emiUtils.isEmiFlowEnabled()){
            emiDataCompletableFuture = emiDashboardService.getEmiDashboardResponse(merchantDetails.getId(), token);
        }

        //if user has inactive loan, return
        LendingPaymentScheduleSlave lendingPaymentSchedule1 = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(merchantDetails.getId(), Collections.singletonList("INACTIVE"));
        if (!ObjectUtils.isEmpty(lendingPaymentSchedule1) && "INACTIVE".equalsIgnoreCase(lendingPaymentSchedule1.getStatus()) &&
            !lendingPaymentSchedule1.getCreditLoan()
        ) {
            loanDashboardResponse.setIneligible(RejectionReason.LOW_TRANSACTION.getReason());
            loanDashboardResponse.setKycStatus(KycStatus.APPROVED);
            emiDashboardService.skipData(emiDataCompletableFuture);
            return loanDashboardResponse;
        }

        Optional<LendingPaymentScheduleSlave> lendingPaymentSchedule = lendingPaymentScheduleDaoSlave.findLatestClosedLoan(merchantDetails.getId());
        // to check if user have repeat loan
        loanDashboardResponse.setRepeatLoan(lendingPaymentSchedule.isPresent());

        if (hasActiveLoan(merchantDetails.getId(), loanDashboardResponse)) {
            log.info("active loan merchant:{}", merchantDetails.getId());
            funnelService.submitEvent(merchantDetails.getId(), null, null,
                    FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.ACTIVE_LOAN, LocalDateTime.now().toString());
            // set bank details and benificiaryName and account details (in single api get all the information)
            setBankAccountDetails(merchantDetails.getId(),loanDashboardResponse);
            LendingApplicationSlave topupApplication = lendingApplicationDaoSlave.findOpenTopUpApplication(merchantDetails.getId(), "TOPUP");
            if(Objects.nonNull(topupApplication)){
                LoanApplicationDetailsV3 topUpApplicationDetails = setApplicationDetails(topupApplication,merchantDetails);
                loanDashboardResponse.setTopupLoanApplication(topUpApplicationDetails);
                loanDashboardResponse.getTopupLoanApplication().setAnnualRoi(getAnnualROI(topupApplication));
            }
            loanDashboardResponse.setActiveLoan(true);
            excessNachService.setExcessCollectionDetails(merchantDetails.getId(), loanDashboardResponse);
            cacheLoanDetailsData(loanDashboardResponse);
            emiDashboardService.skipData(emiDataCompletableFuture);
            return loanDashboardResponse;
        }
        LendingApplicationSlave openApplication;
        if (!ObjectUtils.isEmpty(lendingPaymentSchedule) && "CLOSED".equalsIgnoreCase(lendingPaymentSchedule.get().getStatus())) {
            openApplication = lendingApplicationDaoSlave.findTopByMerchantIdAndLoanDisbursalStatusNullAndPaymentScheduleStatusClosedOrderByIdDesc(merchantDetails.getId(), lendingPaymentSchedule.get().getCreatedAt());
        } else {
            openApplication = lendingApplicationDaoSlave.findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(merchantDetails.getId());
        }
        if (!ObjectUtils.isEmpty(openApplication)) {
            //kyc checks can be removed from here...
            LoanApplicationDetailsV3 loanApplicationDetailsV3 = setApplicationDetails(openApplication, merchantDetails);
            if (!ObjectUtils.isEmpty(loanApplicationDetailsV3)){
                loanDashboardResponse.setLoanApplication(loanApplicationDetailsV3);
                loanDashboardResponse.getLoanApplication().setAnnualRoi(getAnnualROI(openApplication));
            }
            if (loanDashboardResponse.getLoanApplication() != null && StringUtils.isEmpty(loanDashboardResponse.getLoanApplication().getReapply())) {
                //if no reapply then dont check eligibility
                cacheLoanDetailsData(loanDashboardResponse);
                emiDashboardService.skipData(emiDataCompletableFuture);
                return loanDashboardResponse;
            }
        }
        EmiDashboardResponse emiDashboardData = emiDashboardService.getData(emiDataCompletableFuture);
        if(emiUtils.isActive(emiDashboardData)){
            // send emiLoanApplication in response
            return handleEmiLoanDashboard(merchantDetails, emiDashboardData.getResult());
        }
        checkEligibility(loanDashboardResponse, merchantDetails);

        if(!ObjectUtils.isEmpty(entity)
                && !ObjectUtils.isEmpty(mileStoneHelperService.fetchTarget(entity))
                && RTEProgramType.SLIDER.name().equals(mileStoneHelperService.fetchTarget(entity).getProgram_type()) && "IN_PROGRESS".equalsIgnoreCase(entity.getSessionStatus())){
            loanDashboardResponse.setSliderEnrolled(true);
        }

        if(!Objects.nonNull(loanDashboardResponse.getIneligible()) && emiUtils.isRejectedWithConditions(emiDashboardData, openApplication)){
            // send emiLoanApplication in response
            return handleEmiLoanDashboard(merchantDetails, emiDashboardData.getResult());
        }
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantDetails.getId());
        if(emiUtils.isEligible(emiDashboardData, lendingRiskVariables) && loanDashboardResponse.getEligibility()!=null){
            log.info("eligible for loan merchant:{}", merchantDetails.getId());
            loanDashboardResponse.getEligibility().setEmiLoanAmount(emiDefaultLoanAmount);
        }
        if(Objects.nonNull(loanDashboardResponse.getIneligible())){
            loanDashboardResponse.setLoanApplication(null);
            funnelService.submitEvent(merchantDetails.getId(), null, null,
                    FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.INELIGIBLE, LocalDateTime.now().toString());
        } else if(Objects.isNull(loanDashboardResponse.getEligibility())){
            funnelService.submitEvent(merchantDetails.getId(), null, null,
                    FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.TEN_LAKH_LOAN_PAGE, LocalDateTime.now().toString());
        }
        cacheLoanDetailsData(loanDashboardResponse);
        log.info("returning response from database");
        log.info("loan dashboard response : {} for merchantId : {}",loanDashboardResponse,merchantDetails.getId());
        return loanDashboardResponse;
    }

    private boolean isEligibleForRTE(MileStoneEntity entity) {
        return !ObjectUtils.isEmpty(entity)
                && !ObjectUtils.isEmpty(mileStoneHelperService.fetchTarget(entity))
                && RTEProgramType.CASHBACK.name().equals(mileStoneHelperService.fetchTarget(entity).getProgram_type()) && Boolean.TRUE.equals(entity.getShowSummaryPage());
    }

    private LoanDashboardResponse handleEmiLoanDashboard(BasicDetailsDto merchantDetails, EmiDashboardResponse.Data emiDashboardDate) {
        return getLoanDashboardResponse(emiDashboardDate, merchantDetails);
    }

    public boolean hasActiveLoan(Long merchantId, LoanDashboardResponse loanDashboardResponse) {
        LendingPaymentScheduleSlave activeLoan = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(merchantId, Arrays.asList(LoanStatus.ACTIVE.name(), LoanStatus.DECEASED.name()));
        if(!ObjectUtils.isEmpty(activeLoan)) {
            loanDashboardResponse.setInsuranceEligibility(insuranceService.checkInsuranceEligibility(activeLoan));
        }
        return activeLoan != null;
    }

    private void setBankAccountDetails(Long merchantId,LoanDashboardResponse loanDashboardResponse) {
           BankAccountDetails bankAccountDetails=getAccountDetails(merchantId);
           if(Objects.nonNull(bankAccountDetails)){
               loanDashboardResponse.setAccountDetails(bankAccountDetails);
               loanDashboardResponse.setMerchantName(bankAccountDetails.getBeneficiaryName());
           }
    }
    private LoanDashboardResponse getLoanDashboardResponse(EmiDashboardResponse.Data emiDashboardData, BasicDetailsDto merchantDetails){
        LoanDashboardResponse loanDashboardResponse = new LoanDashboardResponse();
        loanDashboardResponse.setMerchantId(merchantDetails.getId());
        loanDashboardResponse.setRepeatLoan(emiDashboardData.isRepeatLoan());
        if(emiDashboardData.isActiveLoan()){
            loanDashboardResponse.setActiveEMILoan(true);
            return loanDashboardResponse;
        }
        LoanApplicationDetails loanApplicationDetails = getLoanApplicationDetails(emiDashboardData);
        loanDashboardResponse.setEmiLoanApplication(loanApplicationDetails);
        return loanDashboardResponse;
    }

    private static LoanApplicationDetails getLoanApplicationDetails(EmiDashboardResponse.Data emiDashboardData) {
        LoanApplicationDetails loanApplicationDetails = new LoanApplicationDetails();
        loanApplicationDetails.setApplicationId(emiDashboardData.getApplicationId());
        loanApplicationDetails.setApplicationStatus(emiDashboardData.getStatus());
        loanApplicationDetails.setLender(emiDashboardData.getLender());
        loanApplicationDetails.setTenure(emiDashboardData.getTenureMonth() != null ?
                String.valueOf(emiDashboardData.getTenureMonth()) : "");
        loanApplicationDetails.setInterestRate(emiDashboardData.getRoi());
        loanApplicationDetails.setEmi(emiDashboardData.getEmi());
        loanApplicationDetails.setRejectReason(emiDashboardData.getRejectReason());
        loanApplicationDetails.setApplicationStatus(emiDashboardData.getStatus());
        loanApplicationDetails.setLoanAmount(emiDashboardData.getLoanAmount());
        if(!StringUtils.isEmpty(emiDashboardData.getRejectReason()) && LoanDetailsConstant.LENDER_CHECKS_FAILED.equalsIgnoreCase(emiDashboardData.getRejectReason())){
            loanApplicationDetails.setLoanAmount(LoanDetailsConstant.EMI_DEFAULT_LOAN_AMOUNT);
        }
        return loanApplicationDetails;
    }

    private BankAccountDetails getAccountDetails(Long merchantId) {
        log.info("Getting bank account details for merchant:{}", merchantId);
        try {
            final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
            BankDetailsDto merchantBankDetail = null;
            if (bankDetailsDtoOptional.isPresent())
                merchantBankDetail = bankDetailsDtoOptional.get();
            if (merchantBankDetail == null) return null;

            return BankAccountDetails.builder()
                    .bankName(merchantBankDetail.getBankName())
                    .accountNumber("XXXX " + merchantBankDetail.getAccountNumber().substring(merchantBankDetail.getAccountNumber().length() - 4)).build();

        } catch (Exception e) {
            log.error("Exception in getAccountDetails for merchant:{}", merchantId);
        }
        return null;
    }

    private void setBankName(Long merchantId, LoanDashboardResponse loanDashboardResponse){
        BankAccountDetails bankAccountDetails=getBankName(merchantId);
        if(Objects.nonNull(bankAccountDetails)){
            loanDashboardResponse.setAccountDetails(bankAccountDetails);
        }
    }

    public BankAccountDetails getBankName(Long merchantId){
        log.info("Getting bank account details for merchant:{}", merchantId);
        try {
            final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
            BankDetailsDto merchantBankDetail = null;
            if (bankDetailsDtoOptional.isPresent())
                merchantBankDetail = bankDetailsDtoOptional.get();
            if (merchantBankDetail == null) return null;

            return BankAccountDetails.builder()
                    .bankName(merchantBankDetail.getBankName()).build();

        } catch (Exception e) {
            log.error("Exception in getAccountDetails for merchant:{}", merchantId);
        }
        return null;
    }

    private void populateBusinessDetails(Long merchantId, LoanDetailsResponse loanDetailsResponse) {
        LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        if (Objects.nonNull(lendingMerchantDetails)) {
            loanDetailsResponse.setBusinessName(lendingMerchantDetails.getBusinessName());
            loanDetailsResponse.setBusinessCategory(lendingMerchantDetails.getBusinessCategory());
            loanDetailsResponse.setBusinessSubCategory(lendingMerchantDetails.getBusinessSubCategory());
        }
    }

    private void setBpClubMember(Long merchantId,LoanDetailsResponse loanDetailsResponse){
        String bpMembershipKey = BP_CLUB_MEMBERSHIP_KEY_PREFIX + merchantId;
        Object bpCLubResponse = lendingCache.get(bpMembershipKey);
        if (ObjectUtils.isEmpty(bpCLubResponse)) {
            Boolean isBpClubMember = apiGatewayService.eligibleForProcessingFee(merchantId);
            loanDetailsResponse.setBpClubMember(isBpClubMember);
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(bpMembershipKey);
            addCacheDto.setValue(isBpClubMember);
            addCacheDto.setTtl(7 * 24);
            lendingCache.add(addCacheDto);
        } else {
            loanDetailsResponse.setBpClubMember((Boolean) bpCLubResponse);
        }
    }

    public Boolean isClubV2Member(Long merchantId){
        String clubV2MembershipKey = LoanDetailsConstant.CLUB_V2_MEMBERSHIP_KEY_PREFIX + merchantId;
        Object clubV2Response = lendingCache.get(clubV2MembershipKey);
        if (ObjectUtils.isEmpty(clubV2Response)) {
            Boolean isBpClubMember = apiGatewayService.eligibleForProcessingFee(merchantId);
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(clubV2MembershipKey);
            addCacheDto.setValue(isBpClubMember);
            addCacheDto.setTtl(7 * 24);
            lendingCache.add(addCacheDto);
            return isBpClubMember;
        } else {
            return ((Boolean) clubV2Response);
        }
    }

    private LoanApplicationDetailsV3 setApplicationDetails(LendingApplicationSlave openApplication,BasicDetailsDto merchantDetails) {
        try {
            LoanApplicationDetailsV3 applicationDetails = new LoanApplicationDetailsV3();
            applicationDetails.setApplicationId(openApplication.getId());
            applicationDetails.setLoanAmount(openApplication.getLoanAmount());
            applicationDetails.setTenure(openApplication.getTenure());
            applicationDetails.setEdi(openApplication.getEdi());
            applicationDetails.setInterestRate(openApplication.getInterestRate());
            applicationDetails.setApplicationStatus(openApplication.getStatus().toLowerCase());
            applicationDetails.setLender(openApplication.getLender());
            
            boolean isTopup = LoanType.TOPUP.name().equals(openApplication.getLoanType());

            boolean isNachApproved = NachStatus.APPROVED.name().equalsIgnoreCase(openApplication.getNachStatus()) || NachStatus.APPROVED.name().equalsIgnoreCase(openApplication.getUpiAutopayStatus());

            if(!isTopup && !isNachApproved){
                LendingApplicationDetails lendingApplicationDetails = lendingApplicationServiceV3.getLendingApplicationDetailsByApplicationId(openApplication.getId());
                boolean isAtMandatePage = LendingViewStates.ENACH_PAGE.name().equalsIgnoreCase(lendingApplicationDetails.getApplicationViewState())
                        || LendingViewStates.UPI_AUTOPAY_PAGE.name().equalsIgnoreCase(lendingApplicationDetails.getApplicationViewState());
                applicationDetails.setMandatePending(isAtMandatePage);
            }

            if(ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())){
                funnelService.submitEvent(merchantDetails.getId(), null, openApplication.getId(),
                        FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.PENDING_APPLICATION, LocalDateTime.now().toString());
            }
            if ("approved".equalsIgnoreCase(openApplication.getStatus()) || "pending_verification".equalsIgnoreCase(openApplication.getStatus())) {
                LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationIdAndMerchantId(openApplication.getId(), openApplication.getMerchantId());
                if(Objects.nonNull(lendingResubmitTask)){
                    if (lendingResubmitTask.getResubmit() != null && lendingResubmitTask.getResubmit() && (lendingResubmitTask.getResubmitDone() == null || !lendingResubmitTask.getResubmitDone())) {
                        applicationDetails.setApplicationStatus("RESUBMIT");
                        applicationDetails.setResubmitReason(lendingResubmitTask.getResubmitReason());
                        String resubmitDoneReasons = getResubmitDoneReasons(openApplication.getId(), openApplication.getMerchantId());
                        applicationDetails.setCompletedResubmitReason(resubmitDoneReasons);
                    }
                    else if (lendingResubmitTask.getDowngrade() != null && lendingResubmitTask.getDowngrade() && (lendingResubmitTask.getDowngradeDone() == null || !lendingResubmitTask.getDowngradeDone())) {
                        applicationDetails.setApplicationStatus("DOWNGRADE");
                    }
                    else if(lendingResubmitTask.getResign() != null && lendingResubmitTask.getResign() && (lendingResubmitTask.getResignDone() == null || !lendingResubmitTask.getResignDone())) {
                        applicationDetails.setApplicationStatus("RESIGN");
                    }
                }
                addApplicationStages(openApplication,applicationDetails);

                applicationDetails.setTransferDays(loanUtil.getApplicationTatMessage(openApplication));
            }

            if("rejected".equalsIgnoreCase(openApplication.getStatus())){
                funnelService.submitEvent(merchantDetails.getId(), null, openApplication.getId(),
                        FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.REJECTED, LocalDateTime.now().toString());
                RejectionStateDto rejectionStateDto = getRejectionReason(openApplication, merchantDetails);
                applicationDetails.setRejectReason(rejectionStateDto.getRejectionReason());
                Long reapplyTime = getReapplyTime(openApplication);
                if (Objects.nonNull(reapplyTime)) {
                    reapplyTime = reapplyTime > 0 ? reapplyTime : 0;
                    applicationDetails.setReapplyTime(reapplyTime);
                    applicationDetails.setReapplyTimeEpoch(LoanUtil.addDays(new Date(), reapplyTime).getTime());
                }
                applicationDetails.setReapply(shouldReapply(openApplication, reapplyTime));
            }
            return applicationDetails;
        } catch (Exception e) {
            log.error("Exception in setApplicationDetails for merchant:{}, {}", openApplication.getMerchantId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getResubmitDoneReasons(Long applicationId, Long merchantId){
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
                    && lendingResubmitReasonCount.getResubmitDone()){
                if("".equals(reason))reason = reason + lendingResubmitReasonCount.getResubmitReason();
                else reason = reason + "," + lendingResubmitReasonCount.getResubmitReason();
            }
        }
        if("".equals(reason))return null;
        return reason;
    }

    private RejectionStateDto getRejectionReason(LendingApplicationSlave openApplication, BasicDetailsDto merchant) {
        RejectionStateDto rejectionStateDto = new RejectionStateDto();
        String rejectionReason = null;
        String rejectionMessage = null;
        if (!ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getStatus()))
            return rejectionStateDto;
        if (KycStatus.REJECTED.name().equalsIgnoreCase(openApplication.getCkycStatus())) {
            rejectionReason = openApplication.getCkycRejectionReason();
        } else if (KycStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualKyc())) {
//            rejectionMessage = easyLoanUtil.getRejectionMessage(openApplication.getManualKycReason(), RejectionStage.KYC);
            rejectionReason = Objects.nonNull(openApplication.getManualKycReason()) ? openApplication.getManualKycReason() : null;
        }
        else if (KycStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualCibil())) {
//            rejectionMessage = easyLoanUtil.getRejectionMessage(openApplication.getManualCibilReason(), RejectionStage.CIBIL);
            rejectionReason = Objects.nonNull(openApplication.getManualCibilReason()) ? openApplication.getManualCibilReason() : null;
        }
        else if (KycStatus.REJECTED.name().equalsIgnoreCase(openApplication.getPhysicalVerificationStatus())) {
//            rejectionMessage = easyLoanUtil.getRejectionMessage(openApplication.getPhysicalReason(), RejectionStage.QC);
            rejectionReason = Objects.nonNull(openApplication.getPhysicalReason()) ? openApplication.getPhysicalReason() : null;
        } else if (KycStatus.REJECTED.name().equalsIgnoreCase(openApplication.getPhysicalReason())) {
//            rejectionMessage = easyLoanUtil.getRejectionMessage(openApplication.getPhysicalReason(), RejectionStage.QC);
            rejectionReason = openApplication.getPhysicalReason();
        } else if (!StringUtils.isEmpty(openApplication.getRejectionReason())) {
            rejectionReason = openApplication.getRejectionReason();
        }
        if("ABFL".equalsIgnoreCase(openApplication.getLender())){
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(openApplication.getId(), openApplication.getLender());
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) && LenderAssociationStatus.DOC_GENERATE_FAILED.name().equalsIgnoreCase(lendingApplicationLenderDetails.getLeadStatus())) {
                rejectionReason = "FORCE_REJECT_LENDER_DOC_GENERATE_FAILED";
            }
        }

        rejectionMessage = Objects.nonNull(rejectionMessage) ? rejectionMessage : "Please re-apply with correct shop details";
        rejectionStateDto.setRejectionReason(rejectionReason);
        rejectionStateDto.setRejectionMessage(rejectionMessage);
        return rejectionStateDto;
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

    private String getCurrentAddress(LendingApplication lendingApplication) {
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());
        return lendingGstDetail != null && !StringUtils.isEmpty(lendingGstDetail.getCurrentAddress()) && "Different".equalsIgnoreCase(lendingGstDetail.getAddressType()) ? lendingGstDetail.getCurrentAddress() : null;
    }

    private boolean isShopPhotoRequired(LendingApplication openApplication) {
        if (ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())) {
            List<LendingShopDocuments> lendingShopDocumentsList = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(openApplication.getMerchantId(), openApplication.getId());
            return lendingShopDocumentsList.size() < 2;
        }
        return false;
    }

    private Long getReapplyTime(LendingApplicationSlave lendingApplication) {
        Long reapplyTime = null;
        if ("rejected".equalsIgnoreCase(lendingApplication.getStatus())) {
            Integer reapplyDayDiff = null;
            if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getManualCibilReason(), RejectionStage.CIBIL, lendingApplication.getMerchantId());
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualKyc())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getManualKycReason(), RejectionStage.KYC, lendingApplication.getMerchantId());
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getPhysicalReason(), RejectionStage.QC, lendingApplication.getMerchantId());
            } else if (!ObjectUtils.isEmpty(lendingApplication.getRejectionStage()) && RejectionStage.BRE.name().equalsIgnoreCase(lendingApplication.getRejectionStage().name())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getRejectionReason(), RejectionStage.BRE, lendingApplication.getMerchantId());
            } else if (!ObjectUtils.isEmpty(lendingApplication.getRejectionStage()) && "PNC".equalsIgnoreCase(lendingApplication.getRejectionStage().name())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getRejectionReason(), RejectionStage.PNC, lendingApplication.getMerchantId());
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

    private String shouldReapply(LendingApplicationSlave openApplication, Long reapplyTime) {

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
    private void checkEligibility(LoanDashboardResponse loanDashboardResponse, BasicDetailsDto merchant)  {
        log.info("checking eligibility for {}", merchant.getId());
        /*
        * COMMENT THIS ---
         */
//      MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchant.getId());
//      if (ObjectUtils.isEmpty(merchantResponseDTO)) {
//          throw new MerchantSummaryExceptionHandler(merchant.getId().toString());
//      }

        if (enableDiwaliBanner) {
            handleDiwaliBanner(merchant, loanDashboardResponse);
        }

        Experian experian = experianDao.getByMerchantId(merchant.getId());
        if(ObjectUtils.isEmpty(experian) || Objects.isNull(experian.getPincode())){
            log.info("No experian record for merchantId:{},returning empty records", merchant.getId());
            return;
        }

        // Step 1: Check cached eligibility
        Eligibility eligibility = checkCachedEligibility(merchant, loanDashboardResponse);
        if (eligibility != null) {
            return;
        }

        // Step 2: Fetch global limit response
        MutableBoolean isDerog = new MutableBoolean(false);
        GlobalLimitResponse globalLimitResponse = fetchGlobalLimitResponse(merchant, isDerog);

        // Step 3: Process global limit response
        Double eligibleAmount = processGlobalLimitResponse(merchant, globalLimitResponse, loanDashboardResponse, isDerog);

        // Step 4: Recompute eligibility if eligible amount is greater than 0
        if (eligibleAmount > 0D) {
            log.info("Eligibility found for merchant:{}", merchant.getId());
            eligibility = recomputeEligibility(merchant, globalLimitResponse);
        }

        // Step 5: Set pre-approved tag and funnel event
        setPreApprovedTag(merchant, loanDashboardResponse);

        if (eligibility != null) {
            loanDashboardResponse.setEligibility(eligibility);
            return;
        }
        log.info("Eligibility not found for merchant:{}", merchant.getId());
        boolean eligibilityErrorFlag = false;
        if(Objects.nonNull(globalLimitResponse)  && Objects.nonNull(globalLimitResponse.getErrorCode())) {
            if (LendingConstants.CALL_MASKED_MOBILE_FLOW.equals(globalLimitResponse.getErrorCode())) {
                loanDashboardResponse.setIsMaskedMobileCase(true);
            }
            eligibilityErrorFlag = loanUtil.isEligibilityErrorResponse(globalLimitResponse);
            if(!eligibilityErrorFlag) {
                loanDashboardResponse.setEligibilityExceptionFlag(false);
                return;
            }
        }

        String ineligibleReason = getIneligibleReason(merchant.getId(), isDerog, experian.getPincode(), globalLimitResponse);
        if(IneligibleType.DEROG.name().equals(ineligibleReason)){
            LocalDate reapplyTime = getReapplyTime(merchant.getId());
            loanDashboardResponse.setDerogMerchantReapplyDate(reapplyTime);
        }
        loanDashboardResponse.setIneligible(ineligibleReason);
        loanDashboardResponse.setChangeBankAccount(IneligibleType.CHANGE_BANK_ACCOUNT.name().equalsIgnoreCase(ineligibleReason));
        if(Objects.nonNull(loanDashboardResponse.getIneligible()) &&
                    (IneligibleType.ENACH.name().equalsIgnoreCase(loanDashboardResponse.getIneligible()) ||
                            IneligibleType.CHANGE_BANK_ACCOUNT.name().equalsIgnoreCase(loanDashboardResponse.getIneligible()))) {
            setBankName(merchant.getId(), loanDashboardResponse);
        }

        loanDashboardResponse.setEligibilityExceptionFlag(eligibilityErrorFlag);
        loanDashboardResponse.setRefreshCountDownMinutes(loanUtil.getRefreshCountDownMinutes(globalLimitResponse));
    }

    private void setPreApprovedTag(BasicDetailsDto merchant, LoanDashboardResponse loanDashboardResponse) {
        loanDashboardResponse.setPreApprovedTag(getPreApprovedTag(merchant.getId()));
        if (Objects.nonNull(loanDashboardResponse.getPreApprovedTag())) {
            funnelService.submitEvent(merchant.getId(), null, null,
                    FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.PREAPPROVED, loanDashboardResponse.getPreApprovedTag());
        }
    }
    private Eligibility recomputeEligibility(BasicDetailsDto merchant, GlobalLimitResponse globalLimitResponse) {
        LendingEligibleLoan eligibleLoan = recomputeEligibleLoan(globalLimitResponse, null, merchant.getId());
        if (!ObjectUtils.isEmpty(eligibleLoan)) {
            return createEligibility(merchant.getId(), eligibleLoan);
        }
        return null;
    }

    private Eligibility checkCachedEligibility(BasicDetailsDto merchant, LoanDashboardResponse loanDashboardResponse) {
        LendingEligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdAndLoanTypeNotTopup(merchant.getId());
        String bureauConsentKey = LendingConstants.BUREAU_CONSENT_KEY_PREFIX + merchant.getId();
        int updatedEligibilityRefreshWindow = easyLoanUtil.percentScaleUp(merchant.getId(), newEligibilityRefreshWindowRolloutPercent)
                ? newEligibilityRefreshWindow : eligibilityRefreshWindow;

        if (Objects.nonNull(lendingCache.get(bureauConsentKey))) {
            updatedEligibilityRefreshWindow = 0;
            lendingCache.delete(bureauConsentKey);
        }

        Date dateWindow = dateTimeUtil.getDatePlusDays(dateTimeUtil.getCurrentDate(), -24 * updatedEligibilityRefreshWindow);
        if (!ObjectUtils.isEmpty(eligibleLoan) && eligibleLoan.getCreatedAt().after(dateWindow)) {
            log.info("Eligible offers exist for merchant: {}", merchant.getId());
            Eligibility eligibility = createEligibility(merchant.getId(), eligibleLoan);
            if (eligibility != null) {
                log.info("Eligibility is not null for merchant: {}", merchant.getId());
                loanDashboardResponse.setEligibility(eligibility);
                return eligibility;
            }
        }
        log.info("No cached eligibility found for merchant: {}", merchant.getId());
        return null;
    }

    private GlobalLimitResponse fetchGlobalLimitResponse(BasicDetailsDto merchant, MutableBoolean isDerog) {
        try {
            return apiGatewayService.getGlobalLimit(merchant.getId(), isClubV2Member(merchant.getId()), EligibilityRequestSource.EASY_LOANS);
        } catch (BureauCallMaskedApiException e) {
            log.error("Exception occurred while fetching global limit for merchantId: {}, exception: {}", merchant.getId(), e);
            return new GlobalLimitResponse();
        }
    }

    private Double processGlobalLimitResponse(BasicDetailsDto merchant, GlobalLimitResponse globalLimitResponse, LoanDashboardResponse loanDashboardResponse, MutableBoolean isDerog) {
        Double eligibleAmount = 0D;
        if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
            log.info("Global limit for merchant: {} is {}", merchant.getId(), globalLimitResponse.getData().getGlobalLimit());
            eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
            isDerog.setValue(globalLimitResponse.getData().isDerog());
            loanDashboardResponse.setPreviousFinalOffer(globalLimitResponse.getData().getPreviousFinalOffer());
            loanDashboardResponse.setOfferIncreased(globalLimitResponse.getData().getOfferIncreased());
        }
        return eligibleAmount;
    }

    private void handleDiwaliBanner(BasicDetailsDto merchant, LoanDashboardResponse loanDashboardResponse) {
        Date diwaliBannerOneRolloutDateParsed = DateUtils.parseDate(diwaliBannerOneRolloutDate);
        Date diwaliBannerOneEndDateParsed = DateUtils.parseDate(diwaliBannerOneEndDate);

        Date diwaliBannerTwoRolloutDateParsed = DateUtils.parseDate(diwaliBannerTwoRolloutDate);
        Date diwaliBannerTwoEndDateParsed = DateUtils.parseDate(diwaliBannerTwoEndDate);

        if (!ObjectUtils.isEmpty(diwaliBannerOneRolloutDateParsed) && !ObjectUtils.isEmpty(diwaliBannerOneEndDateParsed)) {
            if (new Date().after(diwaliBannerOneRolloutDateParsed) && new Date().before(diwaliBannerOneEndDateParsed)) {
                log.info("Enabling diwali banner one for merchantId : {}", merchant.getId());
                loanDashboardResponse.setDiwaliBannerType(getDiwaliBannerType(merchant.getId()));
                loanDashboardResponse.setDiwaliBannerEndDate(new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS).format(diwaliBannerOneEndDateParsed));
            }
        }

        if (!ObjectUtils.isEmpty(diwaliBannerTwoRolloutDateParsed) && !ObjectUtils.isEmpty(diwaliBannerTwoEndDateParsed)) {
            if (new Date().after(diwaliBannerTwoRolloutDateParsed) && new Date().before(diwaliBannerTwoEndDateParsed)) {
                log.info("Enabling diwali  banner two for merchantId : {}", merchant.getId());
                loanDashboardResponse.setDiwaliBannerType(getDiwaliBannerType(merchant.getId()));
                loanDashboardResponse.setDiwaliBannerEndDate(new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS).format(diwaliBannerTwoEndDateParsed));
            }
        }
    }


    private void cacheLoanDetailsData(LoanDashboardResponse loanDashboardResponse) {
        //Response should not be cached in case of countdown minutes, so merchant can see updated timer on app restart
        if(!ObjectUtils.isEmpty(loanDashboardResponse) && !ObjectUtils.isEmpty(loanDashboardResponse.getRefreshCountDownMinutes())){
            log.info("Skipping cache for merchant_id: {}", loanDashboardResponse.getMerchantId());
            return;
        }
        try {
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX+loanDashboardResponse.getMerchantId());
            addCacheDto.setValue(objectMapper.writeValueAsString(loanDashboardResponse));
            addCacheDto.setTtl(loanDetailsRefreshWindow);
            lendingCache.add(addCacheDto, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("exception occured while caching loan details for {} !!", LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX+loanDashboardResponse.getMerchantId());
        }
    }

    private void cacheVersionDetails(LoanDashboardApiVersion loanDashboardApiVersion, Long merchantId) {
        try {
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(LoanDetailsConstant.LENDING_VERSION_KEY_PREFIX+merchantId);
            addCacheDto.setValue(objectMapper.writeValueAsString(loanDashboardApiVersion));
            addCacheDto.setTtl(loanVersionApiRefreshWindow);
            lendingCache.add(addCacheDto, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("exception occured while caching version detail for {} !!", LoanDetailsConstant.LENDING_VERSION_KEY_PREFIX+merchantId);
        }
    }

    public Eligibility createEligibility(Long merchantId, LendingEligibleLoan eligibleLoan) {
        try {
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
                    .initialRoi(eligibleLoan.getInitialRoi())
                    .clubV2Amount(eligibleLoan.getClubV2Amount())
                    .uniqueKey(eligibleLoan.getId())
                    .build();
        } catch (Exception e) {
            log.error("Exception in createEligibility for merchant:{}", merchantId, e);
        }
        return null;
    }

    private void addApplicationStages(LendingApplicationSlave openApplication,LoanApplicationDetailsV3 loanApplicationDetails){
        // here we are adding application stages as well
        List<LoanApplicationStage> loanApplicationStageList = new ArrayList<>();
        LoanApplicationStage loanApplicationStage=new LoanApplicationStage();
        loanApplicationStage.setStage(LoanDetailsConstant.APPLICATION_SUBMITTED);
        loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_PENDING);
        if(Objects.nonNull(openApplication.getNachLender()) && NachStatus.APPROVED.name().equalsIgnoreCase(openApplication.getNachStatus())){
            loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_SUCCESS);
        }
        loanApplicationStageList.add(loanApplicationStage);
        loanApplicationStage=new LoanApplicationStage();
        loanApplicationStage.setStage(LoanDetailsConstant.REVIEW);
        loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_PENDING);
        // in case of auto disbursal lmsStage will be null, so checking if application status is approved or not.
        if(ApplicationStatus.APPROVED.name().equalsIgnoreCase(openApplication.getStatus()) &&
                (Objects.isNull(openApplication.getLmsStage()) || LendingConstants.SEND_TO_NBFC.equalsIgnoreCase(openApplication.getLmsStage()))
        ){
            loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_SUCCESS);
        }
        loanApplicationStageList.add(loanApplicationStage);
//        loanApplicationStage=new LoanApplicationStage();
//        loanApplicationStage.setStage(LoanDetailsConstant.SEND_TO_NBFC);
//        loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_PENDING);
//        if(Objects.nonNull(openApplication.getSendToNbfc()) || Objects.nonNull(openApplication.getNbfcSendDate())){
//            loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_SUCCESS);
//        }
//        loanApplicationStageList.add(loanApplicationStage);
        loanApplicationStage=new LoanApplicationStage();
        loanApplicationStage.setStage(LoanDetailsConstant.DISBURSAL);
        loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_PENDING);
        if(Objects.nonNull(openApplication.getDisburseTimestamp()) && "DISBURSED".equalsIgnoreCase(openApplication.getLoanDisbursalStatus())){
            loanApplicationStage.setStatus(LoanDetailsConstant.STATUS_SUCCESS);
        }
        loanApplicationStageList.add(loanApplicationStage);
        loanApplicationDetails.setLoanApplicationStageList(loanApplicationStageList);
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
                log.info("Derog merchant: {}", merchantId);
                return IneligibleType.DEROG.name();
            }

            if (!loanUtil.isEnachBank(merchantId)) {
                log.info("Not an enach bank for the  merchantId:{}", merchantId);
                return IneligibleType.CHANGE_BANK_ACCOUNT.name();
            }

        } catch (Exception e) {
            log.error("Exception in getIneligibleReason for merchant:{}", merchantId, e);
        }
        log.info("Ineligible merchant:{}", merchantId);
        return RejectionReason.LOW_TRANSACTION.getReason();
    }

    public LocalDate getReapplyTime(Long merchantId) {
        MerchantMetadata merchantMetadata = merchantMetadataDao.findByMerchantId(merchantId);

        // Case 1: First time derog → set initial reapply date = now + 90 days
        if (ObjectUtils.isEmpty(merchantMetadata)|| ObjectUtils.isEmpty(merchantMetadata.getReapplyTime())) {
            LocalDate reapplyDate = LocalDate.now().plusDays(90);
            merchantMetadata = new MerchantMetadata();
            merchantMetadata.setMerchantId(merchantId);
            merchantMetadata.setReapplyTime(reapplyDate);
            merchantMetadataDao.save(merchantMetadata);
            log.info("Initial reapply date (90 days later) set for merchant {}: {}", merchantId, reapplyDate);
            return reapplyDate;
        }

        LocalDate existingDate = merchantMetadata.getReapplyTime();
        LocalDate today = LocalDate.now();

        // Case 2a: One day before expiry → extend by 90 days from expiry date
        if (today.isEqual(existingDate.minusDays(1))) {
            LocalDate newDate = existingDate.plusDays(90);
            merchantMetadata.setReapplyTime(newDate);
            merchantMetadataDao.save(merchantMetadata);
            log.info("Reapply date extended for merchant {} (expiry -1 day): {} -> {}",
                    merchantId, existingDate, newDate);
            return newDate;
        }

        // Case 2b: Already past expiry → extend by 90 days from today
        if (today.isAfter(existingDate)) {
            LocalDate newDate = today.plusDays(90);
            merchantMetadata.setReapplyTime(newDate);
            merchantMetadataDao.save(merchantMetadata);
            log.info("Reapply date expired. New date set for merchant {}: {}", merchantId, newDate);
            return newDate;
        }

        // Case 3: Otherwise → return existing
        log.info("Existing reapply date for merchant {}: {}", merchantId, existingDate);
        return existingDate;
    }


    public LendingEligibleLoan recomputeEligibleLoan(GlobalLimitResponse globalLimitResponse, Double customAmount, Long merchantId) {
        if (Objects.isNull(globalLimitResponse) || Objects.isNull(globalLimitResponse.getData())) {
            log.info("Global Limit not found");
            return null;
        }
        Double finalLimit = globalLimitResponse.getData().getGlobalLimit();
        String loanType = globalLimitResponse.getData().getLoanType();
        Double version = globalLimitResponse.getData().getVersion();
        LendingEligibleLoan eligibleLoan = null;
        try {
            List<GlobalLimitResponse.OfferDetail> offerDetails = globalLimitResponse.getData().getOfferDetails();
            offerDetails.sort(Comparator.comparingInt(GlobalLimitResponse.OfferDetail::getTenure));
            for (GlobalLimitResponse.OfferDetail offerDetail : offerDetails) {
                log.info("Tenure: {}, finalLimit: {}, loanAmount: {}, customAmount: {}", offerDetail.getTenure(), finalLimit, offerDetail.getLoanAmount(), customAmount);
                if (Objects.nonNull(customAmount) && customAmount < finalLimit && customAmount <= offerDetail.getLoanAmount()) {
                    eligibleLoan = loanUtil.calculateLoanBreakup(offerDetail, merchantId, loanType, customAmount, null, version, true);
                }
                if (finalLimit <= offerDetail.getMaxLoanAmount() && finalLimit <= (offerDetail.getLoanAmount())) {
                    eligibleLoan = loanUtil.calculateLoanBreakup(offerDetail, merchantId, loanType, finalLimit, null, version, true);
                }
            }
        } catch (Exception e) {
            log.error("Exception while recomputing eligible loan for merchant:{}", merchantId, e);
        }

        return eligibleLoan;
    }

    public void deleteLoanDashboardCache(Long merchantId){
        try{
            if(Objects.isNull(merchantId))return;
            String loanDetailsCacheKey = LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + merchantId;
            log.info("deleting cached key of loan dashboard api for merchant: {}",merchantId);
            lendingCache.delete(loanDetailsCacheKey);
        }
        catch(Exception e){
            log.error("unable to evict dashboard api cache for : {}", merchantId);
        }
    }

    public String getPreApprovedTag(Long merchantId){
        LendingRiskVariablesSlave lendingRiskVariables = lendingRiskVariablesDaoSlave.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        if(ObjectUtils.isEmpty(lendingRiskVariables)){
            return null;
        }
        String pilotIdentifier = lendingRiskVariables.getPilotIdentifier();
        if(ObjectUtils.isEmpty(pilotIdentifier)){
            return null;
        }
        if(pilotIdentifier.contains(LoanDetailsConstant.PREAPPROVED_TOPUP_LOAN_IDENTIFIER)){
            log.info("loan request is pre-approved for {}", merchantId);
            return PreApprovedLoanEnums.PRE_APPROVED_TOPUP.name();
        }
        if(pilotIdentifier.contains(LoanDetailsConstant.PREAPPROVED_REPEAT_LOAN_IDENTIFIER)){
            log.info("loan request is pre-approved for {}", merchantId);
            return PreApprovedLoanEnums.PRE_APPROVED_REPEAT.name();
        }
        if(pilotIdentifier.contains(LoanDetailsConstant.PREAPPROVED_FRESH_LOAN_IDENTIFIER)){
            log.info("loan request is pre-approved for {}", merchantId);
            return PreApprovedLoanEnums.PRE_APPROVED_FRESH.name();
        }
        return null;
    }

    private String getDiwaliBannerType(Long merchantId) {
        LendingRiskVariablesSlave lendingRiskVariables = lendingRiskVariablesDaoSlave.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        if(ObjectUtils.isEmpty(lendingRiskVariables)){
            return "Default";
        }
        String pilotIdentifier = lendingRiskVariables.getPilotIdentifier();
        if(ObjectUtils.isEmpty(pilotIdentifier)){
            return "Default";
        }

        if(pilotIdentifier.contains(LoanDetailsConstant.DIWALI_BANNER_IDENTIFIER)){
            log.info("loan request is pre-approved for {}", merchantId);
            return "Interest";
        }
        return "Default";
    }

    private boolean percentScaleUp(Long merchantId, Integer percent) {
        log.info("checking percent scale up for merchant: {} with percent: {}", merchantId, percent);
        switch (percent) {
            case 1 :
                if (isOnePercentRollOutMerchant(merchantId)){
                    return true;
                }
                break;
            case 5 :
                if(isFivePercentRollOutMerchant(merchantId)){
                    return true;
                }
                break;
            case 10 :
                if (isTenPercentRollOutMerchant(merchantId)) {
                    return true;
                }
                break;
            case 20 :
                if (isTwentyPercentRollOutMerchant(merchantId)) {
                    return true;
                }
                break;
            case 50 :
                if (isFiftyPercentRollOutMerchant(merchantId)) {
                    return true;
                }
                break;
            case 100 :
                return true;
        }

        return false;
    }

    private Date getThresholdDate(Long merchantId) throws ParseException {
        String DATE_FORMAT = "yyyy-MM-dd hh:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        Date date = null;
        if(screenRedesignRolloutPercent == 1 && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)){
            date = sdf.parse(screenRedesignOnePercentRolloutDate);
        }
        else if(screenRedesignRolloutPercent == 5){
            if(isOnePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)) date = sdf.parse(screenRedesignOnePercentRolloutDate);
            else date = sdf.parse(screenRedesignFivePercentRolloutDate);
        }
        else if(screenRedesignRolloutPercent == 10){
            if(isOnePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)) date = sdf.parse(screenRedesignOnePercentRolloutDate);
            else if(isFivePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignFivePercentRolloutDate)) date = sdf.parse(screenRedesignFivePercentRolloutDate);
            else date = sdf.parse(screenRedesignTenPercentRolloutDate);
        }
        else if(screenRedesignRolloutPercent == 20){
            if(isOnePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)) date = sdf.parse(screenRedesignOnePercentRolloutDate);
            else if(isFivePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignFivePercentRolloutDate)) date = sdf.parse(screenRedesignFivePercentRolloutDate);
            else if(isTenPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignTenPercentRolloutDate)) date = sdf.parse(screenRedesignTenPercentRolloutDate);
            else date = sdf.parse(screenRedesignTwentyPercentRolloutDate);
        }
        else if(screenRedesignRolloutPercent == 50){
            if(isOnePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)) date = sdf.parse(screenRedesignOnePercentRolloutDate);
            else if(isFivePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignFivePercentRolloutDate)) date = sdf.parse(screenRedesignFivePercentRolloutDate);
            else if(isTenPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignTenPercentRolloutDate)) date = sdf.parse(screenRedesignTenPercentRolloutDate);
            else if(isTwentyPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignTwentyPercentRolloutDate)) date = sdf.parse(screenRedesignTwentyPercentRolloutDate);
            else date = sdf.parse(screenRedesignFiftyPercentRolloutDate);
        }
        else{
            if(isOnePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignOnePercentRolloutDate)) date = sdf.parse(screenRedesignOnePercentRolloutDate);
            else if(isFivePercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignFivePercentRolloutDate)) date = sdf.parse(screenRedesignFivePercentRolloutDate);
            else if(isTenPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignTenPercentRolloutDate)) date = sdf.parse(screenRedesignTenPercentRolloutDate);
            else if(isTwentyPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignTwentyPercentRolloutDate)) date = sdf.parse(screenRedesignTwentyPercentRolloutDate);
            else if(isFiftyPercentRollOutMerchant(merchantId) && !ObjectUtils.isEmpty(screenRedesignFiftyPercentRolloutDate)) date = sdf.parse(screenRedesignFiftyPercentRolloutDate);
            else date = sdf.parse(screenRedesignHundredPercentRolloutDate);
        }
        log.info("threshold date for {} : {}", merchantId, date);
        return date;
    }

    private boolean isOnePercentRollOutMerchant(Long merchantId){
        if(merchantId % 100 == 11)return true;
        return false;
    }

    private boolean isFivePercentRollOutMerchant(Long merchantId){
        if(merchantId % 10 == 1 && (merchantId % 100 - 1)/10 % 2 == 0)return true;
        return false;
    }

    private boolean isTenPercentRollOutMerchant(Long merchantId){
        if(merchantId % 10 == 1)return true;
        return false;
    }

    private boolean isTwentyPercentRollOutMerchant(Long merchantId){
        if(String.valueOf(merchantId).endsWith("1") || String.valueOf(merchantId).endsWith("2"))return true;
        return false;
    }

    private boolean isFiftyPercentRollOutMerchant(Long merchantId){
        if (String.valueOf(merchantId).endsWith("1") || String.valueOf(merchantId).endsWith("2") || String.valueOf(merchantId).endsWith("3") ||
                String.valueOf(merchantId).endsWith("4") || String.valueOf(merchantId).endsWith("5")
        ) {
            return true;
        }
        return false;
    }

    private Double getAnnualROI(LendingApplicationSlave lendingApplicationSlave) {
        Double annualROI = null;
        try {
            if(ObjectUtils.isEmpty(lendingApplicationSlave.getLender())){
                log.info("Lender not found for application id: {}", lendingApplicationSlave.getId());
                return annualROI;
            }

            LendingApplicationLenderDetailsSlave lendingApplicationLenderDetailsSlave = lendingApplicationLenderDetailsDaoSlave.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplicationSlave.getId(), "ACTIVE", lendingApplicationSlave.getLender());

            if(!ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave)){
                annualROI = lendingApplicationLenderDetailsSlave.getAnnualRoi();
            }

            if(!LoanType.TOPUP.name().equalsIgnoreCase(lendingApplicationSlave.getLoanType()) && ObjectUtils.isEmpty(annualROI)){
                    annualROI = lendingApplicationServiceV2.getApr(lendingApplicationSlave.getMerchantId(), lendingApplicationSlave.getId(), lendingApplicationSlave.getLoanAmount(),
                            LenderOffDays.valueOf(lendingApplicationSlave.getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplicationSlave.getLender());
            }
            log.info("AnnualROI--------> {} , applicationID: {}", annualROI, lendingApplicationSlave.getId());
        }catch (Exception ex){
            log.error("Exception in fetching annual roi for applicationID {}, error: {}", lendingApplicationSlave.getId(), Arrays.asList(ex.getStackTrace()));
        }

        return annualROI;
    }
}
