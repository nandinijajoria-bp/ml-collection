package com.bharatpe.lending.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.PhonebookHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.dto.PhonebookDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.entity.LendingEligibleLoan;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.query.dao.*;
import com.bharatpe.lending.common.query.entity.*;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.lendingplatform.lms.service.LoanDisplayService;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.loanV2.service.ExcessNachService;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.loanV3.dto.TopupEligibilityResponseData;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.util.KycUtils;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.service.helper.MerchantLoanServiceHelper;
import com.bharatpe.lending.service.impl.LenderAssignService;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bharatpe.lending.constant.LendingConstants.PAYTM;
import static com.bharatpe.lending.constant.LendingConstants.TOPUP_PILOT_IDENTIFIER;
import static com.bharatpe.lending.enums.Lender.*;
import static com.bharatpe.lending.enums.LoanStatus.ACTIVE;
import static com.bharatpe.lending.enums.LoanStatus.DECEASED;
import static com.bharatpe.lending.enums.SettlementDetailsStatus.INIT;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ONE_LMS;
import static com.bharatpe.lending.service.impl.LenderAssignService.topupLenderMapper;

@Service
@Slf4j
public class MerchantLoansService {

    private final Logger logger = LoggerFactory.getLogger(MerchantLoansService.class);

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingLedgerSlaveDao lendingLedgerSlaveDao;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    private LendingApplicationDaoSlave lendingApplicationDaoSlave;

    @Autowired
    LoanDpdDao loanDpdDao;

    @Autowired
    LoanDpdDaoSlave loanDpdDaoSlave;

    @Autowired
    LenderAssignService lenderAssignService;

    @Autowired
    LendingEligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    EnachHandler enachHandler;

    @Value("${renach.rollout.date}")
    String renachRolloutDate;
    @Value("${round.down.eligible.lenders:TRILLIONLOANS}")
    private List<String> roundDownEligibleLenders;


    @Autowired
    LoanPaymentOrderSlaveDao loanPaymentOrderSlaveDao;

    @Autowired
    LendingIoHalfTopupDao lendingIoHalfTopupDao;

    @Autowired
    PhonebookHandler phonebookHandler;

    @Autowired
    LendingContactSyncAuditDao lendingContactSyncAuditDao;

    @Value("${topup.loan.enabled:false}")
    Boolean isTopUpEnabled;

    @Autowired
    LendingCache lendingCache;

    @Value("${due.amount.caching.window:60}")
    int dueAmountCachingWindow;

    @Value("${cc.due.amount.caching.window:60}")
    int ccDueAmountCachingWindow;

    @Value("${gold.loan.due.amount.caching.window:60}")
    int goldLoanDueAmountCachingWindow;

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Autowired
    MerchantService merchantService;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    FunnelService funnelService;

    @Value("${topup.rollout.percent:10}")
    Integer rolloutTopupPercent;


    @Value("${topup.on.tl.tl.rollout.percent:10}")
    Integer topupOnTltoTlRolloutPercent;

    @Value("${whitelisted.topup.lenders}")
    String topupLenders;

    @Value(("${pilot.test.enabled:true}"))
    Boolean pilotTestEnabled;

    @Autowired
    LmsFieldValuesDao lmsFieldValuesDao;

    @Autowired
    LendingEDIScheduleQueryDao lendingEDIScheduleQueryDao;

    @Autowired
    LendingPrePaymentSlaveDao lendingPrePaymentSlaveDao;

    @Autowired
    LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;

    @Autowired
    AutoPayUPISlaveDao autoPayUPISlaveDao;

    @Autowired
    PenaltyFeeConfigDaoSlave penaltyFeeConfigDaoSlave;

    @Autowired
    LenderTopupEligibilityDao lenderTopupEligibilityDao;

    @Value("${abfl.topup.rollout.percent}")
    Integer abflTopupRolloutPercent;

    @Value("${abfl.topup.rejection.banner.tat:5}")
    Long abflTopupRejectionBannerTat;

    @Autowired
    PenalChargesDao penalChargesDao;

    @Value("${trillion.topup.rollout.percent:1}")
    Integer trillionTopupRolloutPercent;

    @Value("${trillion.topup.rejection.banner.tat:5}")
    Long trillionTopupRejectionBannerTat;

    @Autowired
    LendingApplicationLenderDetailsDaoSlave lendingApplicationLenderDetailsDaoSlave;

    @Autowired
    LendingLenderPricingDao lendingLenderPricingDao;

    @Value("${topup.min.qrpaidRatio:40}")
    Double topupMinQrPaidRatio;

    @Value("${piramal.topup.rollout.percent:}")
    Integer piramalTopupRolloutPercent;

    @Value(("${topup.pilot.run.enabled.lenders:ABFL}"))
    String topupPilotRunEnabledLenders;

    @Autowired
    ExcessNachService excessNachService;

    @Autowired
    private SettlementDetailsDao settlemetDetailsDao;

    static List<String> LIQUILOANS_TOPUP_LENDERS = Arrays.asList("LIQUILOANS_P2P","LIQUILOANS_NBFC","LIQUILOANS_P2P_OF");

    static List<String> allowedRiskGroupsStp = Arrays.asList("R1", "R2");

    private final DecimalFormat df = new DecimalFormat("#.##");

    @Value("${piramal.topup.rejection.banner.tat:5}")
    Long piramalTopupRejectionBannerTat;

    @Value("${topup.disabled.startTime:22:00}")
    String topupDisabledStartTimeString;

    @Value("${topup.disabled.endTime:10:00}")
    String topupDisabledEndTimeString;

    @Lazy
    @Autowired
    private LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    private MerchantLoanServiceHelper merchantLoanServiceHelper;

    @Value("${piramal.max.irr:36.0}")
    Double piramalMaxIrr;

    @Value("${piramal.max.apr:48.0}")
    Double piramalMaxApr;

    @Value("${piramal.topup.max.current.dpd:1}")
    Long piramalTopupMaxCurrentDpd;

    @Value("${ll.balance.transfer.loan.current.dpd.threshold:0}")
    Integer llBalanceTransferLoanCurrentDpdThreshold;

    @Value("${ll.balance.rejection.banner.tat:1440}")
    Long llBalanceRejectionBannerTat;

    @Value("${ll.balance.bre.hard.reject.enabled:true}")
    Boolean llBalanceBreHardRejectEnabled;

    @Value("${half.io.topup.enabled:false}")
    private boolean halfAndIoTopupEnabled;

    @Value("${merchant.loan.v2.enabled:0}")
    private Integer merchantLoanV2Enabled;

    private static final List<String> TOPUP_REJECTION_ENABLED_LENDERS = Arrays.asList(
            LIQUILOANS_P2P.name(),LIQUILOANS_P2P_OF.name(), ABFL.name(), TRILLIONLOANS.name(), PIRAMAL.name());


    @Value("${pricing.experiment.enable:false}")
    boolean pricingExpEnabled;

    @Autowired
    PricingExperimentDao pricingExperimentDao;

    @Autowired
    LoanDisplayService loanDisplayService;

    public LendingActiveLoansResponseDTO getActiveLoans(Long merchantId, Long merchantStoreId) {
        LendingActiveLoansResponseDTO responseDTO = new LendingActiveLoansResponseDTO();
        List<LendingPaymentSchedule> activeLoans = fetchLendingPaymentSchedule(merchantId, merchantStoreId, "ACTIVE");
        if (activeLoans == null || activeLoans.isEmpty()) {
            logger.info("No active loans found for merchantId: {}, merchantStoreId: {}", merchantId, merchantStoreId);
            responseDTO.setActiveLoans(Collections.emptyList());
            responseDTO.setMessage("No Active Loans Found");
            responseDTO.setSuccess(false);
        } else {
            logger.info("{} active loans found for merchantId: {}, merchantStoreId: {}", activeLoans.size(), merchantId, merchantStoreId);
            responseDTO.setActiveLoansFromLendingPaymentSchedule(activeLoans);
            responseDTO.setMessage("Successfully fetched Active Loans");
            responseDTO.setSuccess(true);
        }
        return responseDTO;
    }

    private List<LendingPaymentSchedule> fetchLendingPaymentSchedule(Long merchantId, Long merchantStoreId, String status) {
        if (merchantStoreId != null) {
            return lendingPaymentScheduleDao.findByMerchantIdAndMerchantStoreIdAndStatus(merchantId, merchantStoreId,
              status);
        }
        return lendingPaymentScheduleDao.findByMerchantIdAndStatusList(merchantId, status);
    }

    private List<LendingPaymentScheduleSlave> fetchLendingPaymentScheduleSlave(Long merchantId, Long merchantStoreId, String status) {
        if (merchantStoreId != null) {
            return lendingPaymentScheduleDaoSlave.findByMerchantIdAndMerchantStoreIdAndStatus(merchantId, merchantStoreId,
              status);
        }
        return lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatusList(merchantId, status);
    }

    public LendingPaymentScheduleStatusDTO firstLoanStatus(Long merchantId, Long merchantStoreId) {
        final LendingPaymentScheduleSlave loan =
          lendingPaymentScheduleDaoSlave.findTop1ByMerchantIdAndMerchantStoreId(merchantId, merchantStoreId);

        if (Objects.nonNull(loan))
            return LendingPaymentScheduleStatusDTO.builder().id(loan.getId()).status(loan.getStatus()).build();

        return null;
    }

    public LendingPaymentScheduleStatusDTO checkLoanStatus(Long merchantId, Long merchantStoreId) {
        LendingPaymentScheduleSlave loan;
        if (merchantStoreId != null) {
            loan = lendingPaymentScheduleDaoSlave.findTop1ByMerchantIdAndMerchantStoreId(merchantId, merchantStoreId);
        } else {
            loan = lendingPaymentScheduleDaoSlave.findTop1ByMerchantId(merchantId);
        }

        if (Objects.nonNull(loan))
            return LendingPaymentScheduleStatusDTO.builder().id(loan.getId()).status(loan.getStatus()).build();

        return null;
    }

    public List<LoansHistoryResponseDTO> getLoansHistory(Long merchantId, Long merchantStoreId) {
        final List<LendingPaymentScheduleSlave> loans =
          lendingPaymentScheduleDaoSlave.findByMerchantIdAndMerchantStoreId(merchantId, merchantStoreId);

        List<LoansHistoryResponseDTO> loansHistory = new ArrayList<>();

        for (LendingPaymentScheduleSlave loan : loans) {
            loansHistory.add(createLoanHistoryDTO(loan));
        }

        return loansHistory;
    }

    public LoanDetailsAndStatementDTO getLoanDetailsAndStatement(Long merchantId, Long merchantStoreId) {
        return LoanDetailsAndStatementDTO.builder()
          .loanDetails(getActiveLoanDetails(merchantId, merchantStoreId))
          .loanStatement(getLoanStatement(merchantId, merchantStoreId)).build();
    }

    private LendingPaymentScheduleDetailsDTO getActiveLoanDetails(Long merchantId, Long merchantStoreId) {
        final LendingPaymentScheduleSlave activeLoan = lendingPaymentScheduleDaoSlave.findTop1ByMerchantIdAndMerchantStoreIdAndStatusOrderByIdDesc(merchantId,
          merchantStoreId, "ACTIVE");

        if (Objects.nonNull(activeLoan))
            return LendingPaymentScheduleDetailsDTO.builder()
              .loanId(activeLoan.getId())
              .loanAmount(activeLoan.getLoanAmount())
              .paidAmount(activeLoan.getPaidAmount())
              .totalPayableAmount(activeLoan.getTotalPayableAmount())
              .ediAmount(activeLoan.getEdiAmount())
              .ediCount(activeLoan.getEdiCount())
              .build();

        return null;
    }

    private List<LendingPaymentScheduleDaoSlave.LoanStatementDTO> getLoanStatement(Long merchantId, Long merchantStoreId) {
        return lendingPaymentScheduleDaoSlave.getLoanStatement(merchantId,
          merchantStoreId);
    }

    private LoansHistoryResponseDTO createLoanHistoryDTO(LendingPaymentScheduleSlave lendingPaymentScheduleSlave) {
        Float tenure = getTenure(lendingPaymentScheduleSlave.getEdiCount());
        Double interestRate =  calculateInterestRate(lendingPaymentScheduleSlave, tenure);

        return LoansHistoryResponseDTO.builder()
          .loanId(lendingPaymentScheduleSlave.getId())
          .loanAmount(lendingPaymentScheduleSlave.getLoanAmount())
          .status(lendingPaymentScheduleSlave.getStatus())
          .startDate(lendingPaymentScheduleSlave.getStartDate())
          .closingDate(lendingPaymentScheduleSlave.getClosingDate())
          .tentativeClosingDate(lendingPaymentScheduleSlave.getTentativeClosingDate())
          .interestRate(interestRate)
          .tenure(tenure)
          .build();
    }

    private Float getTenure(Integer ediCount) {
        final LendingCategories lendingCategory = lendingCategoryDao.findTop1ByPayableDays(ediCount);
        if (Objects.nonNull(lendingCategory))
            return lendingCategory.getTenureMonths();

        return null;
    }

    private Double calculateInterestRate(LendingPaymentScheduleSlave lendingPaymentScheduleSlave, Float tenure) {
        Double interestRate = null;
        if (Objects.nonNull(tenure) & tenure > 0) {
            interestRate = (((lendingPaymentScheduleSlave.getTotalPayableAmount()/lendingPaymentScheduleSlave.getLoanAmount()) - 1) / tenure) * 100;
            interestRate = Double.valueOf(df.format(interestRate));
        }
        return interestRate;
    }


    public long calculateTimeDiff(Date createdMandateDate) {
        log.info("createdMandateDate is {}", createdMandateDate);
        SimpleDateFormat format = new SimpleDateFormat("yy/MM/dd HH:mm:ss");
        long diffMinutes=0l;
        Date date = new Date();
        log.info("date is {}", date);
        format.format(date);
        String currentDateTime = format.format(date);

        Date d1 = null;
        try {
            d1 = format.parse((currentDateTime));

            log.info("d1 {}", d1);
            long diff;
            diff = createdMandateDate.getTime() - d1.getTime();
            if (diff<0)
            {
                diff = Math.abs(diff);
            }
            log.info("diff is {}", diff);
            diffMinutes = diff / (60 * 1000);
            log.info("diff minutes is {}", diffMinutes);
            return diffMinutes;

        }
        catch (ParseException e) {
            log.error("e is {}", e);
        }
        return diffMinutes;
    }
    public LendingMerchantLoansResponseDTO getMerchantLoansV2(String token, Long merchantId) {
        logger.info("started merchant loan request processing for merchant id: {}", merchantId);
        long startTime = System.currentTimeMillis();
        LendingMerchantLoansResponseDTO responseDTO = new LendingMerchantLoansResponseDTO();

        List<LendingPaymentScheduleSlave> merchantLoans = lendingPaymentScheduleDaoSlave.findByMerchantIdAndCreditLoan(merchantId, false);
        if (CollectionUtils.isEmpty(merchantLoans)) {
            logger.info("No payment schedule entry found for merchantId: {}", merchantId);
            responseDTO.setAccountDetails(loanUtil.getAccountDetails(merchantId));
            responseDTO.setLoans(Collections.emptyList());
            responseDTO.setMessage("No merchant loans found");
            responseDTO.setSuccess(false);
            processOneLmsLoan(token, merchantId, responseDTO);
            return responseDTO;
        }
        CompletableFuture<BankAccountDetails> bankAccountDetailsCompletableFuture = loanUtil.getAccountDetailsAsync(merchantId);
        List<Long> loanIds = merchantLoans.stream()
                .map(LendingPaymentScheduleSlave::getId)
                .collect(Collectors.toList());
        Map<Long, LendingApplicationSlave> applicationeMap = lendingApplicationDaoSlave.findByIds(loanIds).stream().
                collect(Collectors.toMap(LendingApplicationSlave::getId, Function.identity()));
        responseDTO.setLoansFromLendingPaymentSchedule(merchantLoans, applicationeMap);
        merchantLoanServiceHelper.prepareAllLoanDetails(merchantId, responseDTO, loanIds);
        LendingPaymentScheduleSlave activeLendingPaymentSchedule =merchantLoans.stream()
                .filter(loan-> ACTIVE.name().equals(loan.getStatus()) || DECEASED.name().equals(loan.getStatus())).findFirst()
                .orElse(null);
        setActiveLoanDetailsInResponse(merchantId, activeLendingPaymentSchedule, responseDTO);

        LendingMerchantLoansResponseDTO.Loan activeLoan = responseDTO.getLoans().stream()
                .filter(loan -> ACTIVE.name().equals(loan.getStatus()))
                .findFirst()
                .orElse(null);

        BankAccountDetails bankAccountDetails = loanUtil.fetchAccountDetailsFromFuture(merchantId, bankAccountDetailsCompletableFuture);
        responseDTO.setAccountDetails(bankAccountDetails);
        if(activeLoan!=null){
            responseDTO.setShowChangeBankAccountBanner(showChangeBankAccountBanner(responseDTO.getAccountDetails(), merchantId));
            responseDTO.setShowRenachBanner(showRenachBanner(merchantId, activeLoan.getLender(), responseDTO.getShowChangeBankAccountBanner()));
        }
        processOneLmsLoan(token, merchantId, responseDTO);
        logger.info("merchant loan request processing completed for merchant id: {}, with time taken: {}",
                merchantId, System.currentTimeMillis()-startTime);
        return responseDTO;
    }

    private void setActiveLoanDetailsInResponse(Long merchantId, LendingPaymentScheduleSlave activeLendingPaymentSchedule, @NotNull LendingMerchantLoansResponseDTO responseDTO) {
        if (Objects.isNull(activeLendingPaymentSchedule)) {
            return;
        }
        double penaltyConfigVersion = Lender.TRILLIONLOANS.name().equals(activeLendingPaymentSchedule.getNbfc()) ? 1D : 2D;
        List<PenaltyFeeConfigSlave> penaltyFeeConfigSlaves = penaltyFeeConfigDaoSlave
                .findByVersionAndStatusAndLenderOrderByMinAmountAsc(penaltyConfigVersion, true, activeLendingPaymentSchedule.getNbfc());
        responseDTO.setPenaltyConfig(loanUtil.getPenaltyConfig(penaltyFeeConfigSlaves));
        Date date = new Date();
        responseDTO.setEdiStarted(date.after(activeLendingPaymentSchedule.getStartDate()));
        if (!date.after(activeLendingPaymentSchedule.getStartDate())
                && PerpetualDpdAdjusted.Y.name().equalsIgnoreCase(activeLendingPaymentSchedule.getPerpetualDpdAdjusted())) {
            responseDTO.setPerpetualDpdRestrictPgPayment(Boolean.TRUE);
        }
        List<LoanPaymentOrderSlave> loanPaymentOrderList = loanPaymentOrderSlaveDao.findRecentTransactions(activeLendingPaymentSchedule.getId(), activeLendingPaymentSchedule.getMerchantId());
        responseDTO.setRepaymentDetails(loanUtil.getRepaymentDetails(loanPaymentOrderList));
        try {
            List<LoanEligibilityDTO> loans = topupLoan(activeLendingPaymentSchedule, false);
            log.info("calculated topup_loan eligibility: {}", loans);
            setTopupDetails(merchantId, loans, responseDTO, activeLendingPaymentSchedule);
            if(TOPUP_REJECTION_ENABLED_LENDERS.contains(activeLendingPaymentSchedule.getNbfc())) {
                responseDTO.setTopupRejected(checkForTopupRejection(activeLendingPaymentSchedule.getMerchantId(), activeLendingPaymentSchedule.getNbfc()));
            }
            responseDTO.setTimeBasedTopupDisabled(loanUtil.isTimeBasedTopupDisabled(activeLendingPaymentSchedule.getNbfc()));
            responseDTO.setIsPanNsdlVerified(false); // setting this false to open pan pin page every time.

        } catch (Exception exception) {
            logger.error("Exception while calculating TOPUP loan for merchant:{}, message: {}, stack_trace: {}",
                    merchantId, exception.getMessage(), Arrays.asList(exception.getStackTrace()));
        }
        if (halfAndIoTopupEnabled && baseChecksForHalfAndIOEdi(activeLendingPaymentSchedule, responseDTO)) {
            setHalfAndIOTopupLoan(merchantId, activeLendingPaymentSchedule, responseDTO);
        }
    }


    public LendingMerchantLoansResponseDTO getMerchantLoans(String token, Long merchantId) {
        if(easyLoanUtil.percentScaleUp(merchantId, merchantLoanV2Enabled)){
            return getMerchantLoansV2(token, merchantId);
        }
        long startTime = System.currentTimeMillis();
        log.info("started processing merchant loan request with old flow for merchant id: {}",merchantId);
        LendingMerchantLoansResponseDTO responseDTO = new LendingMerchantLoansResponseDTO();
        responseDTO.setTopup(Boolean.FALSE);
        List<LendingPaymentScheduleSlave> merchantLoans = lendingPaymentScheduleDaoSlave.findByMerchantIdAndCreditLoan(merchantId, false); // This is for old flow where lms_source is null
        responseDTO.setAccountDetails(loanUtil.getAccountDetails(merchantId));
        if (merchantLoans == null || merchantLoans.isEmpty()) {
            logger.info("No loans found for merchantId: {}", merchantId);
            responseDTO.setLoans(Collections.emptyList());
            responseDTO.setMessage("No merchant loans found");
            responseDTO.setSuccess(false);
        } else {
            logger.info("{} loans found from existing flow for merchantId: {}", merchantLoans.size(), merchantId);
            responseDTO.setLoansFromLendingPaymentSchedule(merchantLoans, new HashMap<>());

            for (LendingMerchantLoansResponseDTO.Loan loan : responseDTO.getLoans()) {
                LendingLedgerSlave lendingLedger = lendingLedgerSlaveDao.findLastPaymentEntryByMerchantAndLoan(merchantId, loan.getLoanId());
                if(ACTIVE.name().equals(loan.getStatus()) || DECEASED.name().equals(loan.getStatus())) {
                    LendingLedgerSlave lastEdiCreated = lendingLedgerSlaveDao.findLastEDIDueEntryByMerchantAndLoan(merchantId, loan.getLoanId());
                    if (!ObjectUtils.isEmpty(lastEdiCreated)) {
                        LocalDate lastEdiDate = LocalDate.parse(new SimpleDateFormat("yyyy-MM-dd").format(lastEdiCreated.getCreatedAt()));
                        loan.setTodayEdi(lastEdiDate.equals(LocalDate.now()) ? Math.abs(lastEdiCreated.getAmount()) : 0);
                    }
                    if (!ObjectUtils.isEmpty(loan.getDueAmount()) && !ObjectUtils.isEmpty(loan.getTodayEdi())) {
                        if (loan.getDueAmount() > loan.getTodayEdi()) {
                            loan.setPendingEdi(ObjectUtils.isEmpty(lastEdiCreated) ? 0 : loan.getDueAmount() - Math.abs(loan.getTodayEdi()));
                        } else {
                            loan.setPendingEdi(0D);
                        }
                    }
                    Double excessCollectionBalance = excessNachService.getExcessCollectionBalanceAmount(merchantId, loan.getLoanId());
                    if (excessCollectionBalance == null) excessCollectionBalance = 0.0;

                    loan.setTotalDue(loan.getDueAmount() + loan.getDuePenalty());
                    loan.setTotalExcessBalance(Math.min(excessCollectionBalance, loan.getTotalDue()));
                    loan.setNetPayable(Math.max(loan.getTotalDue() - loan.getTotalExcessBalance(), 0));
                }
                loan.setDpd(LoanUtil.calculateDPD(loan.getEdiAmount(), loan.getDueAmount()));
                if (lendingLedger != null) {
                    loan.setLastEdiPaid(lendingLedger.getAmount());
                } else {
                    loan.setLastEdiPaid(0D);
                }
                LendingEDIScheduleQuery lendingEDISchedule = lendingEDIScheduleQueryDao.getLatestByLoanId(loan.getLoanId());
                if (lendingEDISchedule != null) {
                    loan.setShowCustomAmount(true);
                }
                LendingPrepaymentSlave lendingPrepayment = lendingPrePaymentSlaveDao.findByMerchantIdAndLoanId(merchantId, loan.getLoanId());
                merchantLoanServiceHelper.setLoanPrepaymentData(loan, lendingPrepayment);

                PenalCharges penalCharges = penalChargesDao.findByLoanId(loan.getLoanId());
                double duePenalty = Objects.nonNull(penalCharges) ? penalCharges.getDuePenalty() : (Objects.nonNull(loan.getDuePenalty()) ? loan.getDuePenalty() : 0);
                loan.setDuePenalty(duePenalty);
                loan.setNachBounceAmount(Objects.nonNull(penalCharges) ? penalCharges.getDueNachBounce() : 0);

                if (loan.getStatus().equals("ACTIVE")) {
                    responseDTO.setShowChangeBankAccountBanner(showChangeBankAccountBanner(responseDTO.getAccountDetails(), merchantId));
                    LendingPullPaymentSlave pullPayment = lendingPullPaymentDaoSlave.findTop1ByMerchantIdAndModeOrderByIdDesc(merchantId, "AUTOPAYUPI");
                    merchantLoanServiceHelper.setLoanPresentmentData(loan, pullPayment);

                    log.info("loan application id is loan.getApplicationId{}", loan.getApplicationId());
                    Optional<AutoPayUPISlave> autoPayUPI = autoPayUPISlaveDao.findTop1ByMerchantIdAndApplicationIdOrderByIdDesc(merchantId, loan.getApplicationId());
                    if (autoPayUPI.isPresent()) {
                        loan.setAutoPayMandateStatus(autoPayUPI.get().getStatus());
                        loan.setMandateRegisterId(autoPayUPI.get().getOrderId());
                    }

                    Optional<LoanDpdSlave> loanDpd = loanDpdDaoSlave.findTop1ByLoanIdOrderByIdDesc(loan.getLoanId());
                    if (!ObjectUtils.isEmpty(loanDpd))
                    {
                        log.info("loan dpd{} for merchant id is {}",loanDpd.get(), merchantId);
                    if (loanDpd.isPresent() && loanDpd.get().getDpd()<3 && loanDpd.get().getDpd()!=0) {
                        log.info("merchant id is {}", merchantId);
                        if (easyLoanUtil.percentScaleUp(merchantId, apiGatewayService.upiPercent)
                                && "LDC".equalsIgnoreCase(loan.getLender())) {
                            loan.setAutoPayEligibility(Boolean.TRUE);
                        } else {
                            loan.setAutoPayEligibility(Boolean.FALSE);
                        }
                    } else {
                        loan.setAutoPayEligibility(Boolean.FALSE);
                    }
                    }
                    else
                        loan.setAutoPayEligibility(Boolean.FALSE);

//                    Set settlement details if settlement initiated
                    try {
                        if (loan.isSettlementInitiated()) {
                            SettlementDetails settlementDetails = settlemetDetailsDao.findByLoanIdAndStatus(loan.getLoanId(), INIT.name());
                            loan.setSettlementAmountOffer(settlementDetails.getSettlementAmountOffer());
                            loan.setSettlementExpiryDate(settlementDetails.getSettlementExpiryDate());
                        }
                    } catch (Exception ex) {
                        logger.error("Multiple settlement initiated, Stack: {}", Arrays.asList(ex.getStackTrace()));
                        loan.setSettlementInitiated(Boolean.FALSE);
                    }
                    responseDTO.setShowRenachBanner(showRenachBanner(merchantId, loan.getLender(), responseDTO.getShowChangeBankAccountBanner()));
                }
                LendingApplicationLenderDetailsSlave lendingApplicationLenderDetailsSlave = lendingApplicationLenderDetailsDaoSlave.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(loan.getApplicationId(),"ACTIVE",loan.getLender());
                if(!ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave)){
                    loan.setAnnualRoi(lendingApplicationLenderDetailsSlave.getAnnualRoi());
                }
                Double nachBounce = loanUtil.getNachBounceAmountConfig(lendingApplicationLenderDetailsSlave);
                responseDTO.setConfigNachBounceAmount(nachBounce);
            }

            LendingPaymentScheduleSlave lendingPaymentSchedule = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(merchantId, Arrays.asList("ACTIVE", "DECEASED"));
            if (Objects.nonNull(lendingPaymentSchedule)) {
                double penaltyConfigVersion = Lender.TRILLIONLOANS.toString().equals(lendingPaymentSchedule.getNbfc()) ? 1D : 2D;
                List<PenaltyFeeConfigSlave> penaltyFeeConfigSlaves = penaltyFeeConfigDaoSlave.findByVersionAndStatusAndLenderOrderByMinAmountAsc
                        (penaltyConfigVersion, true, lendingPaymentSchedule.getNbfc());
                responseDTO.setPenaltyConfig(loanUtil.getPenaltyConfig(penaltyFeeConfigSlaves));
            }
            if (lendingPaymentSchedule != null) {
                Date date = new Date();
                if (date.after(lendingPaymentSchedule.getStartDate())) {
                    responseDTO.setEdiStarted(Boolean.TRUE);
                } else {
                    if(PerpetualDpdAdjusted.Y.name().equalsIgnoreCase(lendingPaymentSchedule.getPerpetualDpdAdjusted())){
                        responseDTO.setPerpetualDpdRestrictPgPayment(Boolean.TRUE);
                    }
                    responseDTO.setEdiStarted(Boolean.FALSE);
                }
                List<LoanPaymentOrderSlave> loanPaymentOrderList = loanPaymentOrderSlaveDao.findRecentTransactions(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getMerchantId());
                responseDTO.setRepaymentDetails(loanUtil.getRepaymentDetails(loanPaymentOrderList));
                try {
                    List<LoanEligibilityDTO> loans = topupLoan(lendingPaymentSchedule, false);
                    log.info("calculated topup_loan eligibility: {}", loans);
                    setTopupDetails(merchantId, loans, responseDTO, lendingPaymentSchedule);
                    if(TOPUP_REJECTION_ENABLED_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
                        responseDTO.setTopupRejected(checkForTopupRejection(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc()));
                    }
                    if(PIRAMAL.name().equals(lendingPaymentSchedule.getNbfc())) {
                        LocalTime now = LocalTime.now();
                        LocalTime topupDisabledStartTime = LocalTime.parse(topupDisabledStartTimeString);
                        LocalTime topupDisabledEndTime = LocalTime.parse(topupDisabledEndTimeString);
                        Boolean isTimeBasedTopupDisabled = now.isAfter(topupDisabledStartTime) || now.isBefore(topupDisabledEndTime);
                        responseDTO.setTimeBasedTopupDisabled(isTimeBasedTopupDisabled);
                    }
                    responseDTO.setIsPanNsdlVerified(false); // setting this false to open pan pin page every time.

                } catch (Exception e) {
                    logger.error("Exception while calculating TOPUP loan for merchant:{}", merchantId, e);
                }
                if (baseChecksForHalfAndIOEdi(lendingPaymentSchedule, responseDTO)) {
                    setHalfAndIOTopupLoan(merchantId, lendingPaymentSchedule, responseDTO);
                }
            }
        }
        processOneLmsLoan(token, merchantId, responseDTO);
        log.info("merchant loan request processing completed with old flow for merchant id: {} with time taken: {}",
                 merchantId, System.currentTimeMillis() - startTime);
        return responseDTO;
    }

    public TopupEligibilityResponseData getTopupEligibility(String token, Long merchantId, String clientIdentifier) {
        TopupEligibilityResponseData response = new TopupEligibilityResponseData();
        LendingPaymentScheduleSlave lendingPaymentSchedule = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(merchantId, Arrays.asList("ACTIVE", "DECEASED"));
        if (lendingPaymentSchedule == null) {
            log.info("No active loan found for merchantId: {}", merchantId);
            response.setMessage("No active loan found for the merchant");
            return response;
        }
        List<LoanEligibilityDTO> loans = topupLoanV2(lendingPaymentSchedule, false, clientIdentifier);
        setTopupDetails(merchantId, loans, response, lendingPaymentSchedule);
        // TODO merge 1LMS topup eligibility, once topup is started for 1LMS
        return response;
    }

    private void setTopupDetails(Long merchantId, @NotNull List<LoanEligibilityDTO> loans, TopupEligibilityResponseData responseDTO, LendingPaymentScheduleSlave lendingPaymentSchedule) {
        List<LoanEligibilityDTO> rejectedLoans = loans.stream()
                .filter(dto -> BooleanUtils.isTrue(dto.getIsRejected()))
                .collect(Collectors.toList());
        log.info("Rejected loans: {}", rejectedLoans);
        if(!rejectedLoans.isEmpty() && rejectedLoans.get(0).getIsRejected() && !StringUtils.isEmpty(rejectedLoans.get(0).getRejectionReason())){
            responseDTO.setIsRejected(true);
            responseDTO.setRejectionReason(loans.get(0).getRejectionReason());
        }
        List<LoanEligibilityDTO> topUpLoans = loans.stream()
                .filter(dto -> BooleanUtils.isNotTrue(dto.getIsRejected()))
                .collect(Collectors.toList());
        log.info("Topup loans eligibility for merchant: {} is: {}",merchantId, topUpLoans);
        if (!topUpLoans.isEmpty()) {
            responseDTO.setEligibility(loans);
            responseDTO.setTopup(Boolean.TRUE);
            responseDTO.setTopupLender(topupLenderMapper(lendingPaymentSchedule.getNbfc()));
            funnelService.submitEventV3(merchantId, null, null, FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.TOPUP_ELIGIBLE, null, LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
    }

    private void setHalfAndIOTopupLoan(Long merchantId, LendingPaymentScheduleSlave lendingPaymentSchedule, LendingMerchantLoansResponseDTO responseDTO) {
        logger.info("Base checks passed for Half/IO Loan for loanId:{}", lendingPaymentSchedule.getId());
        LendingIoHalfTopup lendingIoHalfTopup = lendingIoHalfTopupDao.findByLoanId(lendingPaymentSchedule.getId());
        LoanCalculationUtil.LoanBreakupDetail loanBreakupDetail;
        if (lendingIoHalfTopup != null && LoanType.IO_TOPUP.name().equals(lendingIoHalfTopup.getLoanType())) {
            logger.info("merchant:{} eligible for io loan", merchantId);
            loanBreakupDetail = calculateHalfIOLoan(lendingPaymentSchedule, merchantId, LoanType.IO_TOPUP);
            responseDTO.setIoLoan(lendingPaymentSchedule, loanBreakupDetail);
        } else if (lendingIoHalfTopup != null && LoanType.HALF_TOPUP.name().equals(lendingIoHalfTopup.getLoanType())) {
            logger.info("merchant:{} eligible for half loan", merchantId);
            loanBreakupDetail = calculateHalfIOLoan(lendingPaymentSchedule, merchantId, LoanType.HALF_TOPUP);
            responseDTO.setHalfLoan(lendingPaymentSchedule, loanBreakupDetail);
        } else {
            logger.info("Entry not found in lending_io_half_topup for merchant:{}", merchantId);
        }
    }

    private void processOneLmsLoan(String token, Long merchantId, LendingMerchantLoansResponseDTO responseDTO) {
        log.info("responseDTO size before merging from new 1LMS flow : {}", responseDTO.getLoans().size());
        LendingMerchantLoansResponseDTO responseDTOFromOneLms = new LendingMerchantLoansResponseDTO();
        List<LendingPaymentSchedule> loansFromOneLms = lendingPaymentScheduleDao.findByMerchantIdAndLmsSource(merchantId, ONE_LMS); // This is for new flow where lms_source is "1LMS"
        if (ObjectUtils.isEmpty(loansFromOneLms)) {
            log.info("No loans found from new 1LMS flow for merchantId: {}", merchantId);
            responseDTOFromOneLms.setLoans(Collections.emptyList());
        } else {
            log.info("{} loans found from new 1LMS flow for merchantId: {}", loansFromOneLms.size(), merchantId);
            responseDTOFromOneLms.updateTotalAmounts(loansFromOneLms);
        }

        log.info("MerchantLoans from new 1LMS flow with size {} for merchantId: {}", loansFromOneLms.size(), merchantId);

        if (!ObjectUtils.isEmpty(loansFromOneLms)) {
            log.info("MerchantLoans from new 1LMS flow with size {} for merchantId: {}", loansFromOneLms.size(), merchantId);
            responseDTOFromOneLms = loanDisplayService.setLendingMerchantLoansForOneLms(token, responseDTOFromOneLms, loansFromOneLms, merchantId);
        }

        log.info("responseDTO size before merging with new 1LMS flow : {}", responseDTO.getLoans().size());

        loanDisplayService.updateResponseDto(responseDTO, responseDTOFromOneLms);

        log.info("responseDTO size after merging with old and new 1LMS flow : {}", responseDTO.getLoans().size());

        if (!ObjectUtils.isEmpty(responseDTO.getLoans())) {
            responseDTO.getLoans().sort(Comparator.comparing(LendingMerchantLoansResponseDTO.Loan::getLoanId, Comparator.reverseOrder()));
            responseDTO.setMessage("Successfully fetched merchant loans");
            responseDTO.setSuccess(true);
        } else {
            log.info("No loans found from old and new 1LMS flow for merchantId: {}", merchantId);
            responseDTO.setLoans(Collections.emptyList());
            responseDTO.setMessage("No merchant loans found");
            responseDTO.setSuccess(false);
        }
    }

    private static void setLoanPenaltyCharges(LendingMerchantLoansResponseDTO.Loan loan, PenalCharges penalCharges) {
        loan.setDuePenalty(Objects.nonNull(penalCharges) ? penalCharges.getDuePenalty() : loan.getDuePenalty());
        loan.setNachBounceAmount(Objects.nonNull(penalCharges) ? penalCharges.getDueNachBounce() : 0);
    }



    private Boolean checkForTopupRejection(Long merchantId, String parentLender) {
        try {
            LendingApplication prevApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if(!ObjectUtils.isEmpty(prevApplication)) {
                if(LoanType.TOPUP.name().equalsIgnoreCase(prevApplication.getLoanType()) && "rejected".equalsIgnoreCase(prevApplication.getStatus())) {
                    log.info("latest application with topup loanType for merchantId : {}", prevApplication);
                    Long minutes = TimeUnit.MINUTES.toMinutes(new Date().getTime() - prevApplication.getUpdatedAt().getTime()) / 60000;
                    if(ABFL.name().equalsIgnoreCase(prevApplication.getLender()) && minutes < abflTopupRejectionBannerTat) {
                        log.info("ABFL topup application rejected for merchantId : {} less than {} minutes ago", merchantId, abflTopupRejectionBannerTat);
                        return Boolean.TRUE;
                    } else if(TRILLIONLOANS.name().equalsIgnoreCase(prevApplication.getLender())) {
                           if(TRILLIONLOANS.name().equalsIgnoreCase(parentLender) && minutes < trillionTopupRejectionBannerTat) {
                               log.info("TRILLIONLOANS topup application rejected for merchantId : {} less than {} minutes ago", merchantId, trillionTopupRejectionBannerTat);
                               return Boolean.TRUE;
                           }
                           if(LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(parentLender) && minutes < llBalanceRejectionBannerTat) {
                               log.info("Liquiloans balance transfer topup application rejected for merchantId : {} less than {} minutes ago", merchantId, llBalanceRejectionBannerTat);
                               return Boolean.TRUE;
                           }
                    } else if(PIRAMAL.name().equalsIgnoreCase(prevApplication.getLender()) && minutes < piramalTopupRejectionBannerTat) {
                        log.info("PIRAMAL topup application rejected for merchantId : {} less than {} minutes ago", merchantId, piramalTopupRejectionBannerTat);
                        return Boolean.TRUE;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.info("Exception in checking topup rejection for merchantId : {}, {}, {}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    public boolean showChangeBankAccountBanner(BankAccountDetails bankAccountDetails, Long merchantId) {

        final List<Long> reNachEnabledMerchants = loanUtil.reNachEnabledMerchants();

        if(reNachEnabledMerchants.contains(merchantId) && !ObjectUtils.isEmpty(bankAccountDetails) && !ObjectUtils.isEmpty(bankAccountDetails) && bankAccountDetails.getBankName().toUpperCase().contains(PAYTM)) {
            logger.info("show bank account change banner to merchant : {} with bankAccountDetails : {}", merchantId, bankAccountDetails);
            return true;
        }

        logger.info("hide bank account change banner for merchant : {} with bankAccountDetails : {}", merchantId, bankAccountDetails);
        return false;
    }

    public boolean showRenachBanner(Long merchantId, String lender, boolean showChangeBankAccountBanner) {

        if (!showChangeBankAccountBanner) {
            final List<Long> reNachEnabledMerchants = loanUtil.reNachEnabledMerchants();
            if (reNachEnabledMerchants.contains(merchantId)) {
                MerchantNachDetailsResponseDTO successEnach = enachHandler.findByMerchantIdAndLender(merchantId, loanUtil.enachServiceLenderMapper(lender));
                if (successEnach != null &&
                  !ObjectUtils.isEmpty(successEnach.getBankName()) && successEnach.getBankName().contains(PAYTM)) {
                    logger.info("show renach banner to merchant : {} with successNach details as when existing nachBank is Paytm : {}", merchantId, successEnach);
                    return true;
                }

                final Date renachRolloutDate = loanUtil.parseRolloutDate(this.renachRolloutDate);

                if (successEnach != null && successEnach.getCreatedAt().before(renachRolloutDate)) {
                    logger.info("show renach banner to merchant : {} with successNach details with rollOut : {}", merchantId, successEnach);
                    return true;
                }
            }
        }

        logger.info("hide renach banner for merchant : {}", merchantId);
        return false;
    }

    //Not using this method because phonebook handler api id deprecated
    @Deprecated
    private Boolean isContactSyncRequired(LendingPaymentScheduleSlave lendingPaymentSchedule) {
        try {
            LendingContactSyncAudit lendingContactSyncAudit = lendingContactSyncAuditDao.findTop1ByMerchantId(lendingPaymentSchedule.getMerchantId());
            if (Objects.nonNull(lendingContactSyncAudit) &&
              lendingContactSyncAudit.getTotalEntries() >= 100 &&
              (float) lendingContactSyncAudit.getNameEntries() / lendingContactSyncAudit.getTotalEntries() >= 0.25 &&
              (float) lendingContactSyncAudit.getMobileEntries() / lendingContactSyncAudit.getTotalEntries() >= 0.25
            ) {
                return false;
            }

            List<PhonebookDTO> phonebook = phonebookHandler.getPhonebook(lendingPaymentSchedule.getMerchantId());
            if (phonebook.isEmpty()) {
                return true;
            }
//            if (LoanUtil.getDateDiffInDays(phonebook.get().getUpdatedAt(), new Date()) > 60) {
//                return true;
//            }
            if (Objects.isNull(lendingContactSyncAudit)) {
                lendingContactSyncAudit = new LendingContactSyncAudit();
                lendingContactSyncAudit.setMerchantId(lendingPaymentSchedule.getMerchantId());
            }
//            String[] s3Url = phonebook.get().getS3URL().split("/");
//            String fileName = s3Url[s3Url.length - 1];
//            logger.info("Filename for loanId: {}, {}", lendingPaymentSchedule.getId(), fileName);
            Long totalEntries = 0l, nameEntries = 0l, mobileEntries = 0l;
            try {
//                InputStream inputStream = s3BucketHandler.getObject(fileName, "merchant-phonebook", "us-west-2");
//                if (ObjectUtils.isEmpty(inputStream)) {
//                    return true;
//                }
//                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
//                String readLine = bufferedReader.readLine();
//                readLine = bufferedReader.readLine();

//                while (Objects.nonNull(readLine)) {
//                    totalEntries++;
//                    logger.info("phonebook for loan id : {}, readline: {}", lendingPaymentSchedule.getId(), readLine);
//                    String[] arr = readLine.split(",");
//                    String name = "";
//                    if (arr.length >= 1) {
//                        name = arr[0];
//                    }
//                    String mobile = "";
//                    if (arr.length >= 2) {
//                        mobile = arr[1];
//                    }
//                    if (!StringUtils.isEmpty(name)) {
//                        nameEntries++;
//                    }
//                    if (!StringUtils.isEmpty(mobile)) {
//                        mobileEntries++;
//                    }
//                    readLine = bufferedReader.readLine();
//                }

                for (PhonebookDTO phonebookDTO : phonebook) {
                    totalEntries++;
                    if (!StringUtils.isEmpty(phonebookDTO.getName())) {
                        nameEntries++;
                    }
                    if (!StringUtils.isEmpty(phonebookDTO.getPhoneNumber())) {
                        mobileEntries++;
                    }
                }


                lendingContactSyncAudit.setMobileEntries(mobileEntries);
                lendingContactSyncAudit.setNameEntries(nameEntries);
                lendingContactSyncAudit.setTotalEntries(totalEntries);
                lendingContactSyncAuditDao.save(lendingContactSyncAudit);
            } catch (Exception ex) {
                logger.error("Error Occured while auditing contact data for loan id : {} {}", lendingPaymentSchedule.getId(), ex);
            }
            if (totalEntries < 100 || (float) nameEntries / totalEntries < 0.25 || (float) mobileEntries / totalEntries < 0.25) {
                return true;
            }
        } catch (Exception ex) {
            logger.error("Exception Occured while checking contact sync required for loan id : {}, {}", lendingPaymentSchedule.getId(), ex);
        }
        return false;
    }

    private LoanCalculationUtil.LoanBreakupDetail calculateHalfIOLoan(LendingPaymentScheduleSlave lendingPaymentSchedule, Long merchantId, LoanType loanType) {
        try {
            int foreclosureAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
            BigDecimal processingFee = loanUtil.getIoHalfPFBD(lendingPaymentSchedule);
            double loanAmount = Math.ceil((foreclosureAmount + processingFee.intValue()) / 1000.0) * 1000;
            int ediPaidCount = (int) Math.ceil(lendingPaymentSchedule.getPaidAmount() / lendingPaymentSchedule.getEdiAmount());
            int ediRemainingCount = lendingPaymentSchedule.getEdiCount() - ediPaidCount;
            if (loanAmount < 10000d || ediRemainingCount < 26) {
                logger.info("loan amount less than 10k for merchant:{} and loanType:{}", merchantId, loanType.name());
                return null;
            }
            logger.info("Calculating " + loanType.name() + " for merchant:{} for amount:{}", merchantId, loanAmount);
            int newEdiCount = calculateNewTenure(ediRemainingCount, loanType);
            if (newEdiCount == 0) {
                return null;
            }
            LendingCategories lendingCategory = lendingCategoryDao.getByMasterCategoryAndPayableDays(loanType.name(), newEdiCount);
            if (lendingCategory == null) {
                logger.error("Lending category not found for loanType:{} and days:{}", loanType.name(), newEdiCount);
                return null;
            }
            Experian experian = experianDao.getByMerchantId(merchantId);
            AvailableLoan availableLoan = new AvailableLoan();
            availableLoan.setAmount(loanAmount);
            LoanCalculationUtil.LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategory, loanType.name());
            breakup.setProcessingFee(processingFee.intValue());
            eligibleLoanDao.deleteByMerchantId(merchantId);
            eligibleLoanDao.deleteCustomOffers(merchantId);
            insertEligibleLoan(merchantId, experian, breakup, lendingCategory);
            return breakup;
        } catch (Exception e) {
            logger.error("Exception calculating half topup loan for merchant:{}", merchantId, e);
        }
        return null;
    }

    private void insertEligibleLoan(Long merchantId, Experian experian, LoanCalculationUtil.LoanBreakupDetail breakup, LendingCategories category) {
        try {
            LendingEligibleLoan eligibleLoan = new LendingEligibleLoan();
            eligibleLoan.setMerchantId(merchantId);
            eligibleLoan.setExperianId(experian != null ? experian.getId() : null);
            eligibleLoan.setTenure(category.getPayableConverter());
            eligibleLoan.setStatus("ACTIVE");
            eligibleLoan.setAmount(breakup.getLoanAmount().doubleValue());
            eligibleLoan.setCategory(category.getCategory());
            eligibleLoan.setEdi(breakup.getEdi());
            eligibleLoan.setIoEdi(breakup.getIoEdi());
            eligibleLoan.setRepayment(breakup.getRepayment());
            eligibleLoan.setLoanConstruct(breakup.getConstruct());
            eligibleLoan.setLoanType(category.getMasterCategory());
            eligibleLoan.setIoEdiDays(breakup.getIoEdiDays());
            eligibleLoanDao.save(eligibleLoan);
        } catch (Exception e) {
            logger.error("Exception while saving eligible loan for merchant:{}", merchantId, e);
        }
    }

    private int calculateNewTenure(Integer ediRemainingCount, LoanType loanType) {
        if (loanType.equals(LoanType.HALF_TOPUP)) {
            if (ediRemainingCount >= 26 && ediRemainingCount <= 38) {
                return 77;
            } else if (ediRemainingCount >= 39 && ediRemainingCount <= 77) {
                return 155;
            } else if (ediRemainingCount >= 78 && ediRemainingCount <= 117) {
                return 234;
            } else if (ediRemainingCount >= 118 && ediRemainingCount <= 155) {
                return 311;
            } else if (ediRemainingCount >= 156 && ediRemainingCount <= 234) {
                return 388;
            }
        } else if (loanType.equals(LoanType.IO_TOPUP)) {
            if (ediRemainingCount >= 26 && ediRemainingCount <= 76) {
                return 77;
            } else if (ediRemainingCount >= 77 && ediRemainingCount <= 154) {
                return 155;
            } else if (ediRemainingCount >= 155 && ediRemainingCount <= 233) {
                return 234;
            } else if (ediRemainingCount >= 234 && ediRemainingCount <= 310) {
                return 311;
            }
        }
        return 0;
    }

    private boolean baseChecksForHalfAndIOEdi(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingMerchantLoansResponseDTO responseDTO) {
        if (responseDTO.getTopup()) {
            return false;
        }
        if (lendingPaymentSchedule.getMerchantId().equals(9319451L) || lendingPaymentSchedule.getMerchantId().equals(5352114L))
            return true;
        try {
            List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
            if (lendingPaymentSchedule.getLoanApplication() != null && topupLoans.contains(lendingPaymentSchedule.getLoanApplication().getLoanType())) {
                logger.info("Previous loan is topup for merchant:{}", lendingPaymentSchedule.getMerchantId());
                return false;
            }
            if (!loanUtil.isEnachDone(lendingPaymentSchedule.getMerchantId())) {
                logger.info("Nach not success for merchant:{}", lendingPaymentSchedule.getMerchantId());
                return false;
            }
            if (LoanUtil.getDateDiffInDays(lendingPaymentSchedule.getCreatedAt(), new Date()) <= 30) {
                return false;
            }
            int ediPaidCount = (int) Math.ceil(lendingPaymentSchedule.getPaidAmount() / lendingPaymentSchedule.getEdiAmount());
            int ediRemainingCount = lendingPaymentSchedule.getEdiCount() - ediPaidCount;
            double foreclosureAmount = (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() - (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0) + (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0));
            foreclosureAmount = Math.ceil(foreclosureAmount / 1000.0) * 1000;
            return foreclosureAmount >= 10000d && ediRemainingCount >= 26;
        } catch (Exception e) {
            logger.error("Exception in half io loans base checks for loanId:{}", lendingPaymentSchedule.getId(), e);
        }
        return false;
    }

    public boolean excludeTopUpBaseChecks(Long merchantId) {
        return loanUtil.isInternalMerchant(merchantId);
    }

    private boolean derogTopUpEnable(Long merchantId) {
        LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        logger.info("DEROG_EFFECTED_MERCHANT_FLOW: merchant_id: {} application_id: {} type: {}", merchantId, lendingApplication.getId(), lendingApplication.getLoanType());
        return !LoanType.TOPUP.name().equals(lendingApplication.getLoanType());
    }

    public List<LoanEligibilityDTO> topupLoanV2(LendingPaymentScheduleSlave lendingPaymentSchedule, boolean createTopupAppCheck, String clientIdentifier) {
        log.info("calculating topup loan eligibility for merchantId: {}", lendingPaymentSchedule.getMerchantId());

        LendingApplication lendingApplication =
                lendingApplicationDao.findByIdAndMerchantId(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getMerchantId());

        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();

        if (lendingApplication == null) {
            log.info("Lending Application not found/topup loan for merchant:{}", lendingPaymentSchedule.getMerchantId());
            return eligiblity;
        }

        try {

            if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {

                if(LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
                    return ExistingTopupRuleEngineV2(lendingPaymentSchedule, lendingApplication, createTopupAppCheck, clientIdentifier);
                }

                double paidRatio = 0d;
                if (lendingPaymentSchedule.getPaidPrinciple() != null && lendingPaymentSchedule.getLoanAmount() != null) {
                    paidRatio = lendingPaymentSchedule.getPaidPrinciple() / lendingPaymentSchedule.getLoanAmount();
                }

                if (lendingApplication.getTenureInMonths() < 12) {
                    if (paidRatio > 0.5D && paidRatio <= 0.95D) {
                        log.info("topup tenure {} months of merchantId: {}", lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                        return ExistingTopupRuleEngineV2(lendingPaymentSchedule, lendingApplication, createTopupAppCheck, clientIdentifier);
                    } else {
                        addRejectionReason(eligiblity, "Paid ratio requirement not met for tenure < 12 months");
                        log.info("Paid ratio {} not in range (0.5-0.95) for tenure {} months, merchantId: {}",
                                paidRatio, lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }
                }

                if (lendingApplication.getTenureInMonths() >= 12) {
                    if (TRILLIONLOANS.name().equalsIgnoreCase(lendingApplication.getLender()) || (paidRatio > 0.75D && paidRatio <= 0.95D)) {
                        log.info("topup tenure {} months of merchantId: {}", lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                        return AdditionalTopupRuleEngineV2(lendingPaymentSchedule, lendingApplication, createTopupAppCheck, clientIdentifier);
                    } else {
                        addRejectionReason(eligiblity, "Paid ratio requirement not met for tenure >= 12 months");
                        log.info("Paid ratio {} not in range (0.75-0.95) and lender is not TRILLIONLOANS for tenure {} months, merchantId: {}",
                                paidRatio, lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }
                }

                log.info("topup eligibility checks failed for merchantId: {}, tenure: {}, lender: {}",
                        lendingPaymentSchedule.getMerchantId(), lendingApplication.getTenureInMonths(), lendingApplication.getLender());
            }
        } catch (Exception e) {
            log.error("Exception occurred while checking eligibility for topup", e);
        }
        return eligiblity;
    }

    private List<LoanEligibilityDTO> AdditionalTopupRuleEngineV2(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingApplication lendingApplication, boolean createTopupAppCheck, String clientIdentifier) {
        return processTopupRuleEngine(lendingPaymentSchedule, lendingApplication, createTopupAppCheck, true, clientIdentifier);

    }


    private List<LoanEligibilityDTO> ExistingTopupRuleEngineV2(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingApplication lendingApplication, boolean createTopupAppCheck, String clientIdentifier) {
        return processTopupRuleEngine(lendingPaymentSchedule, lendingApplication, createTopupAppCheck, false, clientIdentifier);
    }

    private List<LoanEligibilityDTO> processTopupRuleEngine(LendingPaymentScheduleSlave lendingPaymentSchedule,
                                                            LendingApplication lendingApplication,
                                                            boolean createTopupAppCheck,
                                                            boolean isAdditionalTopup,
                                                            String clientIdentifier) {
        List<LoanEligibilityDTO> eligibility = new ArrayList<>();
        try {
            Long experianId = null;
            Boolean sevenDayFlag = LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);

            List<LendingEligibleLoan> eligibleLoanList = null;
            if (!createTopupAppCheck) {
                eligibleLoanList = eligibleLoanDao.findLatestByMerchantIdAndLoanTypeAndPayableDays(
                        lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);
                if(!CollectionUtils.isEmpty(eligibleLoanList)){
                    log.info("eligibleLoanList fetched from DB : {}", eligibleLoanList);
                    return eligibilityFromEligibleLoans(eligibleLoanList, lendingPaymentSchedule);
                }
            }

            // Process when no eligible loans found
            if (CollectionUtils.isEmpty(eligibleLoanList)) {
                Double eligibleAmount = 0D;
                GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimitV2(
                        lendingPaymentSchedule.getMerchantId(), EligibilityRequestSource.TOPUP_SCHEDULER.name().equalsIgnoreCase(clientIdentifier) ? EligibilityRequestSource.TOPUP_SCHEDULER : EligibilityRequestSource.EASY_LOANS);

                if (Objects.isNull(globalLimitResponse) || Objects.isNull(globalLimitResponse.getData())) {
                    log.info("Global Limit not found");
                    return null;
                }

                if (globalLimitResponse.getData().getGlobalLimit() != null) {
                    log.info("Global limit for merchant:{} is {}", lendingPaymentSchedule.getMerchantId(),
                            globalLimitResponse.getData().getGlobalLimit());
                    eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                }

                if (eligibleAmount.equals(0D) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId()) && globalLimitResponse.getData().getRejectReason() != null) {
                    addRejectionReason(eligibility, globalLimitResponse.getData().getRejectReason());
                    log.info("No topup eligibility found for merchant:{} , reason:{}", lendingPaymentSchedule.getMerchantId(), globalLimitResponse.getData().getRejectReason() );
                    return eligibility;
                }

                if ( eligibleAmount.equals(0D) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId()) && globalLimitResponse.getData().getRejectReason() == null) {
                    addRejectionReason(eligibility, "No topup eligibility found as eligibleAmount is 0");
                    log.info("No topup eligibility found for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligibility;
                }

                if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
                    int posAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
                    if (eligibleAmount - posAmount < 10000) {
                        addRejectionReason(eligibility, "Outstanding amount less than 10k");
                        log.info("Outstanding amount less than 10k for merchant:{}", lendingPaymentSchedule.getMerchantId());
                        return eligibility;
                    }
                }

                eligibleLoanList = loanDetailsServiceV2.recomputeEligibleLoanV2(globalLimitResponse, eligibleAmount, lendingPaymentSchedule.getMerchantId());
            }

            double prevLoanUnpaidAmount = getPreviousLoanAmount(lendingPaymentSchedule);
            BigDecimal prevLoanUnpaidAmountBD = BigDecimal.valueOf(prevLoanUnpaidAmount);
            String topupLender = topupLenderMapper(lendingPaymentSchedule.getNbfc());

            // Process each eligible loan
            for (LendingEligibleLoan eligibleLoan : eligibleLoanList) {
                log.info("Processing eligible loan: {}", eligibleLoan);

                LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(
                        lendingPaymentSchedule.getMerchantId());

                BigDecimal processingFee = calculateProcessingFeeForTopup(
                        eligibleLoan, lendingPaymentSchedule, lendingApplication,
                        lendingRiskVariables, prevLoanUnpaidAmountBD, topupLender);

                if (processingFee == null) {
                    addRejectionReason(eligibility, "Either loan amount or prevLoanunpaidAmount or processing fee rate is null");
                    continue;
                }

                if (additionalTopupChecksFailedV2(lendingPaymentSchedule, eligibleLoan, lendingApplication, topupLender)) {
                    addRejectionReason(eligibility, "Additional topup checks failed");
                    log.info("additional topup checks failed for merchant id {}", lendingPaymentSchedule.getMerchantId());
                    continue;
                }

                LoanEligibilityDTO loanEligibilityDTO = buildLoanEligibilityDTO(
                        eligibleLoan, lendingPaymentSchedule,
                        processingFee, prevLoanUnpaidAmountBD.doubleValue());

                // Set parent loan details only for existing topup (when not additional topup)
                if (!isAdditionalTopup) {
                    loanEligibilityDTO.setParentLender(lendingApplication.getLender());
                    loanEligibilityDTO.setParentLan(lendingApplication.getNbfcId());
                    loanEligibilityDTO.setParentLoanNo(lendingApplication.getExternalLoanId());
                }

                eligibility.add(loanEligibilityDTO);
                log.info("eligible loan for topUp: {}", eligibleLoan);
                //eligibleLoanDao.save(eligibleLoan);
            }

            // Update pilot identifier only for additional topup
            if (isAdditionalTopup) {
                LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(
                        lendingPaymentSchedule.getMerchantId());
                String pilotIdentifier = lendingRiskVariables.getPilotIdentifier();
                if (!ObjectUtils.isEmpty(pilotIdentifier) && !pilotIdentifier.contains(TOPUP_PILOT_IDENTIFIER)) {
                    pilotIdentifier = pilotIdentifier + "," + TOPUP_PILOT_IDENTIFIER;
                }
                if (ObjectUtils.isEmpty(pilotIdentifier)) {
                    pilotIdentifier = TOPUP_PILOT_IDENTIFIER;
                }
                lendingRiskVariables.setPilotIdentifier(pilotIdentifier);
                //lendingRiskVariablesDao.save(lendingRiskVariables);
            }

        } catch (Exception e) {
            String errorMessage = isAdditionalTopup ?
                    "Exception occurred in Additional Topup Rule Engine" :
                    "Exception occurred while existing topup rule engine";
            addRejectionReason(eligibility, errorMessage + ": " + e.getMessage());
            log.error("{} for merchantId: {} - Error: {}", errorMessage,
                    lendingPaymentSchedule.getMerchantId(), e.getMessage(), e);

        }

        return filterEligibilityResults(eligibility);
    }

    private List<LoanEligibilityDTO> filterEligibilityResults(List<LoanEligibilityDTO> eligibility) {
        // Get all non-rejected loans (valid offers with amount, APR, IRR, etc.)
        List<LoanEligibilityDTO> validLoans = eligibility.stream()
                .filter(loan -> !Boolean.TRUE.equals(loan.getIsRejected()))
                .collect(Collectors.toList());

        // If we have valid loans with offers, return only those
        if (!validLoans.isEmpty()) {
            return validLoans;
        }

        // If only rejection reasons exist, return the first one
        return eligibility.stream()
                .filter(loan -> Boolean.TRUE.equals(loan.getIsRejected()))
                .findFirst()
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    private List<LoanEligibilityDTO> eligibilityFromEligibleLoans(List<LendingEligibleLoan> eligibleLoanList, LendingPaymentScheduleSlave lendingPaymentSchedule) {
        if (CollectionUtils.isEmpty(eligibleLoanList)) {
            return new ArrayList<>();
        }

        return eligibleLoanList.stream()
                .map(eligibleLoan -> {
                    double prevLoanUnpaidAmount = getPreviousLoanAmount(lendingPaymentSchedule);

                    return LoanEligibilityDTO.builder()
                            .activeApplicationId(lendingPaymentSchedule.getApplicationId())
                            .prevLoanUnpaidAmount((int) prevLoanUnpaidAmount)
                            .interestRate(eligibleLoan.getRateOfInterest())
                            .amount(eligibleLoan.getAmount().intValue())
                            .category(eligibleLoan.getCategory())
                            .edi(eligibleLoan.getEdi())
                            .repayment(eligibleLoan.getRepayment())
                            .tenure(eligibleLoan.getTenure())
                            .construct(eligibleLoan.getLoanConstruct())
                            .optionEnable(true)
                            .interestAmount(eligibleLoan.getRepayment() - eligibleLoan.getAmount().intValue())
                            .ioEdiCount(eligibleLoan.getIoEdiDays())
                            .ioEdi(eligibleLoan.getIoEdi())
                            .tenureInMonths(eligibleLoan.getTenureInMonths())
                            .principleEdiTenure(eligibleLoan.getTenureInMonths())
                            .processingFee(eligibleLoan.getProcessingFee())
                            .disbursementAmount(eligibleLoan.getAmount().intValue() - (int) prevLoanUnpaidAmount - eligibleLoan.getProcessingFee().intValue())
                            .loanType("TOPUP")
                            .ediCount(eligibleLoan.getEdiCount())
                            .id(eligibleLoan.getId())
                            .apr(eligibleLoan.getApr() != null ? Double.valueOf(df.format(eligibleLoan.getApr())) : null)
                            .irr(eligibleLoan.getIrr() != null ? Double.valueOf(df.format(eligibleLoan.getIrr())) : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private boolean additionalTopupChecksFailedV2(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingEligibleLoan eligibleLoan, LendingApplication lendingApplication, String topupLender) {
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingPaymentSchedule.getMerchantId());
        double vintage = !ObjectUtils.isEmpty(lendingRiskVariables.getVintage()) ? lendingRiskVariables.getVintage() : 0D;
//        Double amountPaidThroughQrPer = loanUtil.getAmountPaidThroughQrPer(lendingPaymentSchedule);
        int currentDPD = LoanUtil.calculateDPD(lendingPaymentSchedule.getEdiAmount(), lendingPaymentSchedule.getDueAmount());

        logger.info("LendingRiskVariables : {}", lendingRiskVariables);
        logger.info("Topup Lender : {}", topupLender);
        RiskVariablesDTO riskVariables = new RiskVariablesDTO();
        PricingExperiment pricingExperiment = null;
        if(pricingExpEnabled) {
            pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                    eligibleLoan.getTenureInMonths(), (int) (lendingApplication.getMerchantId()%10), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
        }
        if(!ObjectUtils.isEmpty(pricingExperiment)) {
            logger.info("experiment fetched for {}: {}", lendingPaymentSchedule.getMerchantId(), pricingExperiment);
            riskVariables.setPricingExperimentMap(Collections.singletonMap(lendingApplication.getMerchantId(), pricingExperiment));
        }else{
            LendingLenderPricing lenderPricing = lendingLenderPricingDao.findTop1BySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                    eligibleLoan.getTenureInMonths(), topupLender, lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
            riskVariables.setLenderPricingMap(Collections.singletonMap(topupLender, lenderPricing));
        }

        logger.info("Risk variables : {}", riskVariables);
        if (lenderAssignService.maxIrrCheckFailedV2(eligibleLoan, LenderOffDays.valueOf(topupLender).getEdiModel(), topupLender, riskVariables)) {
            logger.info("max irr check failed for merchant id {}, lender {}", lendingPaymentSchedule.getMerchantId(), topupLender);
            return true;
        }
        if (lenderAssignService.maxAprCheckFailedV2(eligibleLoan, LenderOffDays.valueOf(topupLender).getEdiModel(), topupLender, riskVariables)) {
            logger.info("max apr check failed for merchant id {}, lender {}", lendingPaymentSchedule.getMerchantId(), topupLender);
            return true;
        }
        if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && vintage < 90) {
            logger.info("vintage check failed for merchant id {}, lender {} : {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc(), vintage);
            return true;
        }
        if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && currentDPD > piramalTopupMaxCurrentDpd) {
            logger.info("dpd check failed for merchant id {}, lender {} : {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc(), currentDPD);
            return true;
        }
//        if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && amountPaidThroughQrPer < 40) {
//            logger.info("amt paid through qr check failed for merchant id {}, lender {} : {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc(), amountPaidThroughQrPer);
//            return true;
//        }
        if(llBalanceBreHardRejectEnabled && LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
            LendingApplication prevApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(lendingPaymentSchedule.getMerchantId());
            if(!ObjectUtils.isEmpty(prevApplication) && LoanType.TOPUP.name().equalsIgnoreCase(prevApplication.getLoanType()) && "rejected".equalsIgnoreCase(prevApplication.getStatus())) {
                LendingApplicationLenderDetailsSlave lendingApplicationLenderDetailsSlave = lendingApplicationLenderDetailsDaoSlave.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(prevApplication.getId(), Status.INACTIVE.name(), prevApplication.getLender());
                if(!ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave) && !ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave.getBreRejectionReason())) {
                    log.info("BRE for liquiloans balance transfer application {} already failed with reason {} for merchantId {}", prevApplication.getId(), lendingApplicationLenderDetailsSlave.getBreRejectionReason(), lendingPaymentSchedule.getMerchantId());
                    return true;
                }
            }
        }
        return false;
    }

    public BigDecimal calculateProcessingFeeForTopup(LendingEligibleLoan eligibleLoan,
                                                     LendingPaymentScheduleSlave lendingPaymentSchedule,
                                                     LendingApplication lendingApplication,
                                                     LendingRiskVariables lendingRiskVariables,
                                                     BigDecimal prevLoanUnpaidAmountBD, String topupLender) {

        // Try pricing experiment first
        PricingExperiment pricingExperiment = null;
        if (pricingExpEnabled) {
            pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(
                    lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                    eligibleLoan.getTenureInMonths(), (int) (lendingPaymentSchedule.getMerchantId() % 10),
                    lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
        }

        if (!ObjectUtils.isEmpty(pricingExperiment)) {
            logger.info("Using pricing experiment for merchant: {}, experiment: {}",
                    lendingPaymentSchedule.getMerchantId(), pricingExperiment);

            BigDecimal processingFeeRateBD = BigDecimal.valueOf(pricingExperiment.getProcessingFeeRate());
            BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
            BigDecimal processingFee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                    .divide(new BigDecimal(100), 0, RoundingMode.CEILING);

            BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
            eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
            loanUtil.setEligibleLoanV2(eligibleLoan, pricingExperiment.getInterestRate(),
                 processingFee, eligibleLoan.getAmount(), topupLender);

            return processingFee;
        }

        // Try lender pricing
        LendingLenderPricing lenderPricing = lendingLenderPricingDao.findTop1BySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColorAndStatus(
                lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                eligibleLoan.getTenureInMonths(), topupLender,
                lendingRiskVariables.getPincodeColor().name(), "ACTIVE");

        if (!ObjectUtils.isEmpty(lenderPricing)) {
            logger.info("Using lender pricing for merchant: {}, pricing: {}",
                    lendingPaymentSchedule.getMerchantId(), lenderPricing);

            BigDecimal processingFeeRateBD = BigDecimal.valueOf(lenderPricing.getProcessingFeeRate());
            BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
            BigDecimal processingFee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                    .divide(new BigDecimal(100), 0, RoundingMode.CEILING);

            BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
            eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
            loanUtil.setEligibleLoanV2(eligibleLoan, lenderPricing.getInterestRate(),
                  processingFee, eligibleLoan.getAmount(), topupLender);

            return processingFee;
        }

        // Default processing fee calculation
        if (eligibleLoan.getAmount() != null && eligibleLoan.getProcessingFeeRate() != null && prevLoanUnpaidAmountBD != null) {
            BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
            BigDecimal processingFeeRateBD = BigDecimal.valueOf(eligibleLoan.getProcessingFeeRate());
            return amountBD.subtract(prevLoanUnpaidAmountBD)
                    .multiply(processingFeeRateBD)
                    .setScale(0, RoundingMode.CEILING);
        }

        logger.error("Unable to calculate processing fee - missing required data for eligible loan: {}", eligibleLoan.getId());
        return null;
    }

    private LoanEligibilityDTO buildLoanEligibilityDTO(LendingEligibleLoan eligibleLoan,
                                                       LendingPaymentScheduleSlave lendingPaymentSchedule,
                                                       BigDecimal processingFee,
                                                       double prevLoanUnpaidAmount) {

        LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();

        loanEligibilityDTO.setActiveApplicationId(lendingPaymentSchedule.getId());
        loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
        loanEligibilityDTO.setInterestRate(eligibleLoan.getRateOfInterest());
        loanEligibilityDTO.setAmount(eligibleLoan.getAmount().intValue());
        loanEligibilityDTO.setCategory(eligibleLoan.getCategory());
        loanEligibilityDTO.setEdi(eligibleLoan.getEdi());
        loanEligibilityDTO.setRepayment(eligibleLoan.getRepayment());
        loanEligibilityDTO.setTenure(eligibleLoan.getTenure());
        loanEligibilityDTO.setConstruct(eligibleLoan.getLoanConstruct());
        loanEligibilityDTO.setOptionEnable(true);
        loanEligibilityDTO.setInterestAmount(eligibleLoan.getRepayment() - eligibleLoan.getAmount().intValue());
        loanEligibilityDTO.setIoEdiCount(eligibleLoan.getIoEdiDays());
        loanEligibilityDTO.setIoEdi(eligibleLoan.getIoEdi());
        loanEligibilityDTO.setTenureInMonths(eligibleLoan.getTenureInMonths());
        loanEligibilityDTO.setPrincipleEdiTenure(eligibleLoan.getTenureInMonths());
        loanEligibilityDTO.setProcessingFee(processingFee.intValue());
        loanEligibilityDTO.setDisbursementAmount(
                loanEligibilityDTO.getAmount() - (int) prevLoanUnpaidAmount - loanEligibilityDTO.getProcessingFee());
        loanEligibilityDTO.setLoanType("TOPUP");
        loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
        loanEligibilityDTO.setId(eligibleLoan.getId());
        loanEligibilityDTO.setApr(Double.valueOf(df.format(eligibleLoan.getApr())));
        loanEligibilityDTO.setIrr(Double.valueOf(df.format(eligibleLoan.getIrr())));
        log.info("Loan Eligibility DTO created: {}", loanEligibilityDTO);

        return loanEligibilityDTO;
    }

    public List<LoanEligibilityDTO> topupLoan(LendingPaymentScheduleSlave lendingPaymentSchedule, boolean createTopupAppCheck) {
        log.info("calculating topup loan eligibility for merchantId: {}", lendingPaymentSchedule.getMerchantId());
        List<Long> derogMerchants = loanUtil.loadDerogEffectedMerchants();
        List<Long> customEnabledMerchants = loanUtil.customEnabledTopupMerchants();
        LendingApplication lendingApplication =
                lendingApplicationDao.findByIdAndMerchantId(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getMerchantId());

        List<LendingPaymentScheduleSlave> activeLoans = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatusList(lendingPaymentSchedule.getMerchantId(),"ACTIVE");

        if (activeLoans.size() > 1) {
            logger.info("more than 1 loan active for merchantId : {} loans : {}", lendingPaymentSchedule.getMerchantId(), activeLoans.size());
            return Collections.emptyList();
        }

        if (customEnabledMerchants.contains(lendingPaymentSchedule.getMerchantId())) {
            return computeEligibility(lendingPaymentSchedule, createTopupAppCheck, lendingApplication);

        }

        if (pilotTestEnabled && derogMerchants.contains(lendingPaymentSchedule.getMerchantId()) && derogTopUpEnable(lendingPaymentSchedule.getMerchantId())) {
            return computeEligibility(lendingPaymentSchedule, createTopupAppCheck, lendingApplication);
        }

        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();

        try {
            if (!isTopUpEnabled) {
                addRejectionReason(eligiblity, "Topup are disabled");
                logger.info("Topup loans are disabled");
                return eligiblity;
            }
            if(!topupLenders.contains(lendingPaymentSchedule.getNbfc())){
                addRejectionReason(eligiblity, "Topup not enabled for this lender");
                logger.info("Topup not enabled on lender:{}",lendingPaymentSchedule.getNbfc());
                return eligiblity;
            }

            if(LIQUILOANS_NBFC.toString().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && !easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), topupOnTltoTlRolloutPercent)){
                addRejectionReason(eligiblity, "LIQUILOANS_NBFC Topup not enabled for this lender");
                logger.info("LIQUILOANS_NBFC topup not enabled for merchantId:{}",lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if (!(easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(),rolloutTopupPercent)) && LIQUILOANS_TOPUP_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
                addRejectionReason(eligiblity, "Topup not enabled for this merchant");
                logger.info("Topup not enabled for this merchant :{}",lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if(TRILLIONLOANS.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && !easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), trillionTopupRolloutPercent) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                addRejectionReason(eligiblity, "TRILLIONLOANS Topup not enabled for this merchant");
                log.info("TRILLIONLOANS Topup not enabled for merchantId: {}", lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if(ABFL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && !easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), abflTopupRolloutPercent) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                addRejectionReason(eligiblity, "ABFL Topup not enabled for this merchant");
                log.info("ABFL Topup not enabled for merchantId: {}", lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if(PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && !easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), piramalTopupRolloutPercent) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                addRejectionReason(eligiblity, "PIRAMAL Topup not enabled for this merchant");
                log.info("PIRAMAL Topup not enabled for merchantId: {}", lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if(topupPilotRunEnabledLenders.contains(lendingPaymentSchedule.getNbfc())){
                LenderTopupEligibility lenderTopupEligibility = lenderTopupEligibilityDao.findTopupEligibilityFromLender(
                        lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getNbfc());
                if (ObjectUtils.isEmpty(lenderTopupEligibility)) {
                    log.info("Merchant not eligible from lender {} for merchantId: {}",lendingPaymentSchedule.getNbfc(), lendingPaymentSchedule.getMerchantId());
                    addRejectionReason(eligiblity, "Merchant not eligible from lender");
                    return eligiblity;
                }
            }


            if (loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                Long experianId = null;

                Boolean sevenDayFlag = LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);

                List<LendingEligibleLoan> eligibleLoanList = eligibleLoanDao.
                  findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);

                String lender = lendingApplication.getLender();
                int ediAmount = roundDownEligibleLenders.contains(lender) ? 664 : 665;
                int ediCount = 360;
                LendingEligibleLoan internalMerchantLoan = new LendingEligibleLoan(lendingPaymentSchedule.getMerchantId(), experianId, 200000D, "12 Months", "ACTIVE", null, 0, 0, null, ediAmount, 0, ediAmount * ediCount, null, "TOPUP", null);
                internalMerchantLoan.setEdiCount(ediCount);
                internalMerchantLoan.setRateOfInterest(1.63);
                internalMerchantLoan.setProcessingFee(9420);
                internalMerchantLoan.setProcessingFeeRate(0.0471);
                internalMerchantLoan.setId(644147506L);
                internalMerchantLoan.setTenureInMonths(12);
                internalMerchantLoan.setCreatedAt(new Date());
                eligibleLoanDao.save(internalMerchantLoan);
                eligibleLoanList.add(internalMerchantLoan);

                double prevLoanUnpaidAmount = getPreviousLoanAmount(lendingPaymentSchedule);
                BigDecimal prevLoanUnpaidAmountBD = BigDecimal.valueOf(getPreviousLoanAmount(lendingPaymentSchedule));

                if (!eligibleLoanList.isEmpty()) {
                    BigDecimal processingFee;
                    Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                    LendingEligibleLoan eligibleLoan = eligibleLoanList.get(0);
                    logger.info("eligible loan fetched: {}", eligibleLoan);

                    LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingPaymentSchedule.getMerchantId());
                    PricingExperiment pricingExperiment = null;
                    if(pricingExpEnabled) {
                        pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                                eligibleLoan.getTenureInMonths(), (int) (lendingPaymentSchedule.getMerchantId()%10), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
                    }
                    LendingLenderPricing lenderPricing = lendingLenderPricingDao.findTop1BySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                            eligibleLoan.getTenureInMonths(), lendingApplication.getLender(), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");

                    if(!ObjectUtils.isEmpty(pricingExperiment)) {
                        logger.info("pricing experiment fetched for {}: {}", lendingPaymentSchedule.getMerchantId(), pricingExperiment);
                        BigDecimal processingFeeRateBD = BigDecimal.valueOf(pricingExperiment.getProcessingFeeRate());
                        BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                        processingFee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                                .divide(new BigDecimal(100), 0, RoundingMode.CEILING);
                        BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
                        eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
                        loanUtil.setEligibleLoan(eligibleLoan, pricingExperiment.getInterestRate(), processingFee, eligibleLoan.getAmount(), lendingApplication.getLender());
                    }
                    else if(!ObjectUtils.isEmpty(lenderPricing)){
                        logger.info("LendingLenderPricing : {}", lenderPricing);
                        BigDecimal processingFeeRateBD = BigDecimal.valueOf(lenderPricing.getProcessingFeeRate());
                        BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                        processingFee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                                .divide(new BigDecimal(100), 0, RoundingMode.CEILING);
                        BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
                        eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
                        loanUtil.setEligibleLoan(eligibleLoan, lenderPricing.getInterestRate(), processingFee, eligibleLoan.getAmount(), lendingApplication.getLender());
                    } else if(eligibleLoan.getAmount() != null && eligibleLoan.getProcessingFeeRate() != null && prevLoanUnpaidAmountBD != null){
                        BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                        BigDecimal processingFeeRateBD = BigDecimal.valueOf(eligibleLoan.getProcessingFeeRate());
                        processingFee = amountBD.subtract(prevLoanUnpaidAmountBD).multiply(processingFeeRateBD).setScale(0, RoundingMode.CEILING);
                    } else{
                        throw new IllegalArgumentException("Either processing fee rate or loan amount cannot be null");
                    }

                    if (additionalTopupChecksFailed(lendingPaymentSchedule, eligibleLoan, lendingApplication)) {
                        log.info("additional topup checks failed for merchant id {}", lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }

                    logger.info("eligible loan: {}", eligibleLoan);
                    LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
                    loanEligibilityDTO.setActiveApplicationId(lendingPaymentSchedule.getId());
                    loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
                    loanEligibilityDTO.setInterestRate(eligibleLoan.getRateOfInterest());
                    loanEligibilityDTO.setAmount(eligibleLoan.getAmount().intValue());
                    loanEligibilityDTO.setCategory(eligibleLoan.getCategory());
                    loanEligibilityDTO.setEdi(eligibleLoan.getEdi());
                    loanEligibilityDTO.setRepayment(eligibleLoan.getRepayment());
                    loanEligibilityDTO.setTenure(eligibleLoan.getTenure());
                    loanEligibilityDTO.setConstruct(eligibleLoan.getLoanConstruct());
                    loanEligibilityDTO.setOptionEnable(true);
                    loanEligibilityDTO.setInterestAmount(eligibleLoan.getRepayment() - eligibleLoan.getAmount().intValue());
                    loanEligibilityDTO.setIoEdiCount(eligibleLoan.getIoEdiDays());
                    loanEligibilityDTO.setIoEdi(eligibleLoan.getIoEdi());
                    loanEligibilityDTO.setTenureInMonths(eligibleLoan.getTenureInMonths());
                    loanEligibilityDTO.setPrincipleEdiTenure(eligibleLoan.getTenureInMonths());
                    loanEligibilityDTO.setProcessingFee(processingFee.intValue());
                    loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getAmount() - (int) prevLoanUnpaidAmount - loanEligibilityDTO.getProcessingFee());
                    loanEligibilityDTO.setLoanType("TOPUP");
                    loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
                    loanEligibilityDTO.setId(eligibleLoan.getId());
                    loanEligibilityDTO.setApr(Double.valueOf(df.format(eligibleLoan.getApr())));
                    loanEligibilityDTO.setIrr(Double.valueOf(df.format(eligibleLoan.getIrr())));
                    eligiblity.add(loanEligibilityDTO);
                }

                return eligiblity;

            }

            if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
                if (lendingApplication == null) {
                    addRejectionReason(eligiblity, "Lending Application not found/topup loan");
                    logger.info("Lending Application not found/topup loan for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (LoanType.SMALL_TICKET.name().equals(lendingApplication.getLoanType())) {
                    addRejectionReason(eligiblity, "Last loan is small ticket");
                    logger.info("last loan is small ticket for merchant:{} with applicationId: {}", lendingPaymentSchedule.getMerchantId(), lendingApplication.getId());
                    return eligiblity;
                }
//                if (!loanUtil.isEnachDone(lendingPaymentSchedule.getMerchantId())) {
//                    logger.info("Nach not success for merchant:{}", lendingPaymentSchedule.getMerchantId());
//                    return eligiblity;
//                }
                List<KycDoc> kycDocs = kycHandler.getKycDocForTopup(lendingApplication.getMerchantId());
                KycStatusDTO kycStatus = KycUtils.getKycStatusDTO(kycDocs, lendingApplication.getLender());
                if (!KycStatus.APPROVED.equals(kycStatus.getKycStatus())) {
                    addRejectionReason(eligiblity, "Kyc not approved");
                    logger.info("Kyc not approved for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                int age = KycUtils.getAgeFromKycDoc(kycDocs);
                if (age > 0 && age < 21) {
                    addRejectionReason(eligiblity, "Merchant age is less than 21");
                    logger.info("Age requirement not fulfilled for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                BigInteger maxDpd = loanDpdDaoSlave.findMaxDpd(lendingPaymentSchedule.getId());
                if (maxDpd.intValue() > 30) {
                    addRejectionReason(eligiblity, "Merchant Dpd greater than 30");
                    logger.info("Merchant Dpd Greater than 30 merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                if(LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc()) && LoanUtil.calculateDPD(lendingPaymentSchedule.getEdiAmount(), lendingPaymentSchedule.getDueAmount()) > llBalanceTransferLoanCurrentDpdThreshold) {
                    addRejectionReason(eligiblity, "Merchant current Dpd greater than 0 for Liquiloans BT lenders");
                    logger.info("Merchant current Dpd Greater than 0 merchant:{} for lender {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc());
                    return eligiblity;
                }

                Double settlementAmount = lendingLedgerDao.findSettlementAmount(lendingPaymentSchedule.getId());
                if(LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
                    return ExistingTopupRuleEngine(lendingPaymentSchedule, lendingApplication, createTopupAppCheck, settlementAmount);
                }
                double qrPaidRatio = (settlementAmount / lendingPaymentSchedule.getPaidAmount()) * 100;
                if (qrPaidRatio <= topupMinQrPaidRatio) {
                    if(lendingApplication.getTenureInMonths() >= 12 && TRILLIONLOANS.name()
                            .equalsIgnoreCase(lendingApplication.getLender())) {
                        logger.info("Skipping QR rejection due to tenure >= 12 and lender is TRILLIONLOANS" +
                                        " for merchant: {}", lendingPaymentSchedule.getMerchantId());
                    } else {
                        addRejectionReason(eligiblity, "QR payment less than 40%");
                        logger.info("QR payment less than {} in tenure {} for merchant: {}", topupMinQrPaidRatio,
                                lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }
                }

                if(lendingApplication.getTenureInMonths() >= 12 && TRILLIONLOANS.name()
                        .equalsIgnoreCase(lendingApplication.getLender())) {
                    int ediPaidDays = lendingPaymentSchedule.getEdiCount() - lendingPaymentSchedule.getEdiRemainingCount();
                    if(ediPaidDays <= 120) {
                        addRejectionReason(eligiblity, "Edi paid days is less than 120");
                        logger.info("Edi paid days is less than {} for tenure {} for merchant: {} and lender: {}", 120,
                                lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId(),
                                lendingApplication.getLender());
                        return eligiblity;
                    }
                }

                double paidRatio = 0d;
                if (lendingPaymentSchedule.getPaidPrinciple() != null && lendingPaymentSchedule.getLoanAmount() != null) {
                    paidRatio = lendingPaymentSchedule.getPaidPrinciple() / lendingPaymentSchedule.getLoanAmount();
                }

                if (lendingApplication.getTenureInMonths() < 12 && paidRatio > 0.5D && paidRatio <= 0.95D) {
                    logger.info("paid ratio is {} for tenure {} months of merchantId: {}", paidRatio, lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                    return ExistingTopupRuleEngine(lendingPaymentSchedule, lendingApplication, createTopupAppCheck, settlementAmount);
                }
                if (lendingApplication.getTenureInMonths() >= 12 &&
                        (TRILLIONLOANS.name().equalsIgnoreCase(lendingApplication.getLender()) || (paidRatio > 0.75D && paidRatio <= 0.95D))) {
                    logger.info("paid ratio is {} for tenure {} months of merchantId: {}", paidRatio, lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                    return AdditionalTopupRuleEngine(lendingPaymentSchedule, lendingApplication, createTopupAppCheck);
                }
                log.info("topup eligibility checks failed for merchantId: {}, paidRatio: {}, tenure: {}, lender: {}",
                        lendingPaymentSchedule.getMerchantId(), paidRatio, lendingApplication.getTenureInMonths(), lendingApplication.getLender());
            }
        } catch (Exception e) {
            logger.error("Exception occurred while checking eligibility for topup", e);
        }
        return eligiblity;
    }

    private List<LoanEligibilityDTO> AdditionalTopupRuleEngine(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingApplication lendingApplication, boolean createTopupAppCheck) {
        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();
        try {
            String shopType;
            LmsFieldValues lmsFieldValues = lmsFieldValuesDao.findByFieldIdAndLendingApplicationId(38L, lendingPaymentSchedule.getApplicationId());
            if (!ObjectUtils.isEmpty(lmsFieldValues)) {
                shopType = lmsFieldValues.getFieldDropdownValue();
                logger.info("shop type found for merchant: {} from lms fields for last application: {}", shopType, lendingApplication.getId());
            } else {
                LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());
                shopType = Objects.nonNull(lendingGstDetail) ? lendingGstDetail.getShopType() : null;
                logger.info("shop type found for merchant: {} from lending_gst_detail for last application: {}", shopType, lendingApplication.getId());
            }
            if (ObjectUtils.isEmpty(shopType) || !"PERMANENT".equalsIgnoreCase(shopType)) {
                addRejectionReason(eligiblity, "Photo shop is not permanent");
                logger.info("Photo shop is not permanent of merchant: {} for last application: {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                return eligiblity;
            }
            /*Integer ediPaidCount = lendingLedgerDao.findLedgerCountOnAmountGreaterThanEdiAmount(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getEdiAmount());
            int paidCount = lendingPaymentSchedule.getEdiCount() - lendingPaymentSchedule.getEdiRemainingCount();
            logger.info("ediPaidCount:{} and paidCount:{} for merchant:{}", ediPaidCount, paidCount, lendingPaymentSchedule.getMerchantId());
            double ediPaidRatio = (ediPaidCount * 1.0 / paidCount) * 100;*/

            Long experianId = null;

            Boolean sevenDayFlag = LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);

            List<LendingEligibleLoan> eligibleLoanList = null;
            if(!createTopupAppCheck){
                eligibleLoanList = eligibleLoanDao.findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);
            }

            if (loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                LendingEligibleLoan internalMerchantLoan = new LendingEligibleLoan(lendingPaymentSchedule.getMerchantId(), experianId, 300000D, "12 Months", "ACTIVE", null, 0, 0, null, 1149, 0, 357339, null, "TOPUP", null);
                internalMerchantLoan.setRateOfInterest(1.59);
                internalMerchantLoan.setProcessingFee(14130);
                internalMerchantLoan.setProcessingFeeRate(0.05D);
                internalMerchantLoan.setId(644147506L);
                eligibleLoanList.add(internalMerchantLoan);
            }

            if (ObjectUtils.isEmpty(eligibleLoanList)) {
                Double eligibleAmount = 0D;
                GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(lendingPaymentSchedule.getMerchantId(), EligibilityRequestSource.EASY_LOANS);
                if(!ObjectUtils.isEmpty(globalLimitResponse) && !ObjectUtils.isEmpty(globalLimitResponse.getData())
                        && !ObjectUtils.isEmpty(globalLimitResponse.getData().getRiskGroup()) && !allowedRiskGroupsStp.contains(globalLimitResponse.getData().getRiskGroup())) {
                    addRejectionReason(eligiblity, "Risk group is not R1 or R2");
                    logger.info("Risk group is not R1 or R2 of new loan of merchant: {}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
                    logger.info("Global limit for merchant:{} is {}", lendingPaymentSchedule.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
                    eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                }
                if (eligibleAmount.equals(0D) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                    addRejectionReason(eligiblity, "No topup eligibility found as eligibleAmount is 0");
                    logger.info("No topup eligibility found for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
                   /* if (ediPaidRatio < 50D) {
                        logger.info("EDI paid ratio:{} is less than 50% for merchant:{}", ediPaidRatio, lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }*/
                    int posAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
                    if (eligibleAmount - posAmount < 10000) {
                        addRejectionReason(eligiblity, "Outstanding amount less than 10k");
                        logger.info("Outstanding amount less than 10k for merchant:{}", lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }
                }

                loanDetailsServiceV2.recomputeEligibleLoan(globalLimitResponse, eligibleAmount, lendingPaymentSchedule.getMerchantId(), false);
                eligibleLoanList = eligibleLoanDao.findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);
                Experian experian = experianDao.getByMerchantId(lendingPaymentSchedule.getMerchantId());
                experian.setEligibleAmount(eligibleAmount);
                experian.setLoanType("TOPUP");
                experianDao.save(experian);
            }
            double prevLoanUnpaidAmount = getPreviousLoanAmount(lendingPaymentSchedule);
            BigDecimal prevLoanUnpaidAmountBD = BigDecimal.valueOf(getPreviousLoanAmount(lendingPaymentSchedule));
            BigDecimal processingfee;
            if (!eligibleLoanList.isEmpty()) {
                eligibleLoanList.sort((o1, o2) -> (o2.getCreatedAt().compareTo(o1.getCreatedAt())));
                Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                LendingEligibleLoan eligibleLoan = eligibleLoanList.get(0);
                logger.info("eligible loan fetched: {}", eligibleLoan);

                LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingPaymentSchedule.getMerchantId());

                PricingExperiment pricingExperiment = null;
                if(pricingExpEnabled) {
                    pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                            eligibleLoan.getTenureInMonths(), (int) (lendingPaymentSchedule.getMerchantId()%10), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
                }
                LendingLenderPricing lenderPricing = lendingLenderPricingDao.findTop1BySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                        eligibleLoan.getTenureInMonths(), lendingApplication.getLender(), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");

                if(!ObjectUtils.isEmpty(pricingExperiment)) {
                    logger.info("pricing experiment fetched for {}: {}", lendingPaymentSchedule.getMerchantId(), pricingExperiment);
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(pricingExperiment.getProcessingFeeRate());
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    processingfee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                            .divide(new BigDecimal(100), 0, RoundingMode.CEILING);
                    BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
                    eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
                    loanUtil.setEligibleLoan(eligibleLoan, pricingExperiment.getInterestRate(), processingfee, eligibleLoan.getAmount(), lendingApplication.getLender());
                }
                else if(!ObjectUtils.isEmpty(lenderPricing)){
                    logger.info("LendingLenderPricing : {}", lenderPricing);
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(lenderPricing.getProcessingFeeRate());
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    processingfee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                            .divide(new BigDecimal(100), 0, RoundingMode.CEILING);
                    BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
                    eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
                    loanUtil.setEligibleLoan(eligibleLoan, lenderPricing.getInterestRate(), processingfee, eligibleLoan.getAmount(), lendingApplication.getLender());
                } else if(eligibleLoan.getAmount() != null && prevLoanUnpaidAmountBD != null && eligibleLoan.getProcessingFeeRate() != null){
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(eligibleLoan.getProcessingFeeRate());
                    processingfee = amountBD.subtract(prevLoanUnpaidAmountBD)
                            .multiply(processingFeeRateBD)
                            .setScale(0, RoundingMode.CEILING);
                }
                else{
                    addRejectionReason(eligiblity, "Either loan amount or prevLoanunpainAmount or processing fee rate is null");
                    throw new IllegalArgumentException("Either loan amount or prevLoanunpainAmount or processing fee rate is null");
                }

                if (additionalTopupChecksFailed(lendingPaymentSchedule, eligibleLoan, lendingApplication)) {
                    addRejectionReason(eligiblity, "Additional topup checks failed");
                    log.info("additional topup checks failed for merchant id {}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
                loanEligibilityDTO.setActiveApplicationId(lendingPaymentSchedule.getId());
                loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
                loanEligibilityDTO.setInterestRate(eligibleLoan.getRateOfInterest());
                loanEligibilityDTO.setAmount(eligibleLoan.getAmount().intValue());
                loanEligibilityDTO.setCategory(eligibleLoan.getCategory());
                loanEligibilityDTO.setEdi(eligibleLoan.getEdi());
                loanEligibilityDTO.setRepayment(eligibleLoan.getRepayment());
                loanEligibilityDTO.setTenure(eligibleLoan.getTenure());
                loanEligibilityDTO.setConstruct(eligibleLoan.getLoanConstruct());
                loanEligibilityDTO.setOptionEnable(true);
                loanEligibilityDTO.setInterestAmount(eligibleLoan.getRepayment() - eligibleLoan.getAmount().intValue());
                loanEligibilityDTO.setIoEdiCount(eligibleLoan.getIoEdiDays());
                loanEligibilityDTO.setIoEdi(eligibleLoan.getIoEdi());
                loanEligibilityDTO.setTenureInMonths(eligibleLoan.getTenureInMonths());
//              loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
//              loanEligibilityDTO.setType();
                loanEligibilityDTO.setPrincipleEdiTenure(eligibleLoan.getTenureInMonths());
                //loanEligibilityDTO.setProcessingFee((int) Math.ceil((eligibleLoan.getAmount() - (int) prevLoanUnpaidAmount) * eligibleLoan.getProcessingFeeRate()));
                loanEligibilityDTO.setProcessingFee(processingfee.intValue());
                loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getAmount() - (int) prevLoanUnpaidAmount - loanEligibilityDTO.getProcessingFee());
                loanEligibilityDTO.setLoanType("TOPUP");
                loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
                loanEligibilityDTO.setId(eligibleLoan.getId());
                loanEligibilityDTO.setApr(Double.valueOf(df.format(eligibleLoan.getApr())));
                loanEligibilityDTO.setIrr(Double.valueOf(df.format(eligibleLoan.getIrr())));
                eligiblity.add(loanEligibilityDTO);
            }


            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingPaymentSchedule.getMerchantId());
            String pilotIdentifier = lendingRiskVariables.getPilotIdentifier();
            if(!ObjectUtils.isEmpty(pilotIdentifier) && !pilotIdentifier.contains(TOPUP_PILOT_IDENTIFIER)) {
                pilotIdentifier = pilotIdentifier + "," + TOPUP_PILOT_IDENTIFIER;
            }
            if(ObjectUtils.isEmpty(pilotIdentifier)) {
                pilotIdentifier = TOPUP_PILOT_IDENTIFIER;
            }
            lendingRiskVariables.setPilotIdentifier(pilotIdentifier);
            lendingRiskVariablesDao.save(lendingRiskVariables);
        } catch (Exception e) {
            addRejectionReason(eligiblity, "Exception occurred in Additional Topup Rule Engine");
            logger.info("Exception occurred in Additional Topup Rule Engine for merchantId: {} {}", lendingPaymentSchedule.getMerchantId(), Arrays.asList(e.getStackTrace()));
        }
        return eligiblity;
    }

    private boolean checkForMultipleRepeatTopupOffer(Long merchantId){
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);
        if(ObjectUtils.isEmpty(lendingRiskVariables)){
            return false;
        }
        String pilotIdentifier = lendingRiskVariables.getPilotIdentifier();
        if(!ObjectUtils.isEmpty(pilotIdentifier) && pilotIdentifier.contains(LoanDetailsConstant.MULTIPLE_REPEAT_TOPUP_LOAN_IDENTIFIER)){
            log.info("loan request is multiple_repeat_top_up for {}", merchantId);
            return true;
        }
        return false;
    }

    private List<LoanEligibilityDTO> ExistingTopupRuleEngine(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingApplication lendingApplication, boolean createTopupAppCheck, Double settlementAmount) {
        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();
        try {
            double qrPaidRatio = (settlementAmount / lendingPaymentSchedule.getPaidAmount()) * 100;
            if (!LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc()) && qrPaidRatio <= topupMinQrPaidRatio) {
                addRejectionReason(eligiblity, "QR paid ratio is less than 40%");
                logger.info("QR payment less than {} in tenure {} for merchant: {}", topupMinQrPaidRatio, lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            String shopType;
            LmsFieldValues lmsFieldValues = lmsFieldValuesDao.findByFieldIdAndLendingApplicationId(38L, lendingPaymentSchedule.getApplicationId());
            if (!ObjectUtils.isEmpty(lmsFieldValues)) {
                shopType = lmsFieldValues.getFieldDropdownValue();
                logger.info("shop type found for merchant: {} from lms fields for last application: {}", shopType, lendingApplication.getMerchantId());
            } else {
                LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());
                shopType = Objects.nonNull(lendingGstDetail) ? lendingGstDetail.getShopType() : null;
                logger.info("shop type found for merchant: {} for last application: {}", shopType, lendingApplication.getMerchantId());
            }
            if ("PHOTO_NOT_A_SHOP".equalsIgnoreCase(shopType)) {
                addRejectionReason(eligiblity, "Photo not of a shop");
                logger.info("Photo not of a shop found for merchant: {} for last application: {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                return eligiblity;
            }

            Long experianId = null;
            Boolean sevenDayFlag = LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);
            List<LendingEligibleLoan> eligibleLoanList = null;
            if(!createTopupAppCheck){
                eligibleLoanList = eligibleLoanDao.findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);
            }

            if (loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                LendingEligibleLoan internalMerchantLoan = new LendingEligibleLoan(lendingPaymentSchedule.getMerchantId(), experianId, 300000D, "12 Months", "ACTIVE", null, 0, 0, null, 1149, 0, 357339, null, "TOPUP", null);
                internalMerchantLoan.setRateOfInterest(1.59);
                internalMerchantLoan.setProcessingFee(14130);
                internalMerchantLoan.setProcessingFeeRate(0.05D);
                internalMerchantLoan.setId(644147506L);
                eligibleLoanList.add(internalMerchantLoan);
            }

            if (ObjectUtils.isEmpty(eligibleLoanList)) {
                Double eligibleAmount = 0D;
                GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(lendingPaymentSchedule.getMerchantId(),EligibilityRequestSource.EASY_LOANS);
                if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
                    logger.info("Global limit for merchant:{} is {}", lendingPaymentSchedule.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
                    eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                }
                if (eligibleAmount.equals(0D) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                    addRejectionReason(eligiblity, "No topup eligibility found as eligibleAmount is 0");
                    logger.info("No topup eligibility found for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
                    int posAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
                    if (eligibleAmount - posAmount < 10000) {
                        addRejectionReason(eligiblity, "Outstanding amount less than 10k");
                        logger.info("Outstanding amount less than 10k for merchant:{}", lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }
                }

                loanDetailsServiceV2.recomputeEligibleLoan(globalLimitResponse, eligibleAmount, lendingPaymentSchedule.getMerchantId(), false);
                eligibleLoanList = eligibleLoanDao.findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);
                Experian experian = experianDao.getByMerchantId(lendingPaymentSchedule.getMerchantId());
                experian.setEligibleAmount(eligibleAmount);
                experian.setLoanType("TOPUP");
                experianDao.save(experian);
            }
            double prevLoanUnpaidAmount = getPreviousLoanAmount(lendingPaymentSchedule);
            BigDecimal prevLoanUnpaidAmountBD = BigDecimal.valueOf(getPreviousLoanAmount(lendingPaymentSchedule));
            BigDecimal processingfee;
            if (!eligibleLoanList.isEmpty()) {
                eligibleLoanList.sort((o1, o2) -> (o2.getCreatedAt().compareTo(o1.getCreatedAt())));
                Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                LendingEligibleLoan eligibleLoan = eligibleLoanList.get(0);
                logger.info("eligible loan fetched: {}", eligibleLoan);

                LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingPaymentSchedule.getMerchantId());
                PricingExperiment pricingExperiment = null;
                if(pricingExpEnabled) {
                    pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                            eligibleLoan.getTenureInMonths(), (int) (lendingPaymentSchedule.getMerchantId()%10), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
                }
                LendingLenderPricing lenderPricing = lendingLenderPricingDao.findTop1BySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                        eligibleLoan.getTenureInMonths(), lendingApplication.getLender(), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");

                if(!ObjectUtils.isEmpty(pricingExperiment)) {
                    logger.info("pricing experiment fetched for {}: {}", lendingPaymentSchedule.getMerchantId(), pricingExperiment);
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(pricingExperiment.getProcessingFeeRate());
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    processingfee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                            .divide(new BigDecimal(100), 0, RoundingMode.CEILING);
                    BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
                    eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
                    loanUtil.setEligibleLoan(eligibleLoan, pricingExperiment.getInterestRate(), processingfee, eligibleLoan.getAmount(), lendingApplication.getLender());
                }
                else if(!ObjectUtils.isEmpty(lenderPricing)){
                    logger.info("LendingLenderPricing : {}", lenderPricing);
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(lenderPricing.getProcessingFeeRate());
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    processingfee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                            .divide(new BigDecimal(100), 0, RoundingMode.CEILING);
                    BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
                    eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
                    loanUtil.setEligibleLoan(eligibleLoan, lenderPricing.getInterestRate(), processingfee, eligibleLoan.getAmount(), lendingApplication.getLender());
                } else if(eligibleLoan.getAmount() !=null && eligibleLoan.getProcessingFeeRate() != null && prevLoanUnpaidAmountBD !=null){
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(eligibleLoan.getProcessingFeeRate());
                    processingfee = amountBD.subtract(prevLoanUnpaidAmountBD)
                            .multiply(processingFeeRateBD)
                            .setScale(0, RoundingMode.CEILING);
                }
                else{
                    addRejectionReason(eligiblity, "Either loan amount or prevLoanunpainAmount or processing fee rate is null");
                    throw new IllegalArgumentException("Either loan amount or prevLoanunpainAmount or processing fee rate is null");
                }

                if (additionalTopupChecksFailed(lendingPaymentSchedule, eligibleLoan, lendingApplication)) {
                    addRejectionReason(eligiblity, "Additional topup checks failed");
                    log.info("additional topup checks failed for merchant id {}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
                loanEligibilityDTO.setActiveApplicationId(lendingPaymentSchedule.getId());
                loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
                loanEligibilityDTO.setInterestRate(eligibleLoan.getRateOfInterest());
                loanEligibilityDTO.setAmount(eligibleLoan.getAmount().intValue());
                loanEligibilityDTO.setCategory(eligibleLoan.getCategory());
                loanEligibilityDTO.setEdi(eligibleLoan.getEdi());
                loanEligibilityDTO.setRepayment(eligibleLoan.getRepayment());
                loanEligibilityDTO.setTenure(eligibleLoan.getTenure());
                loanEligibilityDTO.setConstruct(eligibleLoan.getLoanConstruct());
                loanEligibilityDTO.setOptionEnable(true);
                loanEligibilityDTO.setInterestAmount(eligibleLoan.getRepayment() - eligibleLoan.getAmount().intValue());
                loanEligibilityDTO.setIoEdiCount(eligibleLoan.getIoEdiDays());
                loanEligibilityDTO.setIoEdi(eligibleLoan.getIoEdi());
                loanEligibilityDTO.setTenureInMonths(eligibleLoan.getTenureInMonths());
//              loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
//              loanEligibilityDTO.setType();
                loanEligibilityDTO.setPrincipleEdiTenure(eligibleLoan.getTenureInMonths());
                //loanEligibilityDTO.setProcessingFee((int) Math.ceil((eligibleLoan.getAmount() - (int) prevLoanUnpaidAmount) * eligibleLoan.getProcessingFeeRate()));
                loanEligibilityDTO.setProcessingFee(processingfee.intValue());
                loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getAmount() - (int) prevLoanUnpaidAmount - loanEligibilityDTO.getProcessingFee());
                loanEligibilityDTO.setLoanType("TOPUP");
                loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
                loanEligibilityDTO.setId(eligibleLoan.getId());
                loanEligibilityDTO.setParentLender(lendingApplication.getLender());
                loanEligibilityDTO.setParentLan(lendingApplication.getNbfcId());
                loanEligibilityDTO.setParentLoanNo(lendingApplication.getExternalLoanId());
                eligiblity.add(loanEligibilityDTO);
                loanEligibilityDTO.setApr(Double.valueOf(df.format(eligibleLoan.getApr())));
                loanEligibilityDTO.setIrr(Double.valueOf(df.format(eligibleLoan.getIrr())));
            }

        } catch (Exception e) {
            addRejectionReason(eligiblity, "Exception occurred while existing topup rule engine");
            logger.info("Exception occurred while existing topup rule engine for merchantId: {} {}", lendingPaymentSchedule.getMerchantId(), Arrays.asList(e.getStackTrace()));
        }
        return eligiblity;
    }

    public CommonResponse getDueAmount(Long merchantId, Long merchantStoreId, BasicDetailsDto basicDetailsDto) {
        if (basicDetailsDto == null) {
            final Optional<BasicDetailsDto> basicDetailsDtoOptional = merchantService.fetchMerchantBasicDetails(merchantId);
            if (basicDetailsDtoOptional.isPresent())
                basicDetailsDto = basicDetailsDtoOptional.get();
        }
        if (basicDetailsDto == null) {
            return new CommonResponse(false, "merchant does not exist");
        }
        Map<String, Double> responseMap = new HashMap<>();
        String dueAmountCacheKey = "DUE_AMT_" + basicDetailsDto.getId() + (ObjectUtils.isEmpty(merchantStoreId) ? "" : ("_" + merchantStoreId));
        try {
            Object dueAmountCached = lendingCache.get(dueAmountCacheKey);
            if (!ObjectUtils.isEmpty(dueAmountCached)) {
                responseMap.put("due_amount", (Double) dueAmountCached);
                return new CommonResponse(responseMap);
            }
        } catch (Exception e) {
            logger.error("exception occurred while retrieving data from redis for: {} {}", basicDetailsDto.getId(), e.getMessage());
        }
        Double dueAmount = 0D;
        List<LendingPaymentScheduleSlave> activeLoans = fetchLendingPaymentScheduleSlave(basicDetailsDto.getId(), merchantStoreId, "ACTIVE");
        if (!activeLoans.isEmpty()) {
            for (LendingPaymentScheduleSlave activeLoan : activeLoans) {
                dueAmount += activeLoan.getDueAmount();
                dueAmount += Objects.nonNull(activeLoan.getDuePenalty()) ? activeLoan.getDuePenalty() : 0d;
            }
        }

        Double creditCardDueAmount = getCreditCardDueAmount(basicDetailsDto, merchantStoreId);

        Double goldLoanDueAmount = getGoldLoanDueAmount(basicDetailsDto, merchantStoreId);

        logger.info("dueAmount : {}, creditCardDueAmount : {}, goldLoanDueAmount : {} for merchantId : {}", dueAmount, creditCardDueAmount, goldLoanDueAmount, basicDetailsDto.getId());

        dueAmount += creditCardDueAmount + goldLoanDueAmount;
        responseMap.put("due_amount", dueAmount);
        cacheDueAmtData(dueAmount,dueAmountCacheKey, dueAmountCachingWindow);
        return new CommonResponse(responseMap);
    }

    private void cacheDueAmtData(Double dueAmt, String key, int ttl) {
        try {
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(key);
            addCacheDto.setValue(dueAmt);
            addCacheDto.setTtl(ttl);
            lendingCache.add(addCacheDto, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.error("exception occured while caching loan details for {} !!", key);
        }
    }

    private Double getCreditCardDueAmount(BasicDetailsDto basicDetailsDto, Long merchantStoreId) {
        Double creditCardDueAmount = null;
        String ccDueAmountCacheKey = "CC_DUE_AMT_" + basicDetailsDto.getId() + (ObjectUtils.isEmpty(merchantStoreId) ? "" : ("_" + merchantStoreId));
        try {
            Object ccDueAmountCached = lendingCache.get(ccDueAmountCacheKey);
            if (!ObjectUtils.isEmpty(ccDueAmountCached)) {
                creditCardDueAmount = (Double) ccDueAmountCached;
            }
        } catch (Exception e) {
            logger.error("exception occurred while retrieving creditCardDueAmount from redis for: {} {}", basicDetailsDto.getId(), Arrays.asList(e.getStackTrace()));
        }

        if (ObjectUtils.isEmpty(creditCardDueAmount)) {
            logger.info("fetching creditCardDueAmount from api for merchantId {} and merchantStoreId {}", basicDetailsDto.getId(),  merchantStoreId);
            creditCardDueAmount = apiGatewayService.getCreditCardDueAmount(basicDetailsDto.getId());
            logger.info("creditCardDueAmount from api for merchantId {} and merchantStoreId {} is {}", basicDetailsDto.getId(),  merchantStoreId, creditCardDueAmount);
            cacheDueAmtData(creditCardDueAmount, ccDueAmountCacheKey, ccDueAmountCachingWindow);
        }
        return creditCardDueAmount;
    }

    private Double getGoldLoanDueAmount(BasicDetailsDto basicDetailsDto, Long merchantStoreId) {
        Double goldLoanDueAmount = null;
        String goldLoanDueAmountCacheKey = "GOLD_LOAN_DUE_AMT_" + basicDetailsDto.getId() + (ObjectUtils.isEmpty(merchantStoreId) ? "" : ("_" + merchantStoreId));
        try {
            Object goldLoanDueAmountCached = lendingCache.get(goldLoanDueAmountCacheKey);
            if (!ObjectUtils.isEmpty(goldLoanDueAmountCached)) {
                goldLoanDueAmount = (Double) goldLoanDueAmountCached;
            }
        } catch (Exception e) {
            logger.error("exception occurred while retrieving goldLoanDueAmount from redis for: {} {}", basicDetailsDto.getId(), Arrays.asList(e.getStackTrace()));
        }

        if (ObjectUtils.isEmpty(goldLoanDueAmount)) {
            logger.info("fetching goldLoanDueAmount from api for merchantId {} and merchantStoreId {}", basicDetailsDto.getId(),  merchantStoreId);
            goldLoanDueAmount = apiGatewayService.getGoldLoanDueAmount(basicDetailsDto.getId());
            logger.info("goldLoanDueAmount from api for merchantId {} and merchantStoreId {} is {}", basicDetailsDto.getId(),  merchantStoreId, goldLoanDueAmount);
            cacheDueAmtData(goldLoanDueAmount, goldLoanDueAmountCacheKey, goldLoanDueAmountCachingWindow);
        }
        return goldLoanDueAmount;
    }

    public CommonResponse checkMerchant(String mobile, String pancard) {
        try {
            logger.info("Request to check merchant for mobile:{} and pancard:{}", mobile, pancard);
            boolean isBpMerchant = false;
            Optional<BasicDetailsDto> basicDetailsDto = null;
            if (!StringUtils.isEmpty(mobile)) {
                if (mobile.length() == 10) {
                    mobile = "91" + mobile;
                }
//                merchant = merchantDao.findByMobile(mobile);
                basicDetailsDto = merchantService.fetchMerchantBasicDetailsByMobile(mobile);
            }
            if (basicDetailsDto == null && !StringUtils.isEmpty(pancard)) {
                Experian experian = experianDao.findByPancard(pancard);
                if (experian != null) {
//                    merchant = merchantDao.getById(experian.getMerchantId());
                    basicDetailsDto = merchantService.fetchMerchantBasicDetails(experian.getMerchantId());
                }
            }
            if (basicDetailsDto != null && basicDetailsDto.isPresent()) {
                List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(basicDetailsDto.get().getId(), false);
                for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
                    BigInteger maxDpd = loanDpdDaoSlave.findMaxDpd(lendingPaymentSchedule.getId());
                    if (maxDpd.intValue() >= 20) {
                        isBpMerchant = true;
                        break;
                    }
                    if (LoanUtil.getDateDiffInDays(lendingPaymentSchedule.getCreatedAt(), new Date()) <= 30) {
                        isBpMerchant = true;
                        break;
                    }
                    if (LoanUtil.calculateDPD(lendingPaymentSchedule.getEdiAmount(), lendingPaymentSchedule.getDueAmount()) > 5) {
                        isBpMerchant = true;
                        break;
                    }
                }
                if (!isBpMerchant) {
                    LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(basicDetailsDto.get().getId(), "rejected");
                    if (lendingApplication != null && LoanUtil.getDateDiffInDays(lendingApplication.getUpdatedAt(), new Date()) <= 30) {
                        isBpMerchant = true;
                    }
                }
            }
            if (isBpMerchant) {
                return new CommonResponse(true, "BP Merchant");
            }
        } catch (Exception e) {
            logger.error("Exception while checking merchant", e);
        }
        return new CommonResponse(false, "merchant not found");
    }

    private List<LoanEligibilityDTO> computeEligibility(LendingPaymentScheduleSlave lendingPaymentSchedule, boolean createTopupApplicationCheck, LendingApplication lendingApplication) {
        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();

        try {

            if(!topupLenders.contains(lendingPaymentSchedule.getNbfc())){
                addRejectionReason(eligiblity, "Topup not enabled on lender");
                logger.info("Topup not enabled on lender:{}",lendingPaymentSchedule.getNbfc());
                return eligiblity;
            }

            Long experianId = null;
            Boolean sevenDayFlag = LenderOffDays.valueOf(lendingPaymentSchedule.getLoanApplication().getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);
            List<LendingEligibleLoan> eligibleLoanList = null;
            if(!createTopupApplicationCheck){
                eligibleLoanList = eligibleLoanDao.findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);
            }

            if (ObjectUtils.isEmpty(eligibleLoanList)) {
                Double eligibleAmount = 0D;
                GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(lendingPaymentSchedule.getMerchantId(),EligibilityRequestSource.EASY_LOANS);
                if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
                    logger.info("Global limit for merchant:{} is {}", lendingPaymentSchedule.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
                    eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                }
                if (eligibleAmount.equals(0D) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                    addRejectionReason(eligiblity, "No topup eligibility found as eligibleAmount is 0");
                    logger.info("No topup eligibility found for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                int posAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
                if (eligibleAmount - posAmount < 10000) {
                    addRejectionReason(eligiblity, "Outstanding amount less than 10k");
                    logger.info("Outstanding amount less than 10k for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                loanDetailsServiceV2.recomputeEligibleLoan(globalLimitResponse, eligibleAmount, lendingPaymentSchedule.getMerchantId(), false);
                eligibleLoanList = eligibleLoanDao.findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);
                Experian experian = experianDao.getByMerchantId(lendingPaymentSchedule.getMerchantId());
                experian.setEligibleAmount(eligibleAmount);
                experian.setLoanType("TOPUP");
                experianDao.save(experian);
                experianId = experian.getId();
            }
            double prevLoanUnpaidAmount = getPreviousLoanAmount(lendingPaymentSchedule);
            BigDecimal prevLoanUnpaidAmountBD = BigDecimal.valueOf(getPreviousLoanAmount(lendingPaymentSchedule));
            BigDecimal processingFee;
            if (!eligibleLoanList.isEmpty()) {
                eligibleLoanList.sort((o1, o2) -> (o2.getCreatedAt().compareTo(o1.getCreatedAt())));
                Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                LendingEligibleLoan eligibleLoan = eligibleLoanList.get(0);
                logger.info("eligible loan fetched: {}", eligibleLoan);

                LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingPaymentSchedule.getMerchantId());
                PricingExperiment pricingExperiment = null;
                if(pricingExpEnabled) {
                    pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                            eligibleLoan.getTenureInMonths(), (int) (lendingPaymentSchedule.getMerchantId()%10), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
                }
                LendingLenderPricing lenderPricing = lendingLenderPricingDao.findTop1BySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                        eligibleLoan.getTenureInMonths(), lendingApplication.getLender(), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");

                if(!ObjectUtils.isEmpty(pricingExperiment)) {
                    logger.info("pricing experiment fetched for {}: {}", lendingPaymentSchedule.getMerchantId(), pricingExperiment);
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(pricingExperiment.getProcessingFeeRate());
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    processingFee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                            .divide(new BigDecimal(100), 0, RoundingMode.CEILING);
                    BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
                    eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
                    loanUtil.setEligibleLoan(eligibleLoan, pricingExperiment.getInterestRate(), processingFee, eligibleLoan.getAmount(), lendingApplication.getLender());
                }
                else if(!ObjectUtils.isEmpty(lenderPricing)){
                    logger.info("LendingLenderPricing : {}", lenderPricing);
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(lenderPricing.getProcessingFeeRate());
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    processingFee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                            .divide(new BigDecimal(100), 0, RoundingMode.CEILING);
                    BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
                    eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
                    loanUtil.setEligibleLoan(eligibleLoan, lenderPricing.getInterestRate(), processingFee, eligibleLoan.getAmount(), lendingApplication.getLender());
                } else if(eligibleLoan.getAmount() != null && eligibleLoan.getProcessingFeeRate() != null && prevLoanUnpaidAmountBD != null){
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(eligibleLoan.getProcessingFee());
                    processingFee = amountBD.subtract(prevLoanUnpaidAmountBD)
                            .multiply(processingFeeRateBD)
                            .setScale(0, RoundingMode.CEILING);
                }else{
                    throw new IllegalArgumentException("Either loan amount or prevLoanUnpaidAmount or processing fee rate is null");
                }

                if (additionalTopupChecksFailed(lendingPaymentSchedule, eligibleLoan, lendingApplication)) {
                    log.info("additional topup checks failed for merchant id {}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                logger.info("eligible loan: {}", eligibleLoan);
                LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
                loanEligibilityDTO.setActiveApplicationId(lendingPaymentSchedule.getId());
                loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
                loanEligibilityDTO.setInterestRate(eligibleLoan.getRateOfInterest());
                loanEligibilityDTO.setAmount(eligibleLoan.getAmount().intValue());
                loanEligibilityDTO.setCategory(eligibleLoan.getCategory());
                loanEligibilityDTO.setEdi(eligibleLoan.getEdi());
                loanEligibilityDTO.setRepayment(eligibleLoan.getRepayment());
                loanEligibilityDTO.setTenure(eligibleLoan.getTenure());
                loanEligibilityDTO.setConstruct(eligibleLoan.getLoanConstruct());
                loanEligibilityDTO.setOptionEnable(true);
                loanEligibilityDTO.setInterestAmount(eligibleLoan.getRepayment() - eligibleLoan.getAmount().intValue());
                loanEligibilityDTO.setIoEdiCount(eligibleLoan.getIoEdiDays());
                loanEligibilityDTO.setIoEdi(eligibleLoan.getIoEdi());
                loanEligibilityDTO.setTenureInMonths(eligibleLoan.getTenureInMonths());
//              loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
//              loanEligibilityDTO.setType();
                loanEligibilityDTO.setPrincipleEdiTenure(eligibleLoan.getTenureInMonths());
                //loanEligibilityDTO.setProcessingFee((int)Math.ceil((eligibleLoan.getAmount() - (int) prevLoanUnpaidAmount) * eligibleLoan.getProcessingFeeRate()));
                loanEligibilityDTO.setProcessingFee(processingFee.intValue());
                loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getAmount() - (int) prevLoanUnpaidAmount - loanEligibilityDTO.getProcessingFee());
                loanEligibilityDTO.setLoanType("TOPUP");
                loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
                loanEligibilityDTO.setId(eligibleLoan.getId());
                loanEligibilityDTO.setApr(Double.valueOf(df.format(eligibleLoan.getApr())));
                loanEligibilityDTO.setIrr(Double.valueOf(df.format(eligibleLoan.getIrr())));
                eligiblity.add(loanEligibilityDTO);
            }

        } catch (Exception ex) {
            addRejectionReason(eligiblity, "Exception occurred while checking derog test eligibilty for topup");
            logger.info("Exception Occured while checking derog test eligibilty for topup");
        }
        return eligiblity;
    }

    private double getPreviousLoanAmount(LendingPaymentScheduleSlave lendingPaymentSchedule) {
        double prevLoanUnpaidAmount = 0;
        double penaltyFee = Objects.nonNull(lendingPaymentSchedule.getDuePenalty()) ? lendingPaymentSchedule.getDuePenalty() : 0;
        if ("LDC".equalsIgnoreCase(lendingPaymentSchedule.getNbfc())) {
            prevLoanUnpaidAmount = loanUtil.getForeclosureAmountForLdc(lendingPaymentSchedule);
        } else {
            prevLoanUnpaidAmount = (lendingPaymentSchedule.getLoanAmount() - lendingPaymentSchedule.getPaidPrinciple())
                    + lendingPaymentSchedule.getDueInterest() + penaltyFee;
        }

        return prevLoanUnpaidAmount;
    }

    public double getPreviousLoanAmount(LendingPaymentSchedule lendingPaymentSchedule) {
        double previousAmount = 0;
        if ("LDC".equalsIgnoreCase(lendingPaymentSchedule.getNbfc())) {
            previousAmount = loanUtil.getForeclosureAmountForLdc(lendingPaymentSchedule);
        } else if (Arrays.asList(ABFL.name(), TRILLIONLOANS.name(), PIRAMAL.name()).contains(lendingPaymentSchedule.getNbfc())) {
            previousAmount = loanUtil.getForeClosureAmountForLender(lendingPaymentSchedule);
            if (previousAmount <= 0) {
                String error = String.format("Error getting foreclosure details for %s %s with loanId %s", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc(), lendingPaymentSchedule.getId());
                throw new RuntimeException(error);
            }
        } else {
            previousAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
        }
        return previousAmount;
    }

    private boolean maxIrrCheckFailed(Integer ediCount, Double ediAmount, Double loanAmount, Long merchantId, String lender) {
        Double irr = lendingApplicationServiceV2.getApr(ediCount, ediAmount, loanAmount, merchantId, lender);
        log.info("IRR generated for merchant id :{} IRR:{} and lender:{}", merchantId, irr, lender);
        Double maxIrr = 0D;
        switch (lender) {
            case "PIRAMAL":
                maxIrr = piramalMaxIrr;
                break;
            default:
                maxIrr = 0D;
        }
        return irr > maxIrr;
    }


    private boolean maxAprCheckFailed(Integer ediCount, Double ediAmount, Double loanAmount, Double processingFee, Long merchantId, String lender) {
        Double apr = lendingApplicationServiceV2.getApr(ediCount, ediAmount, loanAmount - processingFee, merchantId, lender);
        log.info("APR generated for merchant id:{} APR:{} and lender:{}", merchantId, apr, lender);
        Double maxApr = 0D;
        switch (lender) {
            case "PIRAMAL":
                maxApr = piramalMaxApr;
                break;
            default:
                maxApr = 0D;
        }
        return apr > maxApr;
    }

    private boolean additionalTopupChecksFailed(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingEligibleLoan eligibleLoan, LendingApplication lendingApplication) {
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingPaymentSchedule.getMerchantId());
        double vintage = !ObjectUtils.isEmpty(lendingRiskVariables.getVintage()) ? lendingRiskVariables.getVintage() : 0D;
//        Double amountPaidThroughQrPer = loanUtil.getAmountPaidThroughQrPer(lendingPaymentSchedule);
        int currentDPD = LoanUtil.calculateDPD(lendingPaymentSchedule.getEdiAmount(), lendingPaymentSchedule.getDueAmount());

        RiskVariablesDTO riskVariables = new RiskVariablesDTO();
        PricingExperiment pricingExperiment = null;
        if(pricingExpEnabled) {
            pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                    eligibleLoan.getTenureInMonths(), (int) (lendingApplication.getMerchantId()%10), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
        }
        if(!ObjectUtils.isEmpty(pricingExperiment)) {
            logger.info("experiment fetched for {}: {}", lendingPaymentSchedule.getMerchantId(), pricingExperiment);
            riskVariables.setPricingExperimentMap(Collections.singletonMap(lendingApplication.getMerchantId(), pricingExperiment));
        }else{
            LendingLenderPricing lenderPricing = lendingLenderPricingDao.findTop1BySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                    eligibleLoan.getTenureInMonths(), lendingApplication.getLender(), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
            riskVariables.setLenderPricingMap(Collections.singletonMap(lendingApplication.getLender(), lenderPricing));
        }

        if (lenderAssignService.maxIrrCheckFailedV2(eligibleLoan, LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel(), lendingApplication.getLender(), riskVariables)) {
            logger.info("max irr check failed for merchant id {}, lender {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc());
            return true;
        }
        if (lenderAssignService.maxAprCheckFailedV2(eligibleLoan, LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel(), lendingApplication.getLender(), riskVariables)) {
            logger.info("max apr check failed for merchant id {}, lender {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc());
            return true;
        }
        if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && vintage < 90) {
            logger.info("vintage check failed for merchant id {}, lender {} : {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc(), vintage);
            return true;
        }
        if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && currentDPD > piramalTopupMaxCurrentDpd) {
            logger.info("dpd check failed for merchant id {}, lender {} : {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc(), currentDPD);
            return true;
        }
//        if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && amountPaidThroughQrPer < 40) {
//            logger.info("amt paid through qr check failed for merchant id {}, lender {} : {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc(), amountPaidThroughQrPer);
//            return true;
//        }
        if(llBalanceBreHardRejectEnabled && LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
            LendingApplication prevApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(lendingPaymentSchedule.getMerchantId());
            if(!ObjectUtils.isEmpty(prevApplication) && LoanType.TOPUP.name().equalsIgnoreCase(prevApplication.getLoanType()) && "rejected".equalsIgnoreCase(prevApplication.getStatus())) {
                 LendingApplicationLenderDetailsSlave lendingApplicationLenderDetailsSlave = lendingApplicationLenderDetailsDaoSlave.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(prevApplication.getId(), Status.INACTIVE.name(), prevApplication.getLender());
                 if(!ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave) && !ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave.getBreRejectionReason())) {
                     log.info("BRE for liquiloans balance transfer application {} already failed with reason {} for merchantId {}", prevApplication.getId(), lendingApplicationLenderDetailsSlave.getBreRejectionReason(), lendingPaymentSchedule.getMerchantId());
                     return true;
                 }
            }
        }
        return false;
    }

    private void addRejectionReason(List<LoanEligibilityDTO> eligibility, String reason) {
        log.info("TopUp rejected:{}", reason);
        LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
        loanEligibilityDTO.setRejectionReason(reason);
        loanEligibilityDTO.setIsRejected(true);
        eligibility.add(loanEligibilityDTO);
    }

}
