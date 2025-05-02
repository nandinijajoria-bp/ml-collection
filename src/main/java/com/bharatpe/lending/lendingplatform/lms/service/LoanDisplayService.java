package com.bharatpe.lending.lendingplatform.lms.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.EdiModel;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.query.dao.*;
import com.bharatpe.lending.common.query.entity.*;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.CommonResponse;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.dto.LendingMerchantLoansResponseDTO;
import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.lendingplatform.lms.dto.response.LoanDetailsResponse;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bharatpe.lending.constant.LendingConstants.PAYTM;
import static com.bharatpe.lending.constant.LendingConstants.TOPUP_PILOT_IDENTIFIER;
import static com.bharatpe.lending.enums.Lender.*;
import static com.bharatpe.lending.enums.SettlementDetailsStatus.INIT;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ONE_LMS;
import static com.bharatpe.lending.service.impl.LenderAssignService.topupLenderMapper;

@Service
@Slf4j
public class LoanDisplayService {

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanDpdDaoSlave loanDpdDaoSlave;

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

    @Autowired
    LoanPaymentOrderSlaveDao loanPaymentOrderSlaveDao;

    @Autowired
    LendingIoHalfTopupDao lendingIoHalfTopupDao;

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
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

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

    @Value("${trillion.topup.rollout.percent:1}")
    Integer trillionTopupRolloutPercent;

    @Value("${trillion.topup.rejection.banner.tat:5}")
    Long trillionTopupRejectionBannerTat;

    @Autowired
    LoanUtilV3 loanUtilV3;

    @Autowired
    LendingApplicationLenderDetailsDaoSlave lendingApplicationLenderDetailsDaoSlave;

    @Value("${topup.min.qrpaidRatio:40}")
    Double topupMinQrPaidRatio;

    @Value("${piramal.topup.rollout.percent:}")
    Integer piramalTopupRolloutPercent;

    @Value(("${topup.pilot.run.enabled.lenders:ABFL}"))
    String topupPilotRunEnabledLenders;

    static List<String> LIQUILOANS_TOPUP_LENDERS = Arrays.asList("LIQUILOANS_P2P", "LIQUILOANS_NBFC", "LIQUILOANS_P2P_OF");

    static List<String> allowedRiskGroupsStp = Arrays.asList("R1", "R2");

    private final DecimalFormat df = new DecimalFormat("#.##");

    @Value("${piramal.topup.rejection.banner.tat:5}")
    Long piramalTopupRejectionBannerTat;

    @Value("${topup.disabled.startTime:22:00}")
    String topupDisabledStartTimeString;

    @Value("${topup.disabled.endTime:10:00}")
    String topupDisabledEndTimeString;

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

    @Value("${ll.balance.transfer.loan.edi.paid.ratio.threshold:50}")
    Double llBalanceTransferLoanEdiPaidRatioThreshold;

    @Autowired
    LmsLoanDetailsService lmsLoanDetailsService;

    @Value("${upi.percent}")
    Integer upiPercent;

    @Autowired
    SettlementDetailsDao settlemetDetailsDao;

    @Autowired
    LmsPaymentDetailsDao lmsPaymentDetailsDao;

    @Lazy
    @Autowired
    private LendingApplicationServiceV2 lendingApplicationServiceV2;

    private Logger logger = LoggerFactory.getLogger(LoanDisplayService.class);

    public LendingMerchantLoansResponseDTO setLendingMerchantLoansForOneLms(String token, LendingMerchantLoansResponseDTO responseDTOFromOneLms,
                                                                            List<LendingPaymentSchedule> merchantLoansFromOneLms, Long merchantId) {


        // The merchantLoansMap is created by converting a list of LendingPaymentScheduleSlave objects into a map
        // where the key is the loan ID and the value is the corresponding LendingPaymentScheduleSlave object.
        // This allows for O(1) time complexity for lookups by loan ID, which is efficient for larger sets.

        Map<Long, LendingPaymentSchedule> merchantLoansMap = merchantLoansFromOneLms.stream()
                .collect(Collectors.toMap(LendingPaymentSchedule::getId, Function.identity()));


        logger.info("{} New 1LMS flow loans found for merchantId: {}", merchantLoansFromOneLms.size(), merchantId);


//        O(m + n) Complexity loop : m = merchantLoansFromOneLms.size(), n = responseDTOFromOneLms.getLoans().size()
        for (LendingMerchantLoansResponseDTO.Loan loan : responseDTOFromOneLms.getLoans()) {
            String bpLoanId = "";
            LendingPaymentSchedule matchedLoan = merchantLoansMap.get(loan.getLoanId());
            if (matchedLoan != null && matchedLoan.getLoanApplication() != null) {
                bpLoanId = matchedLoan.getLoanApplication().getExternalLoanId();
                logger.info("[setLendingMerchantLoansFromOneLms] bpLoanId: {}", bpLoanId);
            } else {
                logger.info("No matching loan found for loanId: {}", loan.getLoanId());
            }

            LoanDetailsResponse lmsLoanDetails = lmsLoanDetailsService.getLoanSummaryFromOneLms(bpLoanId);
            responseDTOFromOneLms.updateFromLoanSummaryOneLms(loan, lmsLoanDetails, matchedLoan);

            matchedLoan.setDueAmount((double) lmsLoanDetails.getLoanSummary().getOverdueInstalmentAmount());
            lendingPaymentScheduleDao.save(matchedLoan);


            //  LendingLedgerSlave lendingLedger = lendingLedgerSlaveDao.findLastPaymentEntryByMerchantAndLoan(merchantId, loan.getLoanId());

            if (loan.getStatus().equals("ACTIVE")) {
                loan.setTodayEdi(loan.getDueAmount() / lmsLoanDetails.getLoanSummary().getOverdueInstalmentCount());
                if (!ObjectUtils.isEmpty(loan.getDueAmount()) && !ObjectUtils.isEmpty(loan.getTodayEdi())) {
                    if (loan.getDueAmount() > loan.getTodayEdi()) {
                        loan.setPendingEdi((double) lmsLoanDetails.getLoanSummary().getOverdueInstalmentAmount());
                    } else {
                        loan.setPendingEdi(0D);
                    }
                }
                Double excessCollectionBalance = (double) lmsLoanDetails.getLoanSummary().getExcessPayable();

                loan.setTotalDue(lmsLoanDetails.getLoanSummary().getOverdueInstalmentAmount());
                loan.setTotalExcessBalance(excessCollectionBalance);
                loan.setNetPayable(Math.max(loan.getTotalDue() - loan.getTotalExcessBalance(), 0));

            }
            loan.setDpd(lmsLoanDetails.getLoanSummary().getOverdueInstalmentCount());

            // TODO :  Fetch from LPO table OR Fetch all Transaction Lentra API and get last transaction
                loan.setLastEdiPaid(0D);
                loan.setShowCustomAmount(true);

//            LendingPrepaymentSlave lendingPrepayment = lendingPrePaymentSlaveDao.findByMerchantIdAndLoanId(merchantId, loan.getLoanId());

            loan.setPaidAmount((double) lmsLoanDetails.getLoanSummary().getTotalPaidAmount());
            loan.setPendingAmount((double) lmsLoanDetails.getLoanSummary().getPendingInstalmentAmount());
            loan.setPaidPrinciple((double) lmsLoanDetails.getLoanSummary().getPaidPrincipalAmount());

            // EDI 7 Days model always as rediscussed with product
            loan.setEdiDays(7);

            loan.setDuePenalty(lmsLoanDetails.getLoanSummary().getPaidPenalCharges());

            // TODO : Need to discuss from NACH Service :
            loan.setNachBounceAmount(0);

            if (loan.getStatus().equals("ACTIVE")) {
                responseDTOFromOneLms.setShowChangeBankAccountBanner(showChangeBankAccountBanner(responseDTOFromOneLms.getAccountDetails(), merchantId));
                LendingPullPaymentSlave pullPayment = lendingPullPaymentDaoSlave.findTop1ByMerchantIdAndModeOrderByIdDesc(merchantId, "AUTOPAYUPI");
                if (pullPayment != null) {
                    Double amount = pullPayment.getDeductedAmount();
                    String status = pullPayment.getStatus();
                    Long id = loan.getLoanId();
                    logger.info("loan id is {}", id);
                    loan.setPresentmentStatus(status);
                    loan.setPresentmentAmount(amount);
                    log.info("responseDTOFromOneLms pull payment Updated Date is {}", pullPayment.getUpdatedAt());
                    loan.setPresentmentDate(pullPayment.getUpdatedAt());
                }

                log.info("loan application id is loan.getApplicationId{}", loan.getApplicationId());
                Optional<AutoPayUPISlave> autoPayUPI = autoPayUPISlaveDao.findTop1ByMerchantIdAndApplicationIdOrderByIdDesc(merchantId, loan.getApplicationId());
                if (autoPayUPI.isPresent()) {
                    loan.setAutoPayMandateStatus(autoPayUPI.get().getStatus());
                    loan.setMandateRegisterId(autoPayUPI.get().getOrderId());
                }


                if (lmsLoanDetails.getLoanSummary().getOverdueInstalmentCount() < 3 && lmsLoanDetails.getLoanSummary().getOverdueInstalmentCount() != 0) {
                        if (easyLoanUtil.percentScaleUp(merchantId, upiPercent)
                                && "LDC".equalsIgnoreCase(loan.getLender())) {
                            loan.setAutoPayEligibility(Boolean.TRUE);
                        } else {
                            loan.setAutoPayEligibility(Boolean.FALSE);
                        }
                    } else {
                        loan.setAutoPayEligibility(Boolean.FALSE);
                    }


                // TODO :  Need to check below code ::

//                    Set settlement details if settlement initiated
                try {
                    if (loan.isSettlementInitiated()) {
                        SettlementDetails settlementDetails = settlemetDetailsDao.findByLoanIdAndStatus(loan.getLoanId(), INIT.name());
                        loan.setSettlementInitiated(loan.isSettlementInitiated());
                        loan.setSettlementAmountOffer(settlementDetails.getSettlementAmountOffer());
                        loan.setSettlementExpiryDate(settlementDetails.getSettlementExpiryDate());
                    }
                } catch (Exception ex) {
                    logger.error("Multiple settlement initiated, Stack: {}", Arrays.asList(ex.getStackTrace()));
                    loan.setSettlementInitiated(Boolean.FALSE);
                }

                responseDTOFromOneLms.setShowRenachBanner(showRenachBanner(merchantId, loan.getLender(), responseDTOFromOneLms.getShowChangeBankAccountBanner()));
            }

            LendingApplicationLenderDetailsSlave lendingApplicationLenderDetailsSlave = lendingApplicationLenderDetailsDaoSlave.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(loan.getApplicationId(), "ACTIVE", loan.getLender());
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave)) {
                loan.setAnnualRoi(lendingApplicationLenderDetailsSlave.getAnnualRoi());
            }
        }


        // No need to modify as this need to be static
        LendingPaymentScheduleSlave lendingPaymentSchedule = lendingPaymentScheduleDaoSlave.findFirstByMerchantIdAndStatusAndLmsSource(merchantId, Arrays.asList("ACTIVE", "DECEASED"), ONE_LMS);
        if (Objects.nonNull(lendingPaymentSchedule)) {
            List<PenaltyFeeConfigSlave> penaltyFeeConfigSlaves;

            if (TRILLIONLOANS.toString().equals(lendingPaymentSchedule.getNbfc())) {
                penaltyFeeConfigSlaves = penaltyFeeConfigDaoSlave.findByVersionAndStatusAndLenderOrderByMinAmountAsc
                        (1D, true, lendingPaymentSchedule.getNbfc());
            } else {
                penaltyFeeConfigSlaves = penaltyFeeConfigDaoSlave.findByVersionAndStatusAndLenderOrderByMinAmountAsc
                        (2D, true, lendingPaymentSchedule.getNbfc());
            }

            List<LendingMerchantLoansResponseDTO.PenaltyConfig> penaltyConfigs = new ArrayList<>();

            for (PenaltyFeeConfigSlave penaltyFeeConfigSlave : penaltyFeeConfigSlaves) {
                LendingMerchantLoansResponseDTO.PenaltyConfig penaltyConfig = new LendingMerchantLoansResponseDTO.PenaltyConfig();
                penaltyConfig.setMinAmount(penaltyFeeConfigSlave.getMinAmount());
                penaltyConfig.setMaxAmount(penaltyFeeConfigSlave.getMaxAmount());
                penaltyConfig.setPenalty(penaltyFeeConfigSlave.getPenalty());
                penaltyConfigs.add(penaltyConfig);
            }
            responseDTOFromOneLms.setPenaltyConfig(penaltyConfigs);
        } else {
            log.info("lendingPaymentSchedule is null from new 1LMS flow for merchantId: {}", merchantId);
        }

        if (lendingPaymentSchedule != null) {
            Date date = new Date();
            if (date.after(lendingPaymentSchedule.getStartDate())) {
                log.info("OneLms loan start date is after current date");
                responseDTOFromOneLms.setEdiStarted(Boolean.TRUE);
            }

            // Note: PerpetualDpd is not handled on the Lentra system. The commented code below is redundant as it mirrors the current implementation in the codebase.
//            } else {
//                if (PerpetualDpdAdjusted.Y.name().equalsIgnoreCase(lendingPaymentSchedule.getPerpetualDpdAdjusted())) {
//                    responseDTOFromOneLms.setPerpetualDpdRestrictPgPayment(Boolean.TRUE);
//                }
//                responseDTOFromOneLms.setEdiStarted(Boolean.FALSE);
//            }

            List<LendingMerchantLoansResponseDTO.RepaymentDetails> repaymentDetailsList = new ArrayList<>();
            // As this is for limit 3 because previously it was 3 on old flow .
            String externalLoanId = lendingApplicationDao.getExternalLoanIdById(lendingPaymentSchedule.getApplicationId());
            if (ObjectUtils.isEmpty(externalLoanId)) {
                logger.error("No external loan id found for application id: {}", lendingPaymentSchedule.getApplicationId());
            } else {
                logger.info("Fetching recent transactions for 1LMS external loan id: {}", externalLoanId);
                List<LmsPaymentDetails> loanPaymentOrderList = lmsPaymentDetailsDao.findRecentTransactionsByBpLoanId(externalLoanId);
                logger.info("Fetched {} recent transactions for 1LMS external loan id: {}", loanPaymentOrderList.size(), externalLoanId);
                if (!ObjectUtils.isEmpty(loanPaymentOrderList)) {
                    for (LmsPaymentDetails lmsPaymentDetail : loanPaymentOrderList) {
                        LendingMerchantLoansResponseDTO.RepaymentDetails repaymentDetails = new LendingMerchantLoansResponseDTO.RepaymentDetails();
                        repaymentDetails.setAmount(lmsPaymentDetail.getAmount().doubleValue());
                        repaymentDetails.setDate(lmsPaymentDetail.getTransferDate());
                        repaymentDetails.setMode(LoanUtil.settlementMode.getOrDefault(lmsPaymentDetail.getAdjustmentMode(), "UPI"));
                        repaymentDetails.setStatus(lmsPaymentDetail.getSentToLms().name());
                        repaymentDetails.setOrderId(lmsPaymentDetail.getTerminalOrderId());
                        repaymentDetailsList.add(repaymentDetails);
                    }
                    responseDTOFromOneLms.setRepaymentDetails(repaymentDetailsList);
                }
            }

            // TODO: Currently, TOPUP is not eligible on Lentra. Analyze the feasibility of enabling it in the future.
            //  The following code reflects the current implementation.
            try {
                List<LoanEligibilityDTO> loans = topupLoan(lendingPaymentSchedule, false);
                if (!loans.isEmpty()) {
                    responseDTOFromOneLms.setEligibility(loans);
                    responseDTOFromOneLms.setTopup(Boolean.TRUE);
//                            lendingMerchantLoansResponseDTO.setTopupLender(!Lender.LDC.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) ? Lender.LDC.name() : Lender.MAMTA.name());
                    responseDTOFromOneLms.setTopupLender(topupLenderMapper(lendingPaymentSchedule.getNbfc()));
                    Experian experian = experianDao.getByMerchantId(merchantId);
                    if (!ObjectUtils.isEmpty(experian) && !ObjectUtils.isEmpty(experian.getPancardNumber())) {
                        responseDTOFromOneLms.setIsPanNsdlVerified(loanUtilV3.isPanNsdlVerified(token, experian.getPancardNumber(), merchantId));
                        if (LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
                            /** Explicitly setting IsPanNsdlVerified false to open PAN PIN consent Page for LL BT Topup loans **/
                            responseDTOFromOneLms.setIsPanNsdlVerified(false);
                        }
                    }
                    funnelService.submitEventV3(merchantId, null, null, FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.TOPUP_ELIGIBLE, null, LoanDetailsConstant.FUNNEL_VERSION_TAG);
                }
                if (Arrays.asList(ABFL.name(), TRILLIONLOANS.name(), PIRAMAL.name()).contains(lendingPaymentSchedule.getNbfc()) || LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
                    responseDTOFromOneLms.setTopupRejected(checkForTopupRejection(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc()));
                }
                if (Arrays.asList(PIRAMAL.name()).contains(lendingPaymentSchedule.getNbfc())) {
                    LocalTime now = LocalTime.now();
                    LocalTime topupDisabledStartTime = LocalTime.parse(topupDisabledStartTimeString);
                    LocalTime topupDisabledEndTime = LocalTime.parse(topupDisabledEndTimeString);
                    Boolean isTimeBasedTopupDisabled = now.isAfter(topupDisabledStartTime) || now.isBefore(topupDisabledEndTime);
                    responseDTOFromOneLms.setTimeBasedTopupDisabled(isTimeBasedTopupDisabled);
                }
            } catch (Exception e) {
                logger.error("Exception while calculating TOPUP loan for merchant:{}", merchantId, e);
            }
            if (baseChecksForHalfAndIOEdi(lendingPaymentSchedule, responseDTOFromOneLms)) {
                logger.info("Base checks passed for Half/IO Loan for loanId:{}", lendingPaymentSchedule.getId());
                LendingIoHalfTopup lendingIoHalfTopup = lendingIoHalfTopupDao.findByLoanId(lendingPaymentSchedule.getId());
                LoanCalculationUtil.LoanBreakupDetail loanBreakupDetail;
                if (lendingIoHalfTopup != null && LoanType.IO_TOPUP.name().equals(lendingIoHalfTopup.getLoanType())) {
                    logger.info("merchant:{} eligible for io loan", merchantId);
                    loanBreakupDetail = calculateHalfIOLoan(lendingPaymentSchedule, merchantId, LoanType.IO_TOPUP);
                    responseDTOFromOneLms.setIoLoan(lendingPaymentSchedule, loanBreakupDetail);
                } else if (lendingIoHalfTopup != null && LoanType.HALF_TOPUP.name().equals(lendingIoHalfTopup.getLoanType())) {
                    logger.info("merchant:{} eligible for half loan", merchantId);
                    loanBreakupDetail = calculateHalfIOLoan(lendingPaymentSchedule, merchantId, LoanType.HALF_TOPUP);
                    responseDTOFromOneLms.setHalfLoan(lendingPaymentSchedule, loanBreakupDetail);
                } else {
                    logger.info("Entry not found in lending_io_half_topup for merchant:{}", merchantId);
                }
            }
        }
        return responseDTOFromOneLms;
    }


// Below code are dependent from above as copied similar way || Will try to remove dependency and then refactor the code ::


    private List<LendingPaymentSchedule> fetchLendingPaymentSchedule(Long merchantId, Long merchantStoreId, String status) {
        if (merchantStoreId != null) {
            return lendingPaymentScheduleDao.findByMerchantIdAndMerchantStoreIdAndStatus(merchantId, merchantStoreId,
                    status);
        }
        return lendingPaymentScheduleDao.findByMerchantIdAndStatusList(merchantId, status);
    }


    private Boolean checkForTopupRejection(Long merchantId, String parentLender) {
        try {
            LendingApplication prevApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if (!ObjectUtils.isEmpty(prevApplication)) {
                if (LoanType.TOPUP.name().equalsIgnoreCase(prevApplication.getLoanType()) && "rejected".equalsIgnoreCase(prevApplication.getStatus())) {
                    log.info("latest application with topup loanType for merchantId : {}", prevApplication);
                    Long minutes = TimeUnit.MINUTES.toMinutes(new Date().getTime() - prevApplication.getUpdatedAt().getTime()) / 60000;
                    if (ABFL.name().equalsIgnoreCase(prevApplication.getLender()) && minutes < abflTopupRejectionBannerTat) {
                        log.info("ABFL topup application rejected for merchantId : {} less than {} minutes ago", merchantId, abflTopupRejectionBannerTat);
                        return Boolean.TRUE;
                    } else if (TRILLIONLOANS.name().equalsIgnoreCase(prevApplication.getLender())) {
                        if (TRILLIONLOANS.name().equalsIgnoreCase(parentLender) && minutes < trillionTopupRejectionBannerTat) {
                            log.info("TRILLIONLOANS topup application rejected for merchantId : {} less than {} minutes ago", merchantId, trillionTopupRejectionBannerTat);
                            return Boolean.TRUE;
                        }
                        if (LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(parentLender) && minutes < llBalanceRejectionBannerTat) {
                            log.info("Liquiloans balance transfer topup application rejected for merchantId : {} less than {} minutes ago", merchantId, llBalanceRejectionBannerTat);
                            return Boolean.TRUE;
                        }
                    } else if (PIRAMAL.name().equalsIgnoreCase(prevApplication.getLender()) && minutes < piramalTopupRejectionBannerTat) {
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

        if (reNachEnabledMerchants.contains(merchantId) && !ObjectUtils.isEmpty(bankAccountDetails) && !ObjectUtils.isEmpty(bankAccountDetails) && bankAccountDetails.getBankName().toUpperCase().contains(PAYTM)) {
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

    public List<LoanEligibilityDTO> topupLoan(LendingPaymentScheduleSlave lendingPaymentSchedule, boolean createTopupAppCheck) {
        List<Long> derogMerchants = loanUtil.loadDerogEffectedMerchants();
        List<Long> customEnabledMerchants = loanUtil.customEnabledTopupMerchants();

        List<LendingPaymentScheduleSlave> activeLoans = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatusList(lendingPaymentSchedule.getMerchantId(), "ACTIVE");

        if (activeLoans.size() > 1) {
            logger.info("more than 1 loan active for merchantId : {} loans : {}", lendingPaymentSchedule.getMerchantId(), activeLoans.size());
            return Collections.emptyList();
        }

        if (customEnabledMerchants.contains(lendingPaymentSchedule.getMerchantId())) {
            return computeEligibility(lendingPaymentSchedule, createTopupAppCheck);

        }

        if (pilotTestEnabled && derogMerchants.contains(lendingPaymentSchedule.getMerchantId()) && derogTopUpEnable(lendingPaymentSchedule.getMerchantId())) {
            return computeEligibility(lendingPaymentSchedule, createTopupAppCheck);
        }

        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();
        LendingApplication lendingApplication =
                lendingApplicationDao.findByIdAndMerchantId(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getMerchantId());
        try {
            if (!isTopUpEnabled) {
                logger.info("Topup are loans are disabled");
                return eligiblity;
            }
            if (!topupLenders.contains(lendingPaymentSchedule.getNbfc())) {
                logger.info("Topup not enabled on lender:{}", lendingPaymentSchedule.getNbfc());
                return eligiblity;
            }

            if (LIQUILOANS_NBFC.toString().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && !easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), topupOnTltoTlRolloutPercent)) {
                logger.info("LIQUILOANS_NBFC topup not enabled for merchantId:{}", lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if (!(easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), rolloutTopupPercent)) && LIQUILOANS_TOPUP_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
                logger.info("Topup not enabled for this merchant :{}", lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if (TRILLIONLOANS.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && !easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), trillionTopupRolloutPercent) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                log.info("TRILLIONLOANS Topup not enabled for merchantId: {}", lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if (ABFL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && !easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), abflTopupRolloutPercent) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                log.info("ABFL Topup not enabled for merchantId: {}", lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && !easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), piramalTopupRolloutPercent) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                log.info("PIRAMAL Topup not enabled for merchantId: {}", lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if (topupPilotRunEnabledLenders.contains(lendingPaymentSchedule.getNbfc())) {
                LenderTopupEligibility lenderTopupEligibility = lenderTopupEligibilityDao.findTopupEligibilityFromLender(
                        lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getNbfc());
                if (ObjectUtils.isEmpty(lenderTopupEligibility)) {
                    log.info("Merchant not eligible from lender {} for merchantId: {}", lendingPaymentSchedule.getNbfc(), lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
            }


            if (loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                Long experianId = null;

                Boolean sevenDayFlag = LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);


                List<LendingEligibleLoan> eligibleLoanList = eligibleLoanDao.
                        findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);


                LendingEligibleLoan internalMerchantLoan = new LendingEligibleLoan(lendingPaymentSchedule.getMerchantId(), experianId, 200000D, "12 Months", "ACTIVE", null, 0, 0, null, 665, 0, 239400, null, "TOPUP", null);
                internalMerchantLoan.setEdiCount(360);
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
                    if (eligibleLoan.getAmount() != null && eligibleLoan.getProcessingFeeRate() != null && prevLoanUnpaidAmountBD != null) {
                        BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                        BigDecimal processingFeeRateBD = BigDecimal.valueOf(eligibleLoan.getProcessingFeeRate());
                        processingFee = amountBD.subtract(prevLoanUnpaidAmountBD).multiply(processingFeeRateBD).setScale(0, RoundingMode.CEILING);
                    } else {
                        throw new IllegalArgumentException("Either processing fee rate or loan amount cannot be null");
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
                    loanEligibilityDTO.setProcessingFee(processingFee.intValue());
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

                BigInteger maxDpd = loanDpdDaoSlave.findMaxDpd(lendingPaymentSchedule.getId());
                if (maxDpd.intValue() > 30) {
                    logger.info("Merchant Dpd Greater than 30 merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                //No Lender specific checks for topup
               /* if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && maxDpd.intValue() > 15) {
                    logger.info("Merchant Dpd Greater than 15 merchant:{} for lender {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc());
                    return eligiblity;
                }*/

                if (LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc()) && LoanUtil.calculateDPD(lendingPaymentSchedule.getEdiAmount(), lendingPaymentSchedule.getDueAmount()) > llBalanceTransferLoanCurrentDpdThreshold) {
                    logger.info("Merchant current Dpd Greater than 0 merchant:{} for lender {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc());
                    return eligiblity;
                }

                if (LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
                    return ExistingTopupRuleEngine(lendingPaymentSchedule, lendingApplication, createTopupAppCheck);
                }
                Double settlementAmount = lendingLedgerDao.findSettlementAmount(lendingPaymentSchedule.getId());
                double qrPaidRatio = (settlementAmount / lendingPaymentSchedule.getPaidAmount()) * 100;
                if (qrPaidRatio <= topupMinQrPaidRatio) {
                    logger.info("QR payment less than {} in tenure {} for merchant: {}", topupMinQrPaidRatio, lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                double paidRatio = 0d;
                if (lendingPaymentSchedule.getPaidPrinciple() != null && lendingPaymentSchedule.getLoanAmount() != null) {
                    paidRatio = lendingPaymentSchedule.getPaidPrinciple() / lendingPaymentSchedule.getLoanAmount();
                }
                //No Lender specific checks for topup
                /*if(ABFL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && paidRatio > 0.95D) {
                    logger.info("paid ratio is {} for ABFL loan of merchantId {}", paidRatio, lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }*/
                if (lendingApplication.getTenureInMonths() < 12 && paidRatio > 0.5D && paidRatio <= 0.95D) {
                    logger.info("paid ratio is {} for tenure {} months of merchantId: {}", paidRatio, lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                    return ExistingTopupRuleEngine(lendingPaymentSchedule, lendingApplication, createTopupAppCheck);
                }
                if (lendingApplication.getTenureInMonths() >= 12 && paidRatio > 0.75D && paidRatio <= 0.95D) {
                    logger.info("paid ratio is {} for tenure {} months of merchantId: {}", paidRatio, lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                    return AdditionalTopupRuleEngine(lendingPaymentSchedule, lendingApplication, createTopupAppCheck);
                }
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
            if (!createTopupAppCheck) {
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
                if (!ObjectUtils.isEmpty(globalLimitResponse) && !ObjectUtils.isEmpty(globalLimitResponse.getData())
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
                   /* if (ediPaidRatio < 50D) {
                        logger.info("EDI paid ratio:{} is less than 50% for merchant:{}", ediPaidRatio, lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }*/
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
            BigDecimal prevLoanUnpaidAmountBD = BigDecimal.valueOf(getPreviousLoanAmount(lendingPaymentSchedule));
            BigDecimal processingfee;
            if (!eligibleLoanList.isEmpty()) {
                eligibleLoanList.sort((o1, o2) -> (o2.getCreatedAt().compareTo(o1.getCreatedAt())));
                Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                LendingEligibleLoan eligibleLoan = eligibleLoanList.get(0);
                logger.info("eligible loan: {}", eligibleLoan);


                if (additionalTopupChecksFailed(lendingPaymentSchedule, eligibleLoan)) {
                    log.info("additional topup checks failed for merchant id {}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (eligibleLoan.getAmount() != null && prevLoanUnpaidAmountBD != null && eligibleLoan.getProcessingFeeRate() != null) {
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(eligibleLoan.getProcessingFeeRate());
                    processingfee = amountBD.subtract(prevLoanUnpaidAmountBD)
                            .multiply(processingFeeRateBD)
                            .setScale(0, RoundingMode.CEILING);
                } else {
                    throw new IllegalArgumentException("Either loan amount or prevLoanunpainAmount or processing fee rate is null");
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
                eligiblity.add(loanEligibilityDTO);
            }


            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingPaymentSchedule.getMerchantId());
            String pilotIdentifier = lendingRiskVariables.getPilotIdentifier();
            if (!ObjectUtils.isEmpty(pilotIdentifier) && !pilotIdentifier.contains(TOPUP_PILOT_IDENTIFIER)) {
                pilotIdentifier = pilotIdentifier + "," + TOPUP_PILOT_IDENTIFIER;
            }
            if (ObjectUtils.isEmpty(pilotIdentifier)) {
                pilotIdentifier = TOPUP_PILOT_IDENTIFIER;
            }
            lendingRiskVariables.setPilotIdentifier(pilotIdentifier);
            lendingRiskVariablesDao.save(lendingRiskVariables);
        } catch (Exception e) {
            logger.info("Exception occurred in Additional Topup Rule Engine for merchantId: {} {}", lendingPaymentSchedule.getMerchantId(), Arrays.asList(e.getStackTrace()));
        }
        return eligiblity;
    }

    private List<LoanEligibilityDTO> ExistingTopupRuleEngine(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingApplication lendingApplication, boolean createTopupAppCheck) {
        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();
        try {
            Double settlementAmount = lendingLedgerDao.findSettlementAmount(lendingPaymentSchedule.getId());
            double qrPaidRatio = (settlementAmount / lendingPaymentSchedule.getPaidAmount()) * 100;
            if (!LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc()) && qrPaidRatio <= topupMinQrPaidRatio) {
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
                logger.info("Photo not of a shop found for merchant: {} for last application: {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                return eligiblity;
            }
            Integer ediPaidCount = lendingLedgerDao.findLedgerCountOnAmountGreaterThanEdiAmount(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getEdiAmount());
            int paidCount = lendingPaymentSchedule.getEdiCount() - lendingPaymentSchedule.getEdiRemainingCount();
            logger.info("ediPaidCount:{} and paidCount:{} for merchant:{}", ediPaidCount, paidCount, lendingPaymentSchedule.getMerchantId());
            double ediPaidRatio = (ediPaidCount * 1.0 / paidCount) * 100;

            Long experianId = null;
            Boolean sevenDayFlag = LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);
            List<LendingEligibleLoan> eligibleLoanList = null;
            if (!createTopupAppCheck) {
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
                if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
                    logger.info("Global limit for merchant:{} is {}", lendingPaymentSchedule.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
                    eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                }
                if (eligibleAmount.equals(0D) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                    logger.info("No topup eligibility found for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
                    //Removing ediPaidRatio check, handled by Scienaptics
                   /* if (!LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc()) && ediPaidRatio < 65D) {
                        logger.info("EDI paid ratio:{} is less than 65% for merchant:{}", ediPaidRatio, lendingPaymentSchedule.getMerchantId());
                        eligibleAmount = Math.min(eligibleAmount, lendingPaymentSchedule.getLoanAmount());
                    }*/
                    if (LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc()) && lendingApplication.getTenureInMonths() > 12 && ediPaidRatio < llBalanceTransferLoanEdiPaidRatioThreshold) {
                        logger.info("For parent loan tenure {} EDI paid ratio:{} is less than {} % for Liquiloans balance transfer for merchant: {}", lendingApplication.getTenureInMonths(), ediPaidRatio, llBalanceTransferLoanEdiPaidRatioThreshold, lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }
//                    if(LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
//                        eligibleAmount = Math.min(eligibleAmount, lendingPaymentSchedule.getLoanAmount());
//                    }
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
            BigDecimal prevLoanUnpaidAmountBD = BigDecimal.valueOf(getPreviousLoanAmount(lendingPaymentSchedule));
            BigDecimal processingfee;
            if (!eligibleLoanList.isEmpty()) {
                eligibleLoanList.sort((o1, o2) -> (o2.getCreatedAt().compareTo(o1.getCreatedAt())));
                Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                LendingEligibleLoan eligibleLoan = eligibleLoanList.get(0);
                logger.info("eligible loan: {}", eligibleLoan);

                if (additionalTopupChecksFailed(lendingPaymentSchedule, eligibleLoan)) {
                    log.info("additional topup checks failed for merchant id {}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (eligibleLoan.getAmount() != null && eligibleLoan.getProcessingFeeRate() != null && prevLoanUnpaidAmountBD != null) {
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(eligibleLoan.getProcessingFeeRate());
                    processingfee = amountBD.subtract(prevLoanUnpaidAmountBD)
                            .multiply(processingFeeRateBD)
                            .setScale(0, RoundingMode.CEILING);
                } else {
                    throw new IllegalArgumentException("Either loan amount or prevLoanunpainAmount or processing fee rate is null");
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
            }

        } catch (Exception e) {
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
        List<LendingPaymentSchedule> activeLoans = fetchLendingPaymentSchedule(basicDetailsDto.getId(), merchantStoreId, "ACTIVE");
        if (!activeLoans.isEmpty()) {
            for (LendingPaymentSchedule activeLoan : activeLoans) {
                dueAmount += activeLoan.getDueAmount();
                dueAmount += Objects.nonNull(activeLoan.getDuePenalty()) ? activeLoan.getDuePenalty() : 0d;
            }
        }

        Double creditCardDueAmount = getCreditCardDueAmount(basicDetailsDto, merchantStoreId);

        Double goldLoanDueAmount = getGoldLoanDueAmount(basicDetailsDto, merchantStoreId);

        logger.info("dueAmount : {}, creditCardDueAmount : {}, goldLoanDueAmount : {} for merchantId : {}", dueAmount, creditCardDueAmount, goldLoanDueAmount, basicDetailsDto.getId());

        dueAmount += creditCardDueAmount + goldLoanDueAmount;
        responseMap.put("due_amount", dueAmount);
        cacheDueAmtData(dueAmount, dueAmountCacheKey, dueAmountCachingWindow);
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
            logger.info("fetching creditCardDueAmount from api for merchantId {} and merchantStoreId {}", basicDetailsDto.getId(), merchantStoreId);
            creditCardDueAmount = apiGatewayService.getCreditCardDueAmount(basicDetailsDto.getId());
            logger.info("creditCardDueAmount from api for merchantId {} and merchantStoreId {} is {}", basicDetailsDto.getId(), merchantStoreId, creditCardDueAmount);
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
            logger.info("fetching goldLoanDueAmount from api for merchantId {} and merchantStoreId {}", basicDetailsDto.getId(), merchantStoreId);
            goldLoanDueAmount = apiGatewayService.getGoldLoanDueAmount(basicDetailsDto.getId());
            logger.info("goldLoanDueAmount from api for merchantId {} and merchantStoreId {} is {}", basicDetailsDto.getId(), merchantStoreId, goldLoanDueAmount);
            cacheDueAmtData(goldLoanDueAmount, goldLoanDueAmountCacheKey, goldLoanDueAmountCachingWindow);
        }
        return goldLoanDueAmount;
    }

    private List<LoanEligibilityDTO> computeEligibility(LendingPaymentScheduleSlave lendingPaymentSchedule, boolean createTopupApplicationCheck) {
        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();

        try {

            if (!topupLenders.contains(lendingPaymentSchedule.getNbfc())) {
                logger.info("Topup not enabled on lender:{}", lendingPaymentSchedule.getNbfc());
                return eligiblity;
            }

            Long experianId = null;
            Boolean sevenDayFlag = LenderOffDays.valueOf(lendingPaymentSchedule.getLoanApplication().getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);
            List<LendingEligibleLoan> eligibleLoanList = null;
            if (!createTopupApplicationCheck) {
                eligibleLoanList = eligibleLoanDao.findByMerchantIdAndLoanTypeAndPayableDays(lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);
            }

            if (ObjectUtils.isEmpty(eligibleLoanList)) {
                Double eligibleAmount = 0D;
                GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(lendingPaymentSchedule.getMerchantId(), EligibilityRequestSource.EASY_LOANS);
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
            BigDecimal prevLoanUnpaidAmountBD = BigDecimal.valueOf(getPreviousLoanAmount(lendingPaymentSchedule));
            BigDecimal processingfee;
            if (!eligibleLoanList.isEmpty()) {
                eligibleLoanList.sort((o1, o2) -> (o2.getCreatedAt().compareTo(o1.getCreatedAt())));
                Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                LendingEligibleLoan eligibleLoan = eligibleLoanList.get(0);
                if (eligibleLoan.getAmount() != null && eligibleLoan.getProcessingFeeRate() != null && prevLoanUnpaidAmountBD != null) {
                    BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
                    BigDecimal processingFeeRateBD = BigDecimal.valueOf(eligibleLoan.getProcessingFee());
                    processingfee = amountBD.subtract(prevLoanUnpaidAmountBD)
                            .multiply(processingFeeRateBD)
                            .setScale(0, RoundingMode.CEILING);
                } else {
                    throw new IllegalArgumentException("Either loan amount or prevLoanUnpaidAmount or processing fee rate is null");
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
                loanEligibilityDTO.setProcessingFee(processingfee.intValue());
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

    private boolean additionalTopupChecksFailed(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingEligibleLoan eligibleLoan) {
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingPaymentSchedule.getMerchantId());
        double vintage = !ObjectUtils.isEmpty(lendingRiskVariables.getVintage()) ? lendingRiskVariables.getVintage() : 0D;
        Double amountPaidThroughQrPer = loanUtil.getAmountPaidThroughQrPer(lendingPaymentSchedule);
        int currentDPD = LoanUtil.calculateDPD(lendingPaymentSchedule.getEdiAmount(), lendingPaymentSchedule.getDueAmount());
        if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && maxIrrCheckFailed(eligibleLoan.getEdiCount(), Double.valueOf(eligibleLoan.getEdi()), eligibleLoan.getAmount(), lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc())) {
            logger.info("max irr check failed for merchant id {}, lender {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc());
            return true;
        }
        if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && maxAprCheckFailed(eligibleLoan.getEdiCount(), Double.valueOf(eligibleLoan.getEdi()), eligibleLoan.getAmount(), Double.valueOf(eligibleLoan.getProcessingFee()), lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc())) {
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
        if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && amountPaidThroughQrPer < 40) {
            logger.info("amt paid through qr check failed for merchant id {}, lender {} : {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc(), amountPaidThroughQrPer);
            return true;
        }
        if (llBalanceBreHardRejectEnabled && LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
            LendingApplication prevApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(lendingPaymentSchedule.getMerchantId());
            if (!ObjectUtils.isEmpty(prevApplication) && LoanType.TOPUP.name().equalsIgnoreCase(prevApplication.getLoanType()) && "rejected".equalsIgnoreCase(prevApplication.getStatus())) {
                LendingApplicationLenderDetailsSlave lendingApplicationLenderDetailsSlave = lendingApplicationLenderDetailsDaoSlave.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(prevApplication.getId(), Status.INACTIVE.name(), prevApplication.getLender());
                if (!ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave) && !ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave.getBreRejectionReason())) {
                    log.info("BRE for liquiloans balance transfer application {} already failed with reason {} for merchantId {}", prevApplication.getId(), lendingApplicationLenderDetailsSlave.getBreRejectionReason(), lendingPaymentSchedule.getMerchantId());
                    return true;
                }
            }
        }
        return false;
    }

    public void updateResponseDto(LendingMerchantLoansResponseDTO responseDTO, LendingMerchantLoansResponseDTO responseDTOFromOneLms) {

        if (!ObjectUtils.isEmpty(responseDTOFromOneLms)) {
            List<LendingMerchantLoansResponseDTO.Loan> loansList = new ArrayList<>(responseDTO.getLoans());
            loansList.addAll(responseDTOFromOneLms.getLoans());
            responseDTO.setLoans(loansList);

            if (!ObjectUtils.isEmpty(responseDTOFromOneLms.getRepaymentDetails())) {
                responseDTO.setRepaymentDetails(responseDTOFromOneLms.getRepaymentDetails());
            }

            if (Boolean.TRUE.equals(responseDTOFromOneLms.getEdiStarted())) {
                responseDTO.setEdiStarted(true);
            }

            if (!ObjectUtils.isEmpty(responseDTOFromOneLms.getPenaltyConfig())) {
                responseDTO.setPenaltyConfig(responseDTOFromOneLms.getPenaltyConfig());
            }
        }
    }
}

