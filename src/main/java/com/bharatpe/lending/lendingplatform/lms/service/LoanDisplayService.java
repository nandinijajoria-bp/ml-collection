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
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.lendingplatform.lending.util.RolloutUtil;
import com.bharatpe.lending.lendingplatform.lms.dto.response.LoanDetailsResponse;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.loanV3.dto.TopupEligibilityResponseData;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.loanV3.services.associationsV2.payu.impl.PayUKycService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.impl.LenderAssignService;
import com.bharatpe.lending.util.BQPublisherUtil;
import com.bharatpe.lending.util.ErrorDescriptionMapper;
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
import static com.bharatpe.lending.lendingplatform.lms.util.ConversionUtil.safeBigDecimalToDouble;
import static com.bharatpe.lending.lendingplatform.lms.util.ConversionUtil.safeBigDecimalToInt;
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
    BQPublisherUtil bqPublisherUtil;

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
    private ErrorDescriptionMapper errorDescriptionMapper;

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

    @Autowired
    private RolloutUtil rolloutUtil;

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

    @Autowired
    private PayUKycService payUKycService;

    @Value("${pricing.experiment.enable:false}")
    private boolean pricingExpEnabled;

    @Autowired
    private PricingExperimentDao pricingExperimentDao;
    @Autowired
    private LenderAssignService lenderAssignService;
    @Value("${topup.skip.pos.check.lenders:PIRAMAL,TRILLIONLOANS}")
    private List<String> LENDER_TO_SKIP_POS_CHECK;
    @Value("${round.down.eligible.lenders:TRILLIONLOANS}")
    private List<String> roundDownEligibleLenders;
    @Value("${topup.abfl.minimum.additional.amount:50000}")
    private Double topupABFLMinimumAdditionalAmount;
    @Value("${topup.minimum.additional.amount:10000}")
    private Double topupMinimumAdditionalAmount;
    @Value("${payu.topup.rollout.percent:}")
    Integer payuTopUpRolloutPercent;

    @Value("${topup.v2.flow.trillion.merchants:-}")
    private List<Long> merchantsWithTrillionTopupV2FlowEnabled = Collections.emptyList();
    @Value("${topup.v2.flow.enabled:1}")
    private Integer topupV2FlowEnabled;
    @Value("${topup.v2.flow.lenders:PIRAMAL}")
    private String topupV2FlowLenders;
    @Autowired
    private LendingLenderPricingDao lendingLenderPricingDao;
    @Autowired
    private ForeclosureService foreclosureService;
    private static final List<String> TOPUP_REJECTION_ENABLED_LENDERS = Arrays.asList(
            LIQUILOANS_P2P.name(),LIQUILOANS_P2P_OF.name(), ABFL.name(), TRILLIONLOANS.name(), PIRAMAL.name(), PAYU.name());
    @Value("${payu.topup.rejection.banner.tat:5}")
    Long payuTopupRejectionBannerTat;
    @Value("#{${topup.v2.flow.lender.rollout.percentage:{}}}")
    private Map<String,Integer> topupV2FlowLenderRolloutPercentage = new HashMap<>();

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

            if(matchedLoan != null) {
                matchedLoan.setDueAmount(Math.ceil(safeBigDecimalToDouble(lmsLoanDetails.getLoanSummary().getOverdueInstalmentAmount())));
                matchedLoan.setDuePenalty(lmsLoanDetails.getLoanSummary().calculateDuePenaltyAsDouble(true));
                lendingPaymentScheduleDao.save(matchedLoan);
            }


            //  LendingLedgerSlave lendingLedger = lendingLedgerSlaveDao.findLastPaymentEntryByMerchantAndLoan(merchantId, loan.getLoanId());

            if (loan.getStatus().equals("ACTIVE")) {
                loan.setTodayEdi(lmsLoanDetails.getLoanSummary().getInstalmentAmount().doubleValue());
                if (!ObjectUtils.isEmpty(loan.getDueAmount()) && !ObjectUtils.isEmpty(loan.getTodayEdi())) {
                    if (loan.getDueAmount() > loan.getTodayEdi()) {
                        loan.setPendingEdi(Math.ceil(safeBigDecimalToDouble(lmsLoanDetails.getLoanSummary().getOverdueInstalmentAmount())));
                    } else {
                        loan.setPendingEdi(0D);
                    }
                }
                Double excessCollectionBalance = (double) lmsLoanDetails.getLoanSummary().getExcessPayable();
                loan.setTotalDue(Math.ceil(safeBigDecimalToDouble((lmsLoanDetails.getLoanSummary().getOverdueInstalmentAmount() != null ? lmsLoanDetails.getLoanSummary().getOverdueInstalmentAmount() : BigDecimal.ZERO)
                                        .add(lmsLoanDetails.getLoanSummary().calculateDuePenalty()))));
                loan.setTotalExcessBalance(excessCollectionBalance);

                double rawTotalDue = safeBigDecimalToDouble((lmsLoanDetails.getLoanSummary().getOverdueInstalmentAmount() != null ? lmsLoanDetails.getLoanSummary().getOverdueInstalmentAmount() : BigDecimal.ZERO)
                        .add(lmsLoanDetails.getLoanSummary().calculateDuePenalty()));
                loan.setNetPayable(Math.max(Math.ceil(rawTotalDue - loan.getTotalExcessBalance()), 0));

            }
            loan.setDpd(lmsLoanDetails.getLoanSummary().getOverdueInstalmentCount());

            // TODO :  Fetch from LPO table OR Fetch all Transaction Lentra API and get last transaction
                loan.setLastEdiPaid(0D);
                loan.setShowCustomAmount(true);

//            LendingPrepaymentSlave lendingPrepayment = lendingPrePaymentSlaveDao.findByMerchantIdAndLoanId(merchantId, loan.getLoanId());

            loan.setPaidAmount((double) safeBigDecimalToInt(lmsLoanDetails.getLoanSummary().getTotalPaidAmount()));
            loan.setPendingAmount(Math.ceil(safeBigDecimalToDouble(lmsLoanDetails.getLoanSummary().getPendingInstalmentAmount())));
            loan.setPaidPrinciple((double) safeBigDecimalToInt(lmsLoanDetails.getLoanSummary().getPaidPrincipalAmount()));

            // EDI 7 Days model always as rediscussed with product
            loan.setEdiDays(7);

            loan.setDuePenalty(lmsLoanDetails.getLoanSummary().calculateDuePenaltyAsDouble(true));

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
                    if (!"Success".equalsIgnoreCase(status)) {
                        String errorDescription = pullPayment.getErrorDescription();
                        loan.setFailureReason(errorDescriptionMapper.mapToUserMessage(errorDescription));
                    }
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

            LendingPaymentScheduleDTO lendingPaymentScheduleDTO = lmsLoanDetailsService.getLendingPaymentScheduleDTOFromOneLms(externalLoanId, lendingPaymentSchedule);
            try {
                List<LoanEligibilityDTO> loans = null;
                String topupLender = topupLenderMapper(lendingPaymentScheduleDTO.getNbfc());
                int rolloutPercentage = topupV2FlowLenderRolloutPercentage.getOrDefault(topupLender, 0);
                boolean isV2Flow = easyLoanUtil.percentScaleUp(merchantId, rolloutPercentage);
                if(isV2Flow) {
                    log.info("1LMSTOPUP: Calculating TOPUP loan for merchant:{}", merchantId);
                    loans = topupLoanV2(lendingPaymentScheduleDTO, false);
                }
                setTopupDetailsV2(merchantId, loans, responseDTOFromOneLms, lendingPaymentScheduleDTO);

                if(TOPUP_REJECTION_ENABLED_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
                    responseDTOFromOneLms.setTopupRejected(checkForTopupRejection(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc()));
                }
                responseDTOFromOneLms.setTimeBasedTopupDisabled(loanUtil.isTimeBasedTopupDisabled(lendingPaymentSchedule.getNbfc()));
                responseDTOFromOneLms.setIsPanNsdlVerified(false);
                funnelService.submitEventV3(merchantId, null, null, FunnelEnums.StageId.LOAN_DASHBOARD, FunnelEnums.StageEvent.TOPUP_ELIGIBLE, null, LoanDetailsConstant.FUNNEL_VERSION_TAG);
            } catch (Exception e) {
                logger.error("Exception while calculating TOPUP loan for merchant:{}", merchantId, e);
            }
            if (baseChecksForHalfAndIOEdi(lendingPaymentScheduleDTO, responseDTOFromOneLms)) {
                logger.info("Base checks passed for Half/IO Loan for loanId:{}", lendingPaymentSchedule.getId());
                LendingIoHalfTopup lendingIoHalfTopup = lendingIoHalfTopupDao.findByLoanId(lendingPaymentSchedule.getId());
                LoanCalculationUtil.LoanBreakupDetail loanBreakupDetail;
                if (lendingIoHalfTopup != null && LoanType.IO_TOPUP.name().equals(lendingIoHalfTopup.getLoanType())) {
                    logger.info("merchant:{} eligible for io loan", merchantId);
                    loanBreakupDetail = calculateHalfIOLoan(lendingPaymentScheduleDTO, merchantId, LoanType.IO_TOPUP);
                    responseDTOFromOneLms.setIoLoan(lendingPaymentSchedule, loanBreakupDetail);
                } else if (lendingIoHalfTopup != null && LoanType.HALF_TOPUP.name().equals(lendingIoHalfTopup.getLoanType())) {
                    logger.info("merchant:{} eligible for half loan", merchantId);
                    loanBreakupDetail = calculateHalfIOLoan(lendingPaymentScheduleDTO, merchantId, LoanType.HALF_TOPUP);
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

    private LoanCalculationUtil.LoanBreakupDetail calculateHalfIOLoan(LendingPaymentScheduleDTO lendingPaymentSchedule, Long merchantId, LoanType loanType) {
        try {
            int foreclosureAmount = foreclosureService.getForeclosureAmount(lendingPaymentSchedule.getApplicationId(), merchantId);
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

    private boolean baseChecksForHalfAndIOEdi(LendingPaymentScheduleDTO lendingPaymentSchedule, LendingMerchantLoansResponseDTO responseDTO) {
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


    private double getPreviousLoanAmount(LendingPaymentScheduleDTO lendingPaymentSchedule) {
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

    public void updateResponseDto(LendingMerchantLoansResponseDTO responseDTO, LendingMerchantLoansResponseDTO responseDTOFromOneLms, Long merchantId) {

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

            if(responseDTOFromOneLms.getTopup()) {
                log.info("1LMSTOPUP: Updating topup details for merchant: {}", merchantId);
                if (!ObjectUtils.isEmpty(responseDTOFromOneLms.getEligibility())) {
                    if (responseDTO.getEligibility() == null) {
                        responseDTO.setEligibility(new ArrayList<>());
                    }
                    responseDTO.getEligibility().addAll(responseDTOFromOneLms.getEligibility());
                }

                if (!ObjectUtils.isEmpty(responseDTOFromOneLms.getTopup())) {
                    responseDTO.setTopup(responseDTOFromOneLms.getTopup());
                }

                if (!ObjectUtils.isEmpty(responseDTOFromOneLms.getTopupLender())) {
                    responseDTO.setTopupLender(responseDTOFromOneLms.getTopupLender());
                }

                if (!ObjectUtils.isEmpty(responseDTOFromOneLms.getIsPanNsdlVerified())) {
                    responseDTO.setIsPanNsdlVerified(responseDTOFromOneLms.getIsPanNsdlVerified());
                }

                if (!ObjectUtils.isEmpty(responseDTOFromOneLms.getTopupRejected())) {
                    responseDTO.setTopupRejected(responseDTOFromOneLms.getTopupRejected());
                }

                if (!ObjectUtils.isEmpty(responseDTOFromOneLms.getTimeBasedTopupDisabled())) {
                    responseDTO.setTimeBasedTopupDisabled(responseDTOFromOneLms.getTimeBasedTopupDisabled());
                }

                if (!ObjectUtils.isEmpty(responseDTOFromOneLms.getMinimumAllowedAmount())) {
                    responseDTO.setMinimumAllowedAmount(responseDTOFromOneLms.getMinimumAllowedAmount());
                }

                if (!ObjectUtils.isEmpty(responseDTOFromOneLms.getMaximumAllowedAmount())) {
                    responseDTO.setMaximumAllowedAmount(responseDTOFromOneLms.getMaximumAllowedAmount());
                }

                if (!ObjectUtils.isEmpty(responseDTOFromOneLms.getTenures())) {
                    responseDTO.setTenures(responseDTOFromOneLms.getTenures());
                }
                responseDTO.setTopupV2FlowEnabled(true);
            }
        }
    }


    public List<LoanEligibilityDTO> topupLoanV2(LendingPaymentScheduleDTO lendingPaymentSchedule, boolean createTopupAppCheck) {
        log.info("calculating topup loan eligibility for merchantId: {}", lendingPaymentSchedule.getMerchantId());

        LendingApplication lendingApplication =
                lendingApplicationDao.findByIdAndMerchantId(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getMerchantId());

        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();

        if (lendingApplication == null) {
            log.info("Lending Application not found/topup loan for merchant:{}", lendingPaymentSchedule.getMerchantId());
            return eligiblity;
        }

        try {

            if (PAYU.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && !easyLoanUtil.percentScaleUp(lendingPaymentSchedule.getMerchantId(), payuTopUpRolloutPercent) && !loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                addRejectionReason(eligiblity, "PAYU Topup not enabled for this merchant");
                log.info("Payu Topup not enabled for merchantId: {}", lendingPaymentSchedule.getMerchantId());
                return eligiblity;
            }

            if (loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
                return processInternalMerchantTopupEligibility(lendingPaymentSchedule, lendingApplication);
            }

            if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {

                if (PAYU.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc())) {
                    LendingApplicationLenderDetailsSlave lendingApplicationLenderDetails = lendingApplicationLenderDetailsDaoSlave.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), "ACTIVE", lendingPaymentSchedule.getNbfc());
                    if (!payUKycService.invokeKycValidity(lendingApplication.getId(), lendingApplicationLenderDetails.getLeadId())) {
                        addRejectionReason(eligiblity, "Payu parent application kyc is not valid for this merchant");
                        log.info("Payu parent application kyc is not valid for this merchantId: {}", lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }
                }

                if(LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
                    return ExistingTopupRuleEngineV3(lendingPaymentSchedule, lendingApplication, createTopupAppCheck);
                }

                if(lendingApplication.getTenureInMonths() >= 12 &&  LENDER_TO_SKIP_POS_CHECK.stream().anyMatch(l -> l.equalsIgnoreCase(lendingApplication.getLender().trim()))) {
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

                if (lendingApplication.getTenureInMonths() < 12) {
                    if (paidRatio > 0.5D && paidRatio <= 0.95D) {
                        log.info("topup tenure {} months of merchantId: {}", lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                        return ExistingTopupRuleEngineV3(lendingPaymentSchedule, lendingApplication, createTopupAppCheck);
                    } else {
                        addRejectionReason(eligiblity, "Paid ratio requirement not met for tenure < 12 months");
                        log.info("Paid ratio {} not in range (0.5-0.95) for tenure {} months, merchantId: {}",
                                paidRatio, lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }
                }

                if (lendingApplication.getTenureInMonths() >= 12) {
                    if ((LENDER_TO_SKIP_POS_CHECK.stream().anyMatch(l -> l.equalsIgnoreCase(lendingApplication.getLender().trim()))) || (paidRatio > 0.75D && paidRatio <= 0.95D)) {
                        log.info("topup tenure {} months of merchantId: {}", lendingApplication.getTenureInMonths(), lendingPaymentSchedule.getMerchantId());
                        return AdditionalTopupRuleEngineV3(lendingPaymentSchedule, lendingApplication, createTopupAppCheck);
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


    private void addRejectionReason(List<LoanEligibilityDTO> eligibility, String reason) {
        log.info("TopUp rejected:{}", reason);
        LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
        loanEligibilityDTO.setRejectionReason(reason);
        loanEligibilityDTO.setIsRejected(true);
        eligibility.add(loanEligibilityDTO);
    }

    private List<LoanEligibilityDTO> processInternalMerchantTopupEligibility(LendingPaymentScheduleDTO lendingPaymentSchedule,
                                                                             LendingApplication lendingApplication) {
        List<LoanEligibilityDTO> eligibility = new ArrayList<>();

        try {
            logger.info("Processing internal merchant topup eligibility for merchantId: {}", lendingPaymentSchedule.getMerchantId());

            // Get existing eligible loans
            Long experianId = null;
            Boolean sevenDayFlag = LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().equals(EdiModel.SEVEN_DAY_MODEL);
            List<LendingEligibleLoan> eligibleLoanList = eligibleLoanDao.findLatestByMerchantIdAndLoanTypeAndPayableDays(
                    lendingPaymentSchedule.getMerchantId(), "TOPUP", sevenDayFlag);

            // Create internal merchant loan
            String lender = lendingApplication.getLender();
            int ediAmount = roundDownEligibleLenders.contains(lender) ? 664 : 665;
            int ediCount = 360;

            LendingEligibleLoan internalMerchantLoan = new LendingEligibleLoan(
                    lendingPaymentSchedule.getMerchantId(), experianId, 200000D, "12 Months", "ACTIVE",
                    null, 0, 0, null, ediAmount, 0, ediAmount * ediCount, null, "TOPUP", null);

            internalMerchantLoan.setEdiCount(ediCount);
            internalMerchantLoan.setRateOfInterest(1.63);
            internalMerchantLoan.setProcessingFee(9420);
            internalMerchantLoan.setProcessingFeeRate(0.0471);
            internalMerchantLoan.setId(644147506L);
            internalMerchantLoan.setTenureInMonths(12);
            internalMerchantLoan.setCreatedAt(new Date());

            eligibleLoanDao.save(internalMerchantLoan);
            eligibleLoanList.add(internalMerchantLoan);

            // Get previous loan unpaid amount
            double prevLoanUnpaidAmount = getPreviousLoanAmount(lendingPaymentSchedule);
            BigDecimal prevLoanUnpaidAmountBD = BigDecimal.valueOf(prevLoanUnpaidAmount);
            String topupLender = topupLenderMapper(lendingPaymentSchedule.getNbfc());

            // Process each eligible loan
            for (LendingEligibleLoan eligibleLoan : eligibleLoanList) {
                logger.info("Processing eligible loan: {}", eligibleLoan);

                try {
                    // Get risk variables
                    LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingPaymentSchedule.getMerchantId());
                    if (lendingRiskVariables == null) {
                        logger.warn("Risk variables not found for merchant: {}", lendingPaymentSchedule.getMerchantId());
                        continue;
                    }

                    // Calculate processing fee
                    BigDecimal processingFee = calculateProcessingFeeForTopup(
                            eligibleLoan, lendingPaymentSchedule, lendingApplication,
                            lendingRiskVariables, prevLoanUnpaidAmountBD, topupLender);

                    if (processingFee == null) {
                        logger.error("Processing fee calculation failed for eligible loan: {}", eligibleLoan.getId());
                        continue;
                    }

                    if (additionalTopupChecksFailed(lendingPaymentSchedule, eligibleLoan, lendingApplication, topupLender)) {
                        logger.info("Additional topup checks failed for merchant id: {}", lendingPaymentSchedule.getMerchantId());
                        continue;
                    }

                    LoanEligibilityDTO loanEligibilityDTO = buildLoanEligibilityDTO(
                            eligibleLoan, lendingPaymentSchedule, processingFee, prevLoanUnpaidAmount);

                    eligibility.add(loanEligibilityDTO);
                    logger.info("Successfully processed eligible loan: {}", eligibleLoan.getId());

                } catch (Exception e) {
                    logger.error("Error processing eligible loan: {} for merchant: {}, error: {}",
                            eligibleLoan.getId(), lendingPaymentSchedule.getMerchantId(), e.getMessage(), e);
                }
            }

            logger.info("Completed processing internal merchant topup eligibility. Found {} eligible loans", eligibility.size());

        } catch (Exception e) {
            logger.error("Error in processing internal merchant topup eligibility for merchant: {}, error: {}",
                    lendingPaymentSchedule.getMerchantId(), e.getMessage(), e);
        }

        return eligibility;
    }

    private List<LoanEligibilityDTO> ExistingTopupRuleEngineV3(LendingPaymentScheduleDTO lendingPaymentSchedule, LendingApplication lendingApplication, boolean createTopupAppCheck) {
        return processTopupRuleEngine(lendingPaymentSchedule, lendingApplication, createTopupAppCheck, false);
    }

    private List<LoanEligibilityDTO> processTopupRuleEngine(LendingPaymentScheduleDTO lendingPaymentSchedule,
                                                            LendingApplication lendingApplication,
                                                            boolean createTopupAppCheck,
                                                            boolean isAdditionalTopup) {
        List<LoanEligibilityDTO> eligibility = new ArrayList<>();
        List<LendingEligibleLoan> eligibleLoansToSave = new ArrayList<>();
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


            // Handle internal merchant loans
//            if (loanUtil.isInternalMerchant(lendingPaymentSchedule.getMerchantId())) {
//                LendingEligibleLoan internalMerchantLoan = new LendingEligibleLoan(
//                        lendingPaymentSchedule.getMerchantId(), experianId, 300000D, "12 Months", "ACTIVE",
//                        null, 0, 0, null, 1149, 0, 357339, null, "TOPUP", null);
//                internalMerchantLoan.setRateOfInterest(1.59);
//                internalMerchantLoan.setProcessingFee(14130);
//                internalMerchantLoan.setProcessingFeeRate(0.05D);
//                internalMerchantLoan.setId(644147506L);
//
//                if (eligibleLoanList == null) {
//                    eligibleLoanList = new ArrayList<>();
//                }
//                eligibleLoanList.add(internalMerchantLoan);
//            }

            // Process when no eligible loans found
            if (CollectionUtils.isEmpty(eligibleLoanList)) {
                Double eligibleAmount = 0D;
                GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimitV2(
                        lendingPaymentSchedule.getMerchantId(), EligibilityRequestSource.EASY_LOANS);

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

                int foreclosureAmount = lmsLoanDetailsService.getForeclosureAmount(lendingApplication.getMerchantId(), lendingApplication.getExternalLoanId());
                if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
                    if (eligibleAmount - foreclosureAmount < 10000) {
                        addRejectionReason(eligibility, "Outstanding amount less than 10k");
                        log.info("Outstanding amount less than 10k for merchant:{}", lendingPaymentSchedule.getMerchantId());
                        return eligibility;
                    }
                }

                List<GlobalLimitResponse.OfferDetail> offerDetails = globalLimitResponse.getData().getOfferDetails();

                Set<Double> processedAmounts = new HashSet<>();
                List<LendingEligibleLoan> loansByEligibleAmount = new ArrayList<>();

                for (GlobalLimitResponse.OfferDetail offerDetail : offerDetails) {
                    Double loanAmount = offerDetail.getLoanAmount();
                    if (!processedAmounts.contains(loanAmount)) {
                        processedAmounts.add(loanAmount);
                        loansByEligibleAmount.addAll(
                                loanDetailsServiceV2.recomputeEligibleLoanV2(globalLimitResponse, loanAmount, lendingPaymentSchedule.getMerchantId())
                        );
                        log.info("Processed loanAmount: {}, loansByEligibleAmount size: {}", loanAmount, loansByEligibleAmount.size());
                    }
                }

                Double minimumAllowedAmount = getMinimumAllowedAmount((double)foreclosureAmount,lendingPaymentSchedule.getNbfc());
                Double minimumAmount = 10000 * Math.ceil(minimumAllowedAmount / 10000.0);
                List<LendingEligibleLoan> loansByMinimumAmount = loanDetailsServiceV2.recomputeEligibleLoanV2(globalLimitResponse, minimumAmount, lendingPaymentSchedule.getMerchantId());

                eligibleLoanList = new ArrayList<>(loansByEligibleAmount);
                log.info("eligibleLoanList after adding loansByEligibleAmount: {}", eligibleLoanList);

                if (loansByMinimumAmount != null) {
                    eligibleLoanList.addAll(loansByMinimumAmount);
                }
                log.info("eligibleLoanList after adding loansByMinimumAmount: {}", eligibleLoanList);
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

                if (additionalTopupChecksFailed(lendingPaymentSchedule, eligibleLoan, lendingApplication, topupLender)) {
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
                eligibleLoansToSave.add(eligibleLoan);
            }

            if (!eligibleLoansToSave.isEmpty()) {
                eligibleLoanDao.saveAll(eligibleLoansToSave);
                eligibleLoanDao.flush();
                log.info("Saved {} eligible loans for merchant {}", eligibleLoansToSave.size(),
                        lendingPaymentSchedule.getMerchantId());
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
                lendingRiskVariablesDao.save(lendingRiskVariables);
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

    private List<LoanEligibilityDTO> eligibilityFromEligibleLoans(List<LendingEligibleLoan> eligibleLoanList, LendingPaymentScheduleDTO lendingPaymentSchedule) {
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

    private Double getMinimumAllowedAmount(Double foreclosureAmount, String lender) {
        if (ABFL.name().equals(lender)) {
            return foreclosureAmount + topupABFLMinimumAdditionalAmount;
        }
        return foreclosureAmount + topupMinimumAdditionalAmount;
    }

    public BigDecimal calculateProcessingFeeForTopup(LendingEligibleLoan eligibleLoan,
                                                     LendingPaymentScheduleDTO lendingPaymentSchedule,
                                                     LendingApplication lendingApplication,
                                                     LendingRiskVariables lendingRiskVariables,
                                                     BigDecimal prevLoanUnpaidAmountBD, String topupLender) {

        // Try pricing experiment first
        PricingExperiment pricingExperiment = null;
        if (pricingExpEnabled) {
            pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(
                    lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                    eligibleLoan.getTenureInMonths(), String.valueOf(lendingPaymentSchedule.getMerchantId()),
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
                                                       LendingPaymentScheduleDTO lendingPaymentSchedule,
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

    private boolean additionalTopupChecksFailed(LendingPaymentScheduleDTO lendingPaymentSchedule, LendingEligibleLoan eligibleLoan, LendingApplication lendingApplication, String topupLender) {
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
                    eligibleLoan.getTenureInMonths(), String.valueOf(lendingApplication.getMerchantId()), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
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

        if (PAYU.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc())) {
            LendingApplicationLenderDetailsSlave lendingApplicationLenderDetails = lendingApplicationLenderDetailsDaoSlave.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), "ACTIVE", lendingPaymentSchedule.getNbfc());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !payUKycService.invokeKycValidity(lendingApplication.getId(), lendingApplicationLenderDetails.getLeadId())) {
                log.info("Payu parent application kyc is not valid for this merchantId: {}", lendingPaymentSchedule.getMerchantId());
                return true;
            }
        }
        return false;
    }


    private List<LoanEligibilityDTO> AdditionalTopupRuleEngineV3(LendingPaymentScheduleDTO lendingPaymentSchedule, LendingApplication lendingApplication, boolean createTopupAppCheck) {
        return processTopupRuleEngine(lendingPaymentSchedule, lendingApplication, createTopupAppCheck, true);

    }


    private void setTopupDetailsV2(Long merchantId, @NotNull List<LoanEligibilityDTO> loans, TopupEligibilityResponseData responseDTO, LendingPaymentScheduleDTO lendingPaymentSchedule) throws Exception {
        if (ObjectUtils.isEmpty(loans)) {
            log.info("No loan eligibility data found for merchant {}", merchantId);
            return;
        }
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
            int foreclosureAmount = foreclosureService.getForeclosureAmount(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getMerchantId());
            Double minimumAllowedAmount = getMinimumAllowedAmount((double) foreclosureAmount,lendingPaymentSchedule.getNbfc());
            Double minimumAmount = 10000 * Math.ceil(minimumAllowedAmount / 10000.0);
            Double highestAmount = getHighestAmount(topUpLoans);
            if(!ObjectUtils.isEmpty(minimumAmount) && !ObjectUtils.isEmpty(highestAmount) && highestAmount < minimumAmount){
                log.info("Topup loan maximum allowed amount {} is less than minimum allowed amount {} for merchant {}", highestAmount, minimumAmount, merchantId);
                responseDTO.setIsRejected(true);
                responseDTO.setRejectionReason("Top-up loan not available as the eligible amount is less than minimum allowed amount");
                responseDTO.setTopup(Boolean.FALSE);
                populateTopupBannerAudit(lendingPaymentSchedule,"topup_banner_shown", "false", "Top-up loan not available as the eligible amount is less than minimum allowed amount");
                return;
            }
            List<String> tenures = topUpLoans.stream()
                    .map(LoanEligibilityDTO::getTenureInMonths)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .map(tenure -> tenure + " Months")
                    .collect(Collectors.toList());

            responseDTO.setMinimumAllowedAmount(minimumAmount);
            responseDTO.setMaximumAllowedAmount(highestAmount);
            responseDTO.setEligibility(loans);
            responseDTO.setTenures(tenures);
            responseDTO.setTopup(Boolean.TRUE);
            responseDTO.setTopupLender(topupLenderMapper(lendingPaymentSchedule.getNbfc()));
            responseDTO.setTopupV2FlowEnabled(true);

            funnelService.submitEventV3(
                    merchantId, null, null,
                    FunnelEnums.StageId.LOAN_DASHBOARD,
                    FunnelEnums.StageEvent.TOPUP_ELIGIBLE,
                    null, LoanDetailsConstant.FUNNEL_VERSION_TAG
            );
        }
    }

    private void populateTopupBannerAudit(LendingPaymentScheduleDTO lendingPaymentSchedule, String identifier, String topupBannerShown, String reasonNotShown) {
        MerchantConfigInfo topupBannerVisibility = new MerchantConfigInfo();
        topupBannerVisibility.setMerchantId(lendingPaymentSchedule.getMerchantId());
        topupBannerVisibility.setLender(lendingPaymentSchedule.getNbfc());
        topupBannerVisibility.setIdentifier(identifier);
        topupBannerVisibility.setState(topupBannerShown);
        topupBannerVisibility.setComment(reasonNotShown);
        topupBannerVisibility.setApplicationId(lendingPaymentSchedule.getApplicationId());
        log.info("Publishing data to BQ for topup banner for merchant id {}",
                lendingPaymentSchedule.getMerchantId());
        bqPublisherUtil.publish("Lending", "merchant_config_info",
                topupBannerVisibility);
    }


    private Double getHighestAmount(List<LoanEligibilityDTO> loans) {
        return loans.stream()
                .map(LoanEligibilityDTO::getAmount)
                .map(Integer::doubleValue)
                .max(Double::compareTo)
                .orElse(null);
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
                    else if (PAYU.name().equalsIgnoreCase(prevApplication.getLender()) && minutes < payuTopupRejectionBannerTat) {
                        log.info("PAYU topup application rejected for merchantId : {} less than {} minutes ago", merchantId, payuTopupRejectionBannerTat);
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

}

