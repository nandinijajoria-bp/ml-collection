package com.bharatpe.lending.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.PhonebookHandler;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.dto.PhonebookDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.EdiModel;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.query.dao.*;
import com.bharatpe.lending.common.query.entity.*;
import com.bharatpe.lending.common.query.entity.LendingLedgerSlave;
import com.bharatpe.lending.common.query.entity.LoanPaymentOrderSlave;
import com.bharatpe.lending.common.query.entity.PenaltyFeeConfigSlave;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.query.dao.AutoPayUPISlaveDao;
import com.bharatpe.lending.common.query.dao.LendingEDIScheduleQueryDao;
import com.bharatpe.lending.common.query.dao.LendingPrePaymentSlaveDao;
import com.bharatpe.lending.common.query.entity.LendingEDIScheduleQuery;
import com.bharatpe.lending.common.query.entity.AutoPayUPISlave;
import com.bharatpe.lending.common.query.entity.LendingPrepaymentSlave;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.bharatpe.lending.constant.LendingConstants.*;
import static com.bharatpe.lending.enums.Lender.ABFL;
import static com.bharatpe.lending.enums.Lender.LIQUILOANS_NBFC;
import static com.bharatpe.lending.service.impl.LenderAssignService.topupLenderMapper;

@Service
@Slf4j
public class MerchantLoansService {

    private Logger logger = LoggerFactory.getLogger(MerchantLoansService.class);

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;


    @Autowired
    AutoPayUPIDao autoPayUPIDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingLedgerSlaveDao lendingLedgerSlaveDao;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanDpdDao loanDpdDao;

    @Autowired
    LoanDpdDaoSlave loanDpdDaoSlave;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

//    @Autowired
//    MerchantSummaryDao merchantSummaryDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingEDIScheduleDao lendingEDIScheduleDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    EnachHandler enachHandler;

    @Value("${renach.rollout.date}")
    String renachRolloutDate;

    @Autowired
    LoanPaymentOrderDao loanPaymentOrderDao;

    @Autowired
    LoanPaymentOrderSlaveDao loanPaymentOrderSlaveDao;

    @Autowired
    LendingPrepaymentDao lendingPrepaymentDao;

//    @Autowired
//    MerchantDao merchantDao;

    @Autowired
    LendingIoHalfTopupDao lendingIoHalfTopupDao;

    @Autowired
    PhonebookHandler phonebookHandler;

    @Autowired
    S3BucketHandler s3BucketHandler;

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
    LendingPullPaymentDao pullPaymentDao;

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

    static List<String> LIQUILOANS_TOPUP_LENDERS = Arrays.asList("LIQUILOANS_P2P","LIQUILOANS_NBFC","LIQUILOANS_P2P_OF");

    static List<String> allowedRiskGroupsStp = Arrays.asList("R1", "R2");

    private final DecimalFormat df = new DecimalFormat("#.##");

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

    public LendingMerchantLoansResponseDTO getMerchantLoans(Long merchantId) {
        LendingMerchantLoansResponseDTO responseDTO = new LendingMerchantLoansResponseDTO();
        responseDTO.setTopup(Boolean.FALSE);
        List<LendingPaymentScheduleSlave> merchantLoans = lendingPaymentScheduleDaoSlave.findByMerchantIdAndCreditLoan(merchantId, false);
        responseDTO.setAccountDetails(loanUtil.getAccountDetails(merchantId));
        if (merchantLoans == null || merchantLoans.isEmpty()) {
            logger.info("No loans found for merchantId: {}", merchantId);
            responseDTO.setLoans(Collections.emptyList());
            responseDTO.setMessage("No merchant loans found");
            responseDTO.setSuccess(false);
        } else {
            logger.info("{} loans found for merchantId: {}", merchantLoans.size(), merchantId);
            responseDTO.setLoansFromLendingPaymentSchedule(merchantLoans);


            for (LendingMerchantLoansResponseDTO.Loan loan : responseDTO.getLoans()) {
                LendingLedgerSlave lendingLedger = lendingLedgerSlaveDao.findLastPaymentEntryByMerchantAndLoan(merchantId, loan.getLoanId());
                if(loan.getStatus().equals("ACTIVE")) {
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
                double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
                loan.setPaidAmount((ObjectUtils.isEmpty(loan.getPaidAmount()) ? 0 : loan.getPaidAmount()) + advanceEdiAmount);
                loan.setPendingAmount((ObjectUtils.isEmpty(loan.getPendingAmount()) ? 0 : loan.getPendingAmount()) - advanceEdiAmount);
                loan.setPaidPrinciple((ObjectUtils.isEmpty(loan.getPaidPrinciple()) ? 0 : loan.getPaidPrinciple()) + advanceEdiAmount);
                loan.setEdiDays(loan.getEdiCount() % 30 == 0 ? 7 : 6);

                PenalCharges penalCharges = penalChargesDao.findByLoanId(loan.getLoanId());
                double duePenalty = Objects.nonNull(penalCharges) ? penalCharges.getDuePenalty() : (Objects.nonNull(loan.getDuePenalty()) ? loan.getDuePenalty() : 0);
                loan.setDuePenalty(duePenalty);
                loan.setNachBounceAmount(Objects.nonNull(penalCharges) ? penalCharges.getDueNachBounce() : 0);

                if (loan.getStatus().equals("ACTIVE")) {
                    responseDTO.setShowChangeBankAccountBanner(showChangeBankAccountBanner(responseDTO.getAccountDetails(), merchantId));
                    LendingPullPaymentSlave pullPayment = lendingPullPaymentDaoSlave.findTop1ByMerchantIdAndModeOrderByIdDesc(merchantId, "AUTOPAYUPI");
                    if (pullPayment != null) {
                        Double amount = pullPayment.getDeductedAmount();
                        String status = pullPayment.getStatus();
                        Long id = loan.getLoanId();
                        logger.info("loan id is {}",id);
                        loan.setPresentmentStatus(status);
                        loan.setPresentmentAmount(amount);
                        log.info("lending pull payment Updated Date is {}", pullPayment.getUpdatedAt());
                        loan.setPresentmentDate(pullPayment.getUpdatedAt());
                    }

                    log.info("loan application id is loan.getApplicationId{}", loan.getApplicationId());
                    Optional<AutoPayUPISlave> autoPayUPI = autoPayUPISlaveDao.findTop1ByMerchantIdAndApplicationIdOrderByIdDesc(merchantId, loan.getApplicationId());
                    if (autoPayUPI.isPresent()) {
                        /*
                        log.info("autoPay UPI is present {}",autoPayUPI);
                        if (autoPayUPI.get().getStatus().equals(AutoPayStatusEnum.PENDING) ||
                                autoPayUPI.get().getStatus().equals(AutoPayStatusEnum.INIT))
                        {
                            Date createdMandateDate = autoPayUPI.get().getCreatedAt();
                            long diffMinutes = calculateTimeDiff(createdMandateDate);
                            log.info("diffMinutes is {}", diffMinutes);
                            if (diffMinutes >= 30L) {
                                autoPayUPI.get().setStatus(AutoPayStatusEnum.FAILED);
                                autoPayUPIDao.save(autoPayUPI.get());
                                log.info("status for mandate register marked as failed due to tat for merchant id {} application id {}",
                                        autoPayUPI.get().getMerchantId(), autoPayUPI.get().getApplicationId());
                            }
                        }
                         */
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

                    responseDTO.setShowRenachBanner(showRenachBanner(merchantId, loan.getLender(), responseDTO.getShowChangeBankAccountBanner()));
                }
            }

            LendingPaymentScheduleSlave lendingPaymentSchedule = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(merchantId, "ACTIVE");
            if (Objects.nonNull(lendingPaymentSchedule)) {
                List<PenaltyFeeConfigSlave> penaltyFeeConfigSlaves = penaltyFeeConfigDaoSlave.findByVersionAndStatusAndLenderOrderByMinAmountAsc
                        (2D, true, lendingPaymentSchedule.getNbfc());

                List<LendingMerchantLoansResponseDTO.PenaltyConfig> penaltyConfigs = new ArrayList<>();

                for (PenaltyFeeConfigSlave penaltyFeeConfigSlave : penaltyFeeConfigSlaves) {
                    LendingMerchantLoansResponseDTO.PenaltyConfig penaltyConfig = new LendingMerchantLoansResponseDTO.PenaltyConfig();
                    penaltyConfig.setMinAmount(penaltyFeeConfigSlave.getMinAmount());
                    penaltyConfig.setMaxAmount(penaltyFeeConfigSlave.getMaxAmount());
                    penaltyConfig.setPenalty(penaltyFeeConfigSlave.getPenalty());
                    penaltyConfigs.add(penaltyConfig);
                }
                responseDTO.setPenaltyConfig(penaltyConfigs);
            }

            if (lendingPaymentSchedule != null) {
                Date date = new Date();
                if (date.after(lendingPaymentSchedule.getStartDate())) {
                    responseDTO.setEdiStarted(Boolean.TRUE);
                } else {
                    responseDTO.setEdiStarted(Boolean.FALSE);
                }
                List<LendingMerchantLoansResponseDTO.RepaymentDetails> repaymentDetailsList = new ArrayList<>();
                List<LoanPaymentOrderSlave> loanPaymentOrderList = loanPaymentOrderSlaveDao.findRecentTransactions(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getMerchantId());
                if (loanPaymentOrderList != null) {
                    for (LoanPaymentOrderSlave loanPaymentOrder : loanPaymentOrderList) {
                        LendingMerchantLoansResponseDTO.RepaymentDetails repaymentDetails = new LendingMerchantLoansResponseDTO.RepaymentDetails();
                        repaymentDetails.setAmount(loanPaymentOrder.getAmount());
                        repaymentDetails.setDate(loanPaymentOrder.getCreatedAt());
                        repaymentDetails.setMode(LoanUtil.settlementMode.getOrDefault(loanPaymentOrder.getSource(), "UPI"));
                        repaymentDetails.setStatus(loanPaymentOrder.getStatus());
                        repaymentDetails.setOrderId(loanPaymentOrder.getOrderId());
                        repaymentDetailsList.add(repaymentDetails);
                    }
                    responseDTO.setRepaymentDetails(repaymentDetailsList);
                }

                /*
                *@Deprecated
                * Removing penny drop check before eligibilty check, penny drop check now on agreement stage
                * EL - 2775
                *
                boolean pennyDrop = loanUtil.checkPennyDropV2(lendingPaymentSchedule.getMerchantId());
                if (pennyDrop) {
                    try {
                        List<LoanEligibilityDTO> loans = topupLoan(lendingPaymentSchedule);
                        if (!loans.isEmpty()) {
                            responseDTO.setEligibility(loans);
                            responseDTO.setTopup(Boolean.TRUE);
//                            responseDTO.setTopupLender(!Lender.LDC.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) ? Lender.LDC.name() : Lender.MAMTA.name());
                            responseDTO.setTopupLender(topupLenderMapper(lendingPaymentSchedule.getNbfc()));
                        }
                        if("ABFL".equalsIgnoreCase(lendingPaymentSchedule.getNbfc())) {
                            responseDTO.setTopupRejected(checkForTopupRejection(lendingPaymentSchedule.getMerchantId()));
                        }
                    } catch (Exception e) {
                        logger.error("Exception while calculating TOPUP loan for merchant:{}", merchantId, e);
                    }
                    if (baseChecksForHalfAndIOEdi(lendingPaymentSchedule, responseDTO)) {
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
                }
                 */

//                responseDTO.setContactSync(isContactSyncRequired(lendingPaymentSchedule));

                try {
                    List<LoanEligibilityDTO> loans = topupLoan(lendingPaymentSchedule);
                    if (!loans.isEmpty()) {
                        responseDTO.setEligibility(loans);
                        responseDTO.setTopup(Boolean.TRUE);
//                            responseDTO.setTopupLender(!Lender.LDC.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) ? Lender.LDC.name() : Lender.MAMTA.name());
                        responseDTO.setTopupLender(topupLenderMapper(lendingPaymentSchedule.getNbfc()));
                    }
                    if("ABFL".equalsIgnoreCase(lendingPaymentSchedule.getNbfc())) {
                        responseDTO.setTopupRejected(checkForTopupRejection(lendingPaymentSchedule.getMerchantId()));
                    }
                } catch (Exception e) {
                    logger.error("Exception while calculating TOPUP loan for merchant:{}", merchantId, e);
                }
                if (baseChecksForHalfAndIOEdi(lendingPaymentSchedule, responseDTO)) {
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
            }

            responseDTO.getLoans().sort(Comparator.comparing(LendingMerchantLoansResponseDTO.Loan::getLoanId, Comparator.reverseOrder()));
            responseDTO.setMessage("Successfully fetched merchant loans");
            responseDTO.setSuccess(true);
        }
        return responseDTO;
    }

    private Boolean checkForTopupRejection(Long merchantId) {
        try {
            LendingApplication prevApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if(!ObjectUtils.isEmpty(prevApplication)) {
                if(LoanType.TOPUP.name().equalsIgnoreCase(prevApplication.getLoanType()) && "rejected".equalsIgnoreCase(prevApplication.getStatus())) {
                    log.info("latest application with topup loanType for merchantId : {}", prevApplication);
                    Long minutes = TimeUnit.MINUTES.toMinutes(new Date().getTime() - prevApplication.getUpdatedAt().getTime()) / 60000;
                    if(minutes < abflTopupRejectionBannerTat) {
                        log.info("topup application rejected for merchantId : {} less than {} minutes ago", merchantId, abflTopupRejectionBannerTat);
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
            int processingFee = loanUtil.getIoHalfPF(lendingPaymentSchedule);
            double loanAmount = Math.ceil((foreclosureAmount + processingFee) / 1000.0) * 1000;
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
            breakup.setProcessingFee(processingFee);
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
            EligibleLoan eligibleLoan = new EligibleLoan();
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

    public List<LoanEligibilityDTO> topupLoan(LendingPaymentScheduleSlave lendingPaymentSchedule) {
        List<Long> derogMerchants = loanUtil.loadDerogEffectedMerchants();
        List<Long> customEnabledMerchants = loanUtil.customEnabledTopupMerchants();

        List<LendingPaymentScheduleSlave> activeLoans = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatusList(lendingPaymentSchedule.getMerchantId(),"ACTIVE");

        if (activeLoans.size() > 1) {
            logger.info("more than 1 loan active for merchantId : {} loans : {}", lendingPaymentSchedule.getMerchantId(), activeLoans.size());
            return Collections.emptyList();
        }

        if (customEnabledMerchants.contains(lendingPaymentSchedule.getMerchantId())) {
            return computeEligibility(lendingPaymentSchedule);

        }

        if (pilotTestEnabled && derogMerchants.contains(lendingPaymentSchedule.getMerchantId()) && derogTopUpEnable(lendingPaymentSchedule.getMerchantId())) {
            return computeEligibility(lendingPaymentSchedule);
        }

        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();
        LendingApplication lendingApplication =
          lendingApplicationDao.findByIdAndMerchantId(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getMerchantId());
        try {
            if (!isTopUpEnabled) {
                logger.info("Topup are loans are disabled");
                return eligiblity;
            }
            if(!topupLenders.contains(lendingPaymentSchedule.getNbfc())){
                logger.info("Topup not enabled on lender:{}",lendingPaymentSchedule.getNbfc());
                return eligiblity;
            }

            if(LIQUILOANS_NBFC.toString().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && !easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), topupOnTltoTlRolloutPercent)){
                logger.info("LIQUILOANS_NBFC topup not enabled for merchantId:{}",lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if (!(easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(),rolloutTopupPercent)) && LIQUILOANS_TOPUP_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
                logger.info("Topup not enabled for this merchant :{}",lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if(ABFL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && !easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), abflTopupRolloutPercent) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                log.info("ABFL Topup not enabled for merchantId: {}", lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if(ABFL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc())){
                LenderTopupEligibility lenderTopupEligibility = lenderTopupEligibilityDao.findTopupEligibilityFromLender(
                        lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getNbfc());
                if(ObjectUtils.isEmpty(lenderTopupEligibility)){
                    log.info("Merchant not eligible from lender ABFL for merchantId: {}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
            }


            if (loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                Long experianId = null;

                Boolean sevenDayFlag = LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);


                List<EligibleLoan> eligibleLoanList = eligibleLoanDao.
                  findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);


                EligibleLoan internalMerchantLoan = new EligibleLoan(lendingPaymentSchedule.getMerchantId(), experianId, 300000D, "12 Months", "ACTIVE", null, 0, 0, null, 1149, 0, 357339, null, "TOPUP", null);
                internalMerchantLoan.setRateOfInterest(1.59);
                internalMerchantLoan.setProcessingFee(14130);
                internalMerchantLoan.setProcessingFeeRate(0.05D);
                internalMerchantLoan.setId(644147506L);
                eligibleLoanList.add(internalMerchantLoan);

                double prevLoanUnpaidAmount = getPreviousLoanAmount(lendingPaymentSchedule);
                if (!eligibleLoanList.isEmpty()) {
                    Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                    EligibleLoan eligibleLoan = eligibleLoanList.get(0);
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
                    loanEligibilityDTO.setProcessingFee((int) Math.ceil((eligibleLoan.getAmount() - (int) prevLoanUnpaidAmount) * eligibleLoan.getProcessingFeeRate()));
                    loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getAmount() - (int) prevLoanUnpaidAmount - loanEligibilityDTO.getProcessingFee());
                    loanEligibilityDTO.setLoanType("TOPUP");
                    loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
                    loanEligibilityDTO.setId(eligibleLoan.getId());
                    eligiblity.add(loanEligibilityDTO);
                }

                return eligiblity;

            }

            if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
                if (lendingApplication == null) {
                    logger.info("Lending Application not found/topup loan for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (LoanType.SMALL_TICKET.name().equals(lendingApplication.getLoanType())) {
                    logger.info("last loan is small ticket for merchant:{} with applicationId: {}", lendingPaymentSchedule.getMerchantId(), lendingApplication.getId());
                    return eligiblity;
                }
                if (!loanUtil.isEnachDone(lendingPaymentSchedule.getMerchantId())) {
                    logger.info("Nach not success for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                KycStatusDTO kycStatus = kycHandler.getKycStatus(lendingApplication.getMerchantId());
                if (!KycStatus.APPROVED.equals(kycStatus.getKycStatus())) {
                    logger.info("Kyc not approved for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                Integer age = apiGatewayService.getMerchantAge(lendingPaymentSchedule.getMerchantId());
                if (age > 0 && age < 21) {
                    logger.info("Age requirement not fulfilled for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                double dpd = lendingPaymentSchedule.getDueAmount() / lendingPaymentSchedule.getEdiAmount();
                if (dpd > 3D) {
                    logger.info("DPD is greater than 3 for merchant ID {}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                BigInteger maxDpd = loanDpdDaoSlave.findMaxDpd(lendingPaymentSchedule.getId());
                if (maxDpd.intValue() > 30) {
                    logger.info("Merchant Dpd Greater than 30 merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                double paidRatio = 0d;
                if (lendingPaymentSchedule.getPaidPrinciple() != null && lendingPaymentSchedule.getLoanAmount() != null) {
                    paidRatio = lendingPaymentSchedule.getPaidPrinciple() / lendingPaymentSchedule.getLoanAmount();
                }

                if (paidRatio >= 0.6D && paidRatio <= 0.95D) {
                    logger.info("paid ratio is between 60 to 95 of merchantId: {}", lendingPaymentSchedule.getMerchantId());
                    return ExistingTopupRuleEngine(lendingPaymentSchedule, lendingApplication);
                }
                if (paidRatio >= 0.5D && paidRatio < 0.60D && (!ABFL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()))) {
                    logger.info("paid ratio is between 50 to 60 of merchantId: {}", lendingPaymentSchedule.getMerchantId());
                    return AdditionalTopupRuleEngine(lendingPaymentSchedule, lendingApplication);
                }
            }
        } catch (Exception e) {
            logger.error("Exception occurred while checking eligibility for topup", e);
        }
        return eligiblity;
    }

    private List<LoanEligibilityDTO> AdditionalTopupRuleEngine(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingApplication lendingApplication) {
        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();
        try {
            Double settlementAmount = lendingLedgerDao.findSettlementAmount(lendingPaymentSchedule.getId());
            double qrPaidRatio = (settlementAmount / lendingPaymentSchedule.getPaidAmount()) * 100;
            if (qrPaidRatio < 80) {
                logger.info("QR payment less than 80% for merchant: {}", lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

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
                logger.info("Photo shop is not permanent of merchant: {} for last application: {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                return eligiblity;
            }
            Integer ediPaidCount = lendingLedgerDao.findLedgerCountOnAmountGreaterThanEdiAmount(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getEdiAmount());
            int paidCount = lendingPaymentSchedule.getEdiCount() - lendingPaymentSchedule.getEdiRemainingCount();
            logger.info("ediPaidCount:{} and paidCount:{} for merchant:{}", ediPaidCount, paidCount, lendingPaymentSchedule.getMerchantId());
            double ediPaidRatio = (ediPaidCount * 1.0 / paidCount) * 100;

            Long experianId = null;

            Boolean sevenDayFlag = LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);

            List<EligibleLoan> eligibleLoanList = eligibleLoanDao.
                    findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);

            if (loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                EligibleLoan internalMerchantLoan = new EligibleLoan(lendingPaymentSchedule.getMerchantId(), experianId, 300000D, "12 Months", "ACTIVE", null, 0, 0, null, 1149, 0, 357339, null, "TOPUP", null);
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
                    logger.info("Risk group is not R1 or R2 of new loan of merchant: {}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
                    logger.info("Global limit for merchant:{} is {}", lendingPaymentSchedule.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
                    eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                }
                if (eligibleAmount.equals(0D) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                    logger.info("No topup eligibility found for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                eligibleAmount = Math.min(eligibleAmount, lendingPaymentSchedule.getLoanAmount());
                if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
                    if (ediPaidRatio < 50D) {
                        logger.info("EDI paid ratio:{} is less than 50% for merchant:{}", ediPaidRatio, lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }
                    int posAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
                    if (eligibleAmount - posAmount < 10000) {
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
                experianId = experian.getId();
            }
            double prevLoanUnpaidAmount = getPreviousLoanAmount(lendingPaymentSchedule);
            if (!eligibleLoanList.isEmpty()) {
                Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                EligibleLoan eligibleLoan = eligibleLoanList.get(0);
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
                loanEligibilityDTO.setProcessingFee((int) Math.ceil((eligibleLoan.getAmount() - (int) prevLoanUnpaidAmount) * eligibleLoan.getProcessingFeeRate()));
                loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getAmount() - (int) prevLoanUnpaidAmount - loanEligibilityDTO.getProcessingFee());
                loanEligibilityDTO.setLoanType("TOPUP");
                loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
                loanEligibilityDTO.setId(eligibleLoan.getId());
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
            logger.info("Exception occurred in Additional Topup Rule Engine for merchantId: {}", lendingPaymentSchedule.getMerchantId());
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

    private List<LoanEligibilityDTO> ExistingTopupRuleEngine(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingApplication lendingApplication) {
        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();
        try {
            Double settlementAmount = lendingLedgerDao.findSettlementAmount(lendingPaymentSchedule.getId());
            double qrPaidRatio = (settlementAmount / lendingPaymentSchedule.getPaidAmount()) * 100;
            if (qrPaidRatio < 70) {
                logger.info("QR payment less than 70% for merchant:{}", lendingPaymentSchedule.getMerchantId());
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
                logger.info("Photo not of a shop found for merchant: {} for last application: {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                return eligiblity;
            }
            Integer ediPaidCount = lendingLedgerDao.findLedgerCountOnAmountGreaterThanEdiAmount(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getEdiAmount());
            int paidCount = lendingPaymentSchedule.getEdiCount() - lendingPaymentSchedule.getEdiRemainingCount();
            logger.info("ediPaidCount:{} and paidCount:{} for merchant:{}", ediPaidCount, paidCount, lendingPaymentSchedule.getMerchantId());
            double ediPaidRatio = (ediPaidCount * 1.0 / paidCount) * 100;

            //Double eligibleAmount = 0D;
//            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(lendingPaymentSchedule.getMerchantId());
//            if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
//                logger.info("Global limit for merchant:{} is {}", lendingPaymentSchedule.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
//                eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
//            }
//            if (eligibleAmount.equals(0D)) {
//                logger.info("No topup eligibility found for merchant:{}", lendingPaymentSchedule.getMerchantId());
//                return eligiblity;
//            }
//            if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
//                if (ediPaidRatio < 65D) {
//                    logger.info("EDI paid ratio:{} is less than 65% for merchant:{}", ediPaidRatio, lendingPaymentSchedule.getMerchantId());
//                    eligibleAmount = Math.min(eligibleAmount, lendingPaymentSchedule.getLoanAmount());
//                }
//                int posAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
//                if (eligibleAmount - posAmount < 10000) {
//                    logger.info("Outstanding amount less than 10k for merchant:{}", lendingPaymentSchedule.getMerchantId());
//                    return eligiblity;
//                }
//            }

            Long experianId = null;
            Boolean sevenDayFlag = LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);
            List<EligibleLoan> eligibleLoanList = eligibleLoanDao.findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);

            if (loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                EligibleLoan internalMerchantLoan = new EligibleLoan(lendingPaymentSchedule.getMerchantId(), experianId, 300000D, "12 Months", "ACTIVE", null, 0, 0, null, 1149, 0, 357339, null, "TOPUP", null);
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
                    logger.info("No topup eligibility found for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
                    if (ediPaidRatio < 65D) {
                        logger.info("EDI paid ratio:{} is less than 65% for merchant:{}", ediPaidRatio, lendingPaymentSchedule.getMerchantId());
                        eligibleAmount = Math.min(eligibleAmount, lendingPaymentSchedule.getLoanAmount());
                    }
                    int posAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
                    if (eligibleAmount - posAmount < 10000) {
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
                experianId = experian.getId();
            }
            double prevLoanUnpaidAmount = getPreviousLoanAmount(lendingPaymentSchedule);
            if (!eligibleLoanList.isEmpty()) {
                Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                EligibleLoan eligibleLoan = eligibleLoanList.get(0);
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
                loanEligibilityDTO.setProcessingFee((int) Math.ceil((eligibleLoan.getAmount() - (int) prevLoanUnpaidAmount) * eligibleLoan.getProcessingFeeRate()));
                loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getAmount() - (int) prevLoanUnpaidAmount - loanEligibilityDTO.getProcessingFee());
                loanEligibilityDTO.setLoanType("TOPUP");
                loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
                loanEligibilityDTO.setId(eligibleLoan.getId());
                eligiblity.add(loanEligibilityDTO);
            }

//            List<LendingCategories> lendingCategories = lendingCategoryDao.getByMasterCategoryForConstruct1("TOPUP");
//            LoanCalculationUtil.LoanBreakupDetail breakup;
//            AvailableLoan availableLoan = new AvailableLoan();
//            availableLoan.setAmount(eligibleAmount);

//            for (LendingCategories category : lendingCategories) {

//                breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, category, "TOPUP");
//                EligibleLoan eligibleLoan = new EligibleLoan();
//                eligibleLoan.setMerchantId(lendingPaymentSchedule.getMerchantId());
//                eligibleLoan.setExperianId(experian.getId());
//                eligibleLoan.setTenure(category.getPayableConverter());
//                eligibleLoan.setStatus("ACTIVE");
//                eligibleLoan.setAmount(eligibleAmount);
//                eligibleLoan.setCategory(category.getCategory());
//                eligibleLoan.setEdi(breakup.getEdi());
//                eligibleLoan.setIoEdi(breakup.getIoEdi());
//                eligibleLoan.setRepayment(breakup.getRepayment());
//                eligibleLoan.setLoanConstruct(breakup.getConstruct());
//                eligibleLoan.setLoanType("TOPUP");
//                eligibleLoanDao.save(eligibleLoan);

//                double prevLoanUnpaidAmount = (lendingPaymentSchedule.getLoanAmount() - lendingPaymentSchedule.getPaidPrinciple()) + lendingPaymentSchedule.getDueInterest();
//                LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
//                loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
//                loanEligibilityDTO.setDisbursementAmount(breakup.getDisbursementAmount());
//                loanEligibilityDTO.setProcessingFee(breakup.getProcessingFee());
//                loanEligibilityDTO.setInterestRate(breakup.getEffectiveInterestRate());
//                loanEligibilityDTO.setAmount(breakup.getLoanAmount());
//                loanEligibilityDTO.setCategory(category.getCategory());
//                loanEligibilityDTO.setInterestAmount(breakup.getTotalInterestAmount());
//                loanEligibilityDTO.setEdi(breakup.getEdi());
//                loanEligibilityDTO.setRepayment(breakup.getRepayment());
//                loanEligibilityDTO.setTenure(eligibleLoan.getTenure());
//                loanEligibilityDTO.setConstruct(breakup.getConstruct());
//                loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
//                loanEligibilityDTO.setType(breakup.getType());
//                loanEligibilityDTO.setOptionEnable(true);
//                loanEligibilityDTO.setPrincipleEdiTenure(breakup.getPrincipleEdiTenure());
//                loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getDisbursementAmount() - (int) prevLoanUnpaidAmount);
//                loanEligibilityDTO.setLoanType("TOPUP");
//                loanEligibilityDTO.setEdiCount(category.getPayableDays());
//                eligiblity.add(loanEligibilityDTO);
//            }


//            int deleteNonTopup = eligibleLoanDao.deleteNonTopUp(lendingPaymentSchedule.getMerchantId());
        } catch (Exception e) {
            logger.info("Exception occurred while existing topup rule engine for merchantId: {}", lendingPaymentSchedule.getMerchantId());
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

    private List<LoanEligibilityDTO> computeEligibility(LendingPaymentScheduleSlave lendingPaymentSchedule) {
        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();

        try {

            if(!topupLenders.contains(lendingPaymentSchedule.getNbfc())){
                logger.info("Topup not enabled on lender:{}",lendingPaymentSchedule.getNbfc());
                return eligiblity;
            }

            Long experianId = null;
            Boolean sevenDayFlag = LenderOffDays.valueOf(lendingPaymentSchedule.getLoanApplication().getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);
            List<EligibleLoan> eligibleLoanList = eligibleLoanDao.findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);

            if (ObjectUtils.isEmpty(eligibleLoanList)) {
                Double eligibleAmount = 0D;
                GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(lendingPaymentSchedule.getMerchantId(),EligibilityRequestSource.EASY_LOANS);
                if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
                    logger.info("Global limit for merchant:{} is {}", lendingPaymentSchedule.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
                    eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                }
                if (eligibleAmount.equals(0D) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                    logger.info("No topup eligibility found for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                int posAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
                if (eligibleAmount - posAmount < 10000) {
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
            if (!eligibleLoanList.isEmpty()) {
                Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                EligibleLoan eligibleLoan = eligibleLoanList.get(0);
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
                loanEligibilityDTO.setProcessingFee((int)Math.ceil((eligibleLoan.getAmount() - (int) prevLoanUnpaidAmount) * eligibleLoan.getProcessingFeeRate()));
                loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getAmount() - (int) prevLoanUnpaidAmount - loanEligibilityDTO.getProcessingFee());
                loanEligibilityDTO.setLoanType("TOPUP");
                loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
                loanEligibilityDTO.setId(eligibleLoan.getId());
                eligiblity.add(loanEligibilityDTO);
            }

        } catch (Exception ex) {
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
}
