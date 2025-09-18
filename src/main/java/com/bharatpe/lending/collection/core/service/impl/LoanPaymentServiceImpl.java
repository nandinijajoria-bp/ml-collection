package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.dao.LendingEDIScheduleDao;
import com.bharatpe.common.entities.LendingEDISchedule;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.LoanClosureDTO;
import com.bharatpe.lending.collection.core.dto.internal.LoanPaymentDetailDTO;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;
import com.bharatpe.lending.collection.core.service.AdjustLoanBalanceService;
import com.bharatpe.lending.collection.core.service.LoanPaymentLedgerAdjustmentService;
import com.bharatpe.lending.collection.core.service.LoanPaymentService;
import com.bharatpe.lending.collection.core.service.LoanStatusService;
import com.bharatpe.lending.collection.core.utils.LoanPaymentUtil;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.enums.LoanStatus;
import com.bharatpe.lending.enums.WaiverType;
import com.bharatpe.lending.loanV2.service.ExcessNachService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.NachBounceChargesService;
import com.bharatpe.lending.service.SettlementService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.bharatpe.lending.common.enums.LoanPaymentMode.*;
import static com.bharatpe.lending.common.enums.LoanSettlementMechanism.*;
import static com.bharatpe.lending.common.enums.PerpetualDpdAdjusted.Y;
import static com.bharatpe.lending.common.enums.TransferTypeModes.DIRECT_TRANSFER_LENDER;
import static com.bharatpe.lending.common.enums.TransferTypeModes.TRANSFER_BY_BP;
import static com.bharatpe.lending.constant.CommonConstants.PAYMENT_LOCK_KEY_PREFIX;
import static com.bharatpe.lending.constant.LendingConfigKeys.ADVANCE_EDI;
import static com.bharatpe.lending.enums.Lender.PIRAMAL;


@Service
@Slf4j
public class LoanPaymentServiceImpl implements LoanPaymentService {

    public static final String LOAN_PAYMENT_ORDER_ID_PREFIX = "LOAN";

    //Allowed -  Arrays.asList(NACH, ADVANCE, OTHER)
    public static final List<LoanPaymentMode> PAYMENT_ADJUSTMENT_PREFRENCE_LIST = Arrays.asList(NACH, OTHER);
    public static final String PAYMENT_EXCESS_SOURCE = "PAYMENT";

    HashSet<String> ALLOWED_POSTING_TRANSFER_BP_MODE = new HashSet<>(Arrays.asList("SETLLEMENT", "FP"));

    public static final String LOAN_PAYMENT_ORDER_SOURCE_EXCESS_NACH = "LOAN";

    public static final String EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX = "_adjust_";

    public static final List<String> WAIVER_LIST = Arrays.asList(WaiverType.EXCEPTION.name(), WaiverType.DECEASED_SCHEME.name(), WaiverType.SCHEME1.name(), WaiverType.SCHEME.name());

    @Autowired
    LendingPrepaymentDao lendingPrepaymentDao;

    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;

    @Autowired
    LoanPaymentOrderDao loanPaymentOrderDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;
    @Autowired
    LendingEDIScheduleDao lendingEDIScheduleDao;

    @Autowired
    LoanForeClosureChargesDao loanForeClosureChargesDao;
    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingPayinDetailsDao lendingPayinDetailsDao;

    @Autowired
    AdjustLoanBalanceByIPCServiceImpl adjustLoanBalanceByIPCService;

    @Autowired
    AdjustLoanBalanceByEdiByEdiServiceImpl adjustLoanBalanceByEdiByEdiService;

    @Autowired
    AdjustLoanBalanceByNPAServiceImpl adjustLoanBalanceByNPAService;

    @Autowired
    LoanPaymentLedgerAdjustmentService ledgerAdjustmentService;

    @Autowired
    LoanStatusService loanStatusService;
    @Autowired
    ExcessNachService excessNachService;

    @Autowired
    LendingRefundAuditDao lendingRefundAuditDao;

    @Autowired
    LendingNotificationService lendingNotificationService;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingCollectionAuditDao lendingCollectionAuditDao;

    ExecutorService notificationExecutor = Executors.newFixedThreadPool(10);

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingPaymentScheduleLendingCommonDao lendingPaymentScheduleLendingCommonDao;

    @Autowired
    SettlementService settlementService;

    @Autowired
    NachBounceChargesService nachBounceChargesService;

    @Autowired
    private AdjustChargesServiceImpl adjustChargesService;

    @Value("${payu.nach.bounce.charge:500}")
    Integer payUNachBounceCharge;

    @Value("#{'${lca.payin.record.eligible.lenders:}'.split(',')}")
    Set<String> lcaPayinRecordEligibleLenders;

    @Override
    @Transactional
    public LendingPaymentSchedule adjustMoney(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment) {
        boolean isPaymentLockEnabled = loanUtil.isPaymentLockEnabled(loan);
        boolean isLockAcquired = isPaymentLockEnabled ? true : false;
        try {
            log.info("adjustMoney for loan: {} and payment {} started ", loan, payment);

            if (Objects.isNull(loan) || Objects.isNull(payment)) return loan;
            String mechanism = LoanPaymentUtil.getLoanSettlementMechanism(loan);
            if (isPaymentLockEnabled && !loanUtil.isPaymentLockAcquired(PAYMENT_LOCK_KEY_PREFIX + loan.getId())) {
                log.info("Payment lock already acquired for loanId: {}, skipping payment", loan.getId());
                isLockAcquired = false;
                // Method signature doesn't allow to throw checked exception
                throw new RuntimeException("Some payment already in process for this loan id: " + loan.getId());
            }
            // Re-fetching a fresh copy of loan to avoid inconsistency in due_amount(stale/old) in case of multiple payment received simultaneously
            Optional<LendingPaymentSchedule> loanOptional = lendingPaymentScheduleDao.findById(loan.getId());
            if (!loanOptional.isPresent()) {
                log.error("Lending Payment Schedule is not available with id : {}", loan.getId());
                return loan;
            }
            loan = loanOptional.get();

            createLoanFundInEntry(loan, payment);

            adjustMoney(loan, payment, mechanism);
            log.info("adjustMoney for loan: {} and payment {} complete", loan, payment);
            return loan;
        } finally {
            if (isLockAcquired) {
                loanUtil.releasePaymentLock(PAYMENT_LOCK_KEY_PREFIX + loan.getId());
            }
        }
    }

    private LendingPaymentSchedule adjustMoney(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment, String settlementMechanism) {
        if (StringUtils.hasLength(settlementMechanism)) {
            if (IPC.name().equalsIgnoreCase(settlementMechanism))
                return adjustMoney(loan, payment, PAYMENT_ADJUSTMENT_PREFRENCE_LIST, IPC);
            if (EDI_BY_EDI.name().equalsIgnoreCase(settlementMechanism))
                return adjustMoney(loan, payment, PAYMENT_ADJUSTMENT_PREFRENCE_LIST, EDI_BY_EDI);
            if (NPA.name().equalsIgnoreCase(settlementMechanism))
                return adjustMoney(loan, payment, PAYMENT_ADJUSTMENT_PREFRENCE_LIST, NPA);
        }
        return loan;
    }

    private LendingPaymentSchedule adjustMoney(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment, List<LoanPaymentMode> loanPaymentModes, LoanSettlementMechanism settlementMechanism) {
        log.info("adjustMoney for loan: {} and payment {} with settlementMechanism: {}", loan, payment, settlementMechanism);
        if (!CollectionUtils.isEmpty(loanPaymentModes) && settlementMechanism != null) {
            log.info("Checking for loan foreclosure for loanId: {}", loan.getId());
            boolean loanForeClosed = checkForLoanForeClosure(loan, payment, settlementMechanism.name());
            log.info("Loan foreclosure check for loanId: {} with payment: {} and settlementMechanism: {} result: {}", loan.getId(), payment, settlementMechanism.name(), loanForeClosed);
            if (!loanForeClosed) {
                log.info("Loan foreclosure not required for loanId: {} with payment: {} and settlementMechanism: {}", loan.getId(), payment, settlementMechanism.name());
                for (LoanPaymentMode paymentMode : loanPaymentModes) {
                    log.info("adjustMoney for loanId: {} paymentMode {} by mechanism {} ", loan.getId(), paymentMode, settlementMechanism.name());
//                    Raise Dues for settlement initiated cases if received amount is more then dues created
                    if (loan.getSettlementInitiated() && ((ADVANCE.equals(paymentMode) && payment.getAdvanceEdiAmount() > loan.getDueAmount())
                            || (OTHER.equals(paymentMode) && payment.getOtherAmount() > loan.getDueAmount()))) {
                        raiseDuesInLedger(loan, payment);
                    }
                    if (NACH.equals(paymentMode) && payment.isAdjustExcessNach())
                        adjustExcessNachBalanceAndLedger(loan, settlementMechanism.name());
                    if (ADVANCE.equals(paymentMode))
                        adjustAdvancePaymentAndLedger(loan, payment.getAdvanceEdiAmount(), settlementMechanism.name());
                    if (OTHER.equals(paymentMode))
                        adjustOtherPaymentAndLedger(loan, payment, settlementMechanism.name());
                }
                lendingPaymentScheduleDao.save(loan);
                if (loan.getSettlementInitiated()) {
                    settlementService.checkForLoanSettlement(loan);
                }
            }
            lendingPaymentScheduleDao.save(loan);
        }
        return loan;
    }


    //==================================================================================================================
    // __________________________________     Payment Adjustment    ____________________________________________________
    //==================================================================================================================

    private void adjustOtherPaymentAndLedger(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment, String mode) {
        PaymentCalculation otherAdjustment = adjustPayment(loan, payment.getOtherAmount(), mode);
        adjustExtraAmountIfAny(loan, otherAdjustment.getBalance(), payment, true);
        LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(String.valueOf(payment.getOrderId()));
        log.info("got order for loanId:{} orderId:{} order:{}", loan.getId(), payment.getOrderId(), order);
        ledgerAdjustmentService.adjustLendingLedger(loan, otherAdjustment, order, payment.getDescription(), payment.getSource(), payment.getTransferType(), payment.getTerminalOrderId());
        ledgerAdjustmentService.adjustPenaltyLedger(loan, otherAdjustment, payment.getSource(), false, payment.getTerminalOrderId());
        if (payment.isUpdateGlobalTxnlimit()) updateGlobaltxnLimit(loan.getMerchantId(), "CREDIT", otherAdjustment.getPrincipleSettled());
    }

    private void updateGlobaltxnLimit(Long merchantId, String mode, double amount) {
        log.info("amount:{} in lending global limit for merchant:{} type :{}", amount, merchantId, mode);
        if(StringUtils.hasLength(mode) && amount > 0) {
            notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(merchantId, mode, amount));
        }
    }

    private void adjustExtraAmountIfAny(LendingPaymentSchedule loan, double balance, LoanPaymentDetailDTO payment, boolean refund) {
        if (balance >= 1) {
            refund = refund || "CLOSED".equalsIgnoreCase(loan.getStatus());

            // LC-565
            // note - terminal payment also in that loan will be there in active state as loan is closed marked by the
            // edi scheduler (in non foreclosure cases) and there is separate job to create refund entry from active excess balance of closed loans
            if ("ACTIVE".equalsIgnoreCase(loan.getStatus()) || LoanStatus.DECEASED.name().equalsIgnoreCase(loan.getStatus()) || LoanStatus.INACTIVE_TOPUP.name().equalsIgnoreCase(loan.getStatus())) refund = false;

            if (refund) {
                createLoanRefund(loan, balance, payment);
            } else {
                createLoanExcess(loan, balance, payment);
            }
        }
    }

    private void createLoanExcess(LendingPaymentSchedule loan, double amount, LoanPaymentDetailDTO payment) {
        Date ledgerDate = new Date();
        Optional<LendingPaymentScheduleLendingCommon> lendingPaymentScheduleLendingCommon = lendingPaymentScheduleLendingCommonDao.findById(loan.getId());
        if(lendingPaymentScheduleLendingCommon.isPresent() && Y.name().equalsIgnoreCase(lendingPaymentScheduleLendingCommon.get().getPerpetualDpdAdjusted()) && !"UPI_AUTOPAY".equalsIgnoreCase(payment.getSource())){
            ledgerDate = DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), 1);
        }
        log.info("Creating Excess balance for merchant:{} amount:{} source:{}", loan.getMerchantId(), amount, payment.getSource());
        LendingCollectionExcess lendingCollectionExcess = new LendingCollectionExcess();
        lendingCollectionExcess.setLoanId(loan.getId());
        lendingCollectionExcess.setMerchantId(loan.getMerchantId());
        lendingCollectionExcess.setStatus("ACTIVE");
        lendingCollectionExcess.setAmount(amount);
        lendingCollectionExcess.setExcessNachCreditAmount(amount);
        lendingCollectionExcess.setMode(payment.getSource());
        lendingCollectionExcess.setTerminalOrderId(StringUtils.hasLength(payment.getTerminalOrderId()) ? payment.getTerminalOrderId() : payment.getBankRefNo());
        lendingCollectionExcess.setDeductionCount(0);
        lendingCollectionExcess.setCreditDate(ledgerDate);
        lendingCollectionExcess.setTransferType(payment.getTransferType());
        lendingCollectionExcess.setDeductedAmount(0D);
        lendingCollectionExcess.setSource(PAYMENT_EXCESS_SOURCE);
        lendingCollectionExcessDao.save(lendingCollectionExcess);
        log.info("created Excess balance Collection credit entry of amount:{} for merchant:{}", amount, loan.getMerchantId());
        // As we already inform user when NACH excess was created from bank
        // As per product - do not send it for other source
        //if (loanPaymentUtil.excessCollectionCommunicationSmsRequired(payment.getSource())) sendExcessNachCollectionSMS(loan.getMerchantId(), loan.getId());
        createLendingCollectionAuditForExcessNachCredit(loan, payment.getTerminalOrderId(), payment.getSource(), amount, lendingCollectionExcess.getId(), payment.getTransferType(), ledgerDate);
    }

    private void createLendingCollectionAuditForExcessNachCredit(LendingPaymentSchedule lendingPaymentSchedule, String txnId, String source, Double amount, Long refId, String transferType, Date ledgerDate){
        // it is possible from the data sometime excess being created from already excess credit entry
        // hence to avoid double payment we adding check
        // allow only mode is strictly = FP, SETTLEMENT
        if (TRANSFER_BY_BP.name().equalsIgnoreCase(transferType) && !ALLOWED_POSTING_TRANSFER_BP_MODE.contains(source)) {
            log.info("its transfer by bp no lca entry is required {} {} {}", lendingPaymentSchedule.getId(), txnId, source);
            return;
        }
        if ("UPI_AUTOPAY".equalsIgnoreCase(source) &&
                ("TRILLIONLOANS".equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) ||
                        "ABFL".equalsIgnoreCase(lendingPaymentSchedule.getNbfc()))) {
            log.info("lca excess Nach credit from UPI_AUTOPAY is not required for lender {} for loanId: {}", lendingPaymentSchedule.getNbfc(), lendingPaymentSchedule.getId());
            return;
        }

        LendingCollectionAudit lendingCollectionAudit = LendingCollectionAudit.builder()
                .merchantId(lendingPaymentSchedule.getMerchantId())
                .merchantStoreId(lendingPaymentSchedule.getMerchantStoreId())
                .loanId(lendingPaymentSchedule.getId())
                .applicationId(lendingPaymentSchedule.getLoanApplication().getId())
                .bpLoanId(lendingPaymentSchedule.getLoanApplication().getExternalLoanId())
                .nbfcId(lendingPaymentSchedule.getLoanApplication().getNbfcId())
                .txnType(String.format("EXCESS_%s_CREDIT", StringUtils.hasLength(source) ? source : ""))
                .transferType(transferType == null ? CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name() : transferType)
                .status("PENDING")
                .amount(amount)
                .otherCharges(0D)
                .penalty(0D)
                .adjustmentMode(String.format("EXCESS_%s_CREDIT", StringUtils.hasLength(source) ? source : ""))
                .transferDate(ledgerDate)  // todo: check for other mode
                .terminalOrderId(txnId)
                .lender(lendingPaymentSchedule.getNbfc())
                .loanStatus(lendingPaymentSchedule.getStatus())
                .loanClosingDate(lendingPaymentSchedule.getClosingDate())
                .mobile(lendingPaymentSchedule.getMobile())
                .ledgerId(refId)
                .build();
        lendingCollectionAuditDao.save(lendingCollectionAudit);
    }
    private void sendExcessNachCollectionSMS(Long merchantId, Long loanId) {
        Optional<BasicDetailsDto> merchantBasicDetails = merchantService.fetchMerchantBasicDetails(merchantId);
        if (ObjectUtils.isEmpty(merchantBasicDetails)) {
            return;
        }
        String identifier = "LENDING_EXCESS_NACH_COLLECTION_V1";
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("loan_id", loanId);
        NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
        notificationPayloadDto.setTemplateIdentifier(identifier);
        notificationPayloadDto.setMobile(merchantBasicDetails.get().getMobile());
        notificationPayloadDto.setClientName("LENDING");
        notificationPayloadDto.setTemplateParams(templateParams);
        lendingNotificationService.notify(notificationPayloadDto);
    }

    private void createLoanRefund(LendingPaymentSchedule loan, double amount, LoanPaymentDetailDTO payment) {
        LendingRefundAudit lendingRefundAudit = new LendingRefundAudit();
        lendingRefundAudit.setDueAmount(loan.getDueAmount());
        lendingRefundAudit.setLoanId(loan.getId());
        lendingRefundAudit.setMerchantId(loan.getMerchantId());
        lendingRefundAudit.setMode(payment.getSource());
        lendingRefundAudit.setBankRefNo(payment.getBankRefNo());
        lendingRefundAudit.setRefundAmount(amount);
        lendingRefundAudit.setOrderAmount(payment.getOtherAmount());
        lendingRefundAuditDao.save(lendingRefundAudit);
    }

    // TODO : not usable  - fix it
    private void adjustAdvancePaymentAndLedger(LendingPaymentSchedule loan, double advanceEdiAmount, String mode) {
        PaymentCalculation advanceAdjustment = adjustPayment(loan, advanceEdiAmount, mode);
        LoanPaymentOrder order = ledgerAdjustmentService.createLoanPaymentOrder(loan, advanceAdjustment.getUsed(), null, CreditConstants.PaymentStatus.PENDING.name(), "ADVANCE_EDI", null);
        ledgerAdjustmentService.adjustAdvanceEdiLedger(loan, advanceAdjustment);
        //todo:ask how to adjust in ledger
        ledgerAdjustmentService.adjustLendingLedger(loan, advanceAdjustment, order, null, null, null, null);
    }

    private void adjustExcessNachBalanceAndLedger(LendingPaymentSchedule loan, String mode) {
        log.info("adjustExcessNachBalanceAndLedger : processing nach adjustment for loanId :{} mode : {} ", loan.getId(), mode);
        List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(loan.getMerchantId(), loan.getId(), "ACTIVE");
        for (LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList) {
            log.info("adjustExcessNachBalanceAndLedger : processing excess nach for loanId :{} and NachId :{} and balance : {}", loan.getId(), lendingCollectionExcess.getId(), lendingCollectionExcess.getAmount());
            double penaltyFee = Objects.nonNull(loan.getDuePenalty()) ? loan.getDuePenalty() : 0d;
            Double deductionAmount = Math.min(lendingCollectionExcess.getAmount(), (loan.getDueAmount() + penaltyFee));
            if (deductionAmount < 1D) continue;
            if (Objects.isNull(lendingCollectionExcess.getTerminalOrderId())) continue;

            //Creating loan payment order for deduction from excess nach credit
            String source = LOAN_PAYMENT_ORDER_SOURCE_EXCESS_NACH;
            String orderId = LOAN_PAYMENT_ORDER_ID_PREFIX + loan.getId() + System.currentTimeMillis();
            LoanPaymentOrder order = ledgerAdjustmentService.createLoanPaymentOrder(loan, deductionAmount, lendingCollectionExcess.getTerminalOrderId(), CreditConstants.PaymentStatus.PENDING.name(), source, orderId);

            PaymentCalculation nachAdjustment = adjustPayment(loan, lendingCollectionExcess.getAmount(), mode);
            String status = (deductionAmount == nachAdjustment.getUsed()) ? "OK" : "MISMATCH";
            log.info("adjustExcessNachBalanceAndLedger :  order vs ledger status: {} order: {} and nachAdjustment: {}", status, order, nachAdjustment);

            String transferType = (StringUtils.hasLength(lendingCollectionExcess.getTransferType())) ? lendingCollectionExcess.getTransferType() : CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name();
            String adjustmentMode = LoanPaymentUtil.getExcessAdjustedModeDesc(lendingCollectionExcess.getMode());

            ledgerAdjustmentService.adjustNachLedger(lendingCollectionExcess, nachAdjustment);
            String terminalOrderId = lendingCollectionExcess.getTerminalOrderId() + EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX + lendingCollectionExcess.getDeductionCount().toString();
            ledgerAdjustmentService.adjustLendingLedger(loan, nachAdjustment, order, terminalOrderId, adjustmentMode, transferType, terminalOrderId);
            ledgerAdjustmentService.adjustPenaltyLedger(loan, nachAdjustment, source, false, terminalOrderId);
        }
    }

    private PaymentCalculation adjustPayment(LendingPaymentSchedule loan, double amount, String mode) {
        return getLoanBalanceAdjustmentService(mode).adjustLoanBalance(loan, amount);
    }

    public AdjustLoanBalanceService getLoanBalanceAdjustmentService(String mode) {
        switch (mode) {
            case "EDI_BY_EDI":
                return adjustLoanBalanceByEdiByEdiService;
            case "NPA":
                return adjustLoanBalanceByNPAService;
            default:
                return adjustLoanBalanceByIPCService;
        }
    }

    private boolean checkForLoanForeClosure(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment, String settlementMechanism) {
        log.info("checkForLoanForeClosure for loan: {} and payment: {} and settlementMechanism: {}", loan, payment, settlementMechanism);
        if ("CLOSED".equalsIgnoreCase(loan.getStatus())) {
            log.info("loan already closed. {}", loan);
            return false;
        }

        log.info("checkForLoanForeClosure: fetching Foreclosure Amount for loan: {}", loan.getId());
        Integer principalDueAmount = getForeclosureAmount(loan);
        log.info("checkForLoanForeClosure: Foreclosure Amount for loan: {} is {}", loan.getId(), principalDueAmount);
        // case 1 (-ve/0 foreclosure) -  we already have sufficient fund no need for additional payment
        //                                     any upcoming payment will lead to unwanted foreclosure
        // Case 2 - (foreclosure < loan due) - we have some excess amount and if someone paying due will
        //                                     foreclose un-intentionally
        // case 3 - due_amount == foreclosure - can't do anything ....
        if (principalDueAmount <= 0 || principalDueAmount < loan.getDueAmount()) {
            log.info("checkForLoanForeClosure: loanId: {} principalDueAmount: {} is less than or equal to 0 or less than due amount: {}. No need for fore closure", loan.getId(), principalDueAmount, loan.getDueAmount());
            return false;
        }

        if(loanUtil.isTodayIsLoanLastDay(loan)){
            log.info("checkForLoanForeClosure: loanId: {} is last day of loan, so not checking for fore closure", loan.getId());
            return false;
        }
        // there will be some pending txn before release this description field wasn't populated
        // will enable this check later in some days
//        if (!payment.isForeCloser()) {
//            return false;
//        }

        Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(loan);
        log.info("checkForLoanForeClosure: EDI Holiday Interest Amount for loan: {} is {}", loan.getId(), ediHolidayInterestAmount);

        double amount = payment.getOtherAmount();
        log.info("checkForLoanForeClosure: payment amount for loan: {} is {}", loan.getId(), amount);

        if (principalDueAmount + ediHolidayInterestAmount - amount <= 1D) {
            log.info("checkForLoanForeClosure: loanId: {} principalDueAmount: {} + ediHolidayInterestAmount: {} - payment amount: {} is less than or equal to 1. Proceeding with fore closure", loan.getId(), principalDueAmount, ediHolidayInterestAmount, amount);
            foreCloseLoan(loan, payment, settlementMechanism, principalDueAmount, ediHolidayInterestAmount);
            return "CLOSED".equalsIgnoreCase(loan.getStatus());
        }
        return false;
    }

    private void checkAndAdjustPdpdInterestIfRequired(LendingPaymentSchedule loan) {
        LendingLedger ledger = loanUtil.fetchAdvanceEdi(loan.getId());
        double advanceInterest = (ledger == null) ? 0 : Math.abs(ledger.getInterest());

        // not pdpd loan or round-off
        if (advanceInterest < 1) return;

        adjustAdvanceInterest(loan, advanceInterest, ledger);
    }
    private void adjustAdvanceInterest(LendingPaymentSchedule loan, double advanceInterest, LendingLedger negativeLedger) {
        // chances of partial payment of advance edi
        // assume advance interest is = 100 Rs and out of which 40 Rs is paid remaining 60 Rs
        // special case interest is = 100 Rs and paid is 100 then remaining is zero
        // knock off the remaining portion of advance interest ( eg - 60 Rs)
        double adjustableInterest = Math.min(loan.getDueInterest(), advanceInterest);
        log.info("adjustAdvanceInterest# is started for adjustableInterest:{} loanId:{}  loan:{}", adjustableInterest, loan.getId(), loan);
        loan.setDueInterest(Math.max(loan.getDueInterest() - adjustableInterest, 0));
        loan.setDueAmount(Math.max(loan.getDueAmount() - adjustableInterest, 0));
        log.info("adjustAdvanceInterest#  for amount :{} loanId {}  loan :{}", adjustableInterest, loan.getId(), loan);

        // if some part of future interest is paid then we have update that amount
        double advanceInterestPaid = Math.max(advanceInterest - adjustableInterest, 0);
        log.info("adjustAdvanceInterest# advanceInterestPaid :{}", advanceInterestPaid);
        adjustAdvanceInterestToPrinciple(loan, advanceInterestPaid, negativeLedger);

        if (negativeLedger.getAmount() < 0 && adjustableInterest > 0) {
            PaymentCalculation paymentAdjusted = PaymentCalculation.builder()
                    .used(adjustableInterest)
                    .principleSettled(0)
                    .interestSettled(adjustableInterest)
                    .chargesSettled(0)
                    .build();
            ledgerAdjustmentService.createLendingLedger(loan, paymentAdjusted, "FUTURE_EDI_INTEREST_WAIVER", "EXCEPTION", "TRANSFER_BY_BP", null);
        }
    }

    private double adjustAdvanceInterestToPrinciple(LendingPaymentSchedule loan, double paidInterest, LendingLedger negativeLedger) {
        List<LendingLedger> advanceLedgerList = lendingLedgerDao.findAdvanceEdiLedgerList(loan.getId(), DateTimeUtil.getCurrentDayStartTime());
        double positiveTargetAmount = paidInterest;
        // sorted id is necessary
        for (LendingLedger _ledger : advanceLedgerList) {
            if (_ledger.getAmount() > 0 && positiveTargetAmount > 0) {
                if ((StringUtils.hasLength(_ledger.getAdjustmentMode()) && WAIVER_LIST.contains(_ledger.getAdjustmentMode()))
                        || StringUtils.isEmpty(_ledger.getTerminalOrderId())) {
                    continue;
                }

                if ("SETTLEMENT".equalsIgnoreCase(_ledger.getAdjustmentMode()) || LoanPaymentUtil.isExcessAdjustmentEntry(_ledger.getTerminalOrderId())) continue;

                double adjustableInterest = Math.max(Math.min(_ledger.getInterest(), positiveTargetAmount), 0);
                _ledger.setInterest(_ledger.getInterest() - adjustableInterest);
                loan.setPaidInterest(loan.getPaidInterest() -  adjustableInterest);

                _ledger.setPrinciple(_ledger.getPrinciple() + adjustableInterest);
                loan.setPaidPrinciple(loan.getPaidPrinciple() + adjustableInterest);

                updateLendingCollectionAudit(_ledger);
                lendingLedgerDao.save(_ledger);

                positiveTargetAmount -= adjustableInterest;
                if (positiveTargetAmount <= 0) break;
            }
        }

        double adjustedInterest = Math.max(paidInterest - positiveTargetAmount, 0);
        if (negativeLedger.getAmount() < 0 && adjustedInterest > 0) {
            log.info("ledger before future interest adjustment {}", negativeLedger);
            negativeLedger.setPrinciple(negativeLedger.getPrinciple() - adjustedInterest); // -ve - (+ve) = addition
            negativeLedger.setInterest(negativeLedger.getInterest() + adjustedInterest);   // -ve + ve = subtract
            lendingLedgerDao.save(negativeLedger);
            log.info("ledger after future interest adjustment {}", negativeLedger);
        }

        log.info("adjustAdvanceInterestToPrinciple targetAmount:{} actualAdjusted:{} and notAdjusted:{}", paidInterest, adjustedInterest, positiveTargetAmount);
        return positiveTargetAmount;
    }

    private void updateLendingCollectionAudit(LendingLedger ledger) {
        LendingCollectionAudit lca = lendingCollectionAuditDao.findByLedgerID(ledger.getId(), 1);
        log.info("updateLendingCollectionAudit ledgeId :{}, lca:{}", ledger.getId(), lca);

        if (lca != null && lca.getAmount() > 0) {
            lca.setPrinciple(ledger.getPrinciple());
            lca.setInterest(ledger.getInterest());
            lendingCollectionAuditDao.save(lca);
            log.info("updateLendingCollectionAudit  updated ledgeId :{}, lca:{}", ledger.getId(), lca);
        }
    }

    /**
     *
     * @param loan
     * @param payment
     * @param settlementMechanism
     * @param principalDueAmount
     * @param ediHolidayInterestAmount
     *
     *
     * functional use case
     *
     * if lender is PAYU
     *    check if any NACH bounce charges are Pending to be posted, If so add it to penalty and post it to lender
     *
     *
     */

    private void foreCloseLoan(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment, String settlementMechanism, Integer principalDueAmount, Integer ediHolidayInterestAmount) {
        double amount = payment.getOtherAmount();
        log.info("foreCloseLoan for loan: {} and payment: {} and settlementMechanism: {}", loan, payment, settlementMechanism);
       Integer pendingNachCharges = nachBounceChargesService.getNachCharges(loan).intValue();
        if (principalDueAmount + ediHolidayInterestAmount + pendingNachCharges - amount <= 1D) {  //foreClosure
            log.info("foreCloseLoan: loanId: {} principalDueAmount: {} + ediHolidayInterestAmount: {} + pendingNachCharges: {} - payment amount: {} is less than or equal to 1. Proceeding with fore closure", loan.getId(), principalDueAmount, ediHolidayInterestAmount, pendingNachCharges, amount);
            // releaseing the short fall principal for fore closure in pdpd for all lenders  on 9/1/25
//            if (PIRAMAL.name().equalsIgnoreCase(loan.getNbfc())) {
                checkAndAdjustPdpdInterestIfRequired(loan);
//            }

            LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(loan.getMerchantId(), loan.getId());
            double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
            double excessCollectionBalance = 0;
            List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(loan.getMerchantId(), loan.getId(), "ACTIVE");
            for (LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList) {
                if (lendingCollectionExcess.getAmount() > 0) {
                    excessCollectionBalance += lendingCollectionExcess.getAmount();
                }
            }

            LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(payment.getOrderId());
            boolean preclosureWithCharges = false;
            double foreclosureChargesAmount = 0.0;
            if (loanForeClosureCharges != null) {
                if (loanForeClosureCharges.getTax() == null) loanForeClosureCharges.setTax(0.0);
                foreclosureChargesAmount = loanForeClosureCharges.getAmount() + loanForeClosureCharges.getTax();
                preclosureWithCharges = true;
                log.info("foreclosure charges exist for the orderId {} and charges : {} amount : {}", payment.getOrderId(), loanForeClosureCharges, foreclosureChargesAmount);
            }

            log.info("Received pre closure amount:{} for loan:{}", amount, loan.getId());
            double paidInterestAmount = (loan.getDueInterest() != null ? loan.getDueInterest() : 0);
            double paidPenalty = Objects.nonNull(loan.getDuePenalty()) ? loan.getDuePenalty() : 0;
            // TODO PAYU add and post charges

            String requestId = UUID.randomUUID().toString().replaceAll("-", "");
            Boolean postChargesToLender = false;

            if(pendingNachCharges != 0d){
                log.info("Found Nach Bounce Charges for loan: {}, nbfc: {} ", loan.getId(), loan.getNbfc());
                nachBounceChargesService.createCharges(loan, requestId);
                paidPenalty += pendingNachCharges;
                postChargesToLender = true;
            }

            double paidPrincipalAmount = amount - paidInterestAmount + excessCollectionBalance - paidPenalty - foreclosureChargesAmount;
            double surplusAmount = (loan.getPaidPrinciple() + paidPrincipalAmount) - loan.getLoanAmount();
//            amount -= surplusAmount;
            if (surplusAmount > 0 && !EDI_BY_EDI.name().equalsIgnoreCase(settlementMechanism) ) {
                log.info("Extra principle received for loanId:{} and extra amount:{}", loan.getId(), surplusAmount);
                paidPrincipalAmount-=surplusAmount;
                paidInterestAmount+=surplusAmount;
            }

            log.info("Adjusting breakup amount for loan:{} is principle:{} and interest:{} and foreclosureCharges : {}", loan.getId(), paidPrincipalAmount, paidInterestAmount, foreclosureChargesAmount);
            if (EDI_BY_EDI.name().equalsIgnoreCase(settlementMechanism)) {
                double totalAmount = payment.getOtherAmount() + excessCollectionBalance + advanceEdiAmount;
                adjustLoanBalanceByEdiByEdiService.settlePreClosureLoanPayment(loan.getId(), loan.getEdiCount(), loan.getEdiRemainingCount(), loan.getSettleAllPrinciple(), totalAmount);
            } else {
                //IPC
                //No Actions
            }
            log.info("Adjusted breakup amount for loan:{} is principle:{} and interest:{} and penalty: {} and foreclosureCharges : {}", loan.getId(),
                    paidPrincipalAmount, paidInterestAmount, paidPenalty, foreclosureChargesAmount);
            String description = (preclosureWithCharges) ? "PREPAYMENT_WITH_CHARGES" : "PREPAYMENT";

            if (Objects.isNull(loan.getDueAmount())) loan.setDueAmount(0d);
            //as we are creating negative penalty in lending ledger when we apply penalty so we have removed paid penalty component while creating negative foreclosure amount.
            PaymentCalculation paymentAdjusted = PaymentCalculation.builder()
                    .used(-1 * Math.abs(amount - loan.getDueAmount() - paidPenalty + advanceEdiAmount + excessCollectionBalance))
                    .principleSettled(-1 * Math.abs(amount - loan.getDueAmount() - ediHolidayInterestAmount + advanceEdiAmount + excessCollectionBalance - paidPenalty - foreclosureChargesAmount))
                    .interestSettled(-1 * Double.valueOf(ediHolidayInterestAmount))
                    .chargesSettled(-1 * foreclosureChargesAmount)
                    .build();
            ledgerAdjustmentService.createLendingLedger(loan, paymentAdjusted, description, payment.getSource(), payment.getTransferType(), payment.getTerminalOrderId());
            markExcessNachAdjusted(loan, lendingCollectionExcessList);
            if (lendingPrepayment != null && advanceEdiAmount > 0d) {
                lendingPrepayment.setAdvanceEdiCount(0);
                lendingPrepayment.setAdvanceEdiAmount(0D);
                lendingPrepaymentDao.save(lendingPrepayment);
            }
            loan.setDueAmount(0D);
            loan.setPaidAmount(loan.getPaidAmount() + amount + advanceEdiAmount + excessCollectionBalance);

            loan.setDueInterest(0D);
            loan.setPaidInterest((loan.getPaidInterest() != null ? loan.getPaidInterest() : 0) + paidInterestAmount);

            loan.setDuePrinciple(0D);
            loan.setPaidPrinciple((loan.getPaidPrinciple() != null ? loan.getPaidPrinciple() : 0) + paidPrincipalAmount);

            loan.setDuePenalty(0D);
            loan.setPaidPenalty((loan.getPaidPenalty() != null ? loan.getPaidPenalty() : 0) + paidPenalty);

            loan.setOtherCharges((loan.getOtherCharges() != null ? loan.getOtherCharges() : 0) + foreclosureChargesAmount);

            loan.setDueOtherCharges(0D);
            loan.setPaidOtherCharges((loan.getPaidOtherCharges() != null ? loan.getPaidOtherCharges() : 0) + foreclosureChargesAmount);
            loan.setStatus("CLOSED");
            loan.setClosingDate(new Date());
            String preclosureDescription = ((preclosureWithCharges) ? "PRECLOSER_WITH_CHARGES_UPI : " : "PRECLOSER_UPI : ") + payment.getBankRefNo();

            PaymentCalculation paymentAdjustedPositiveEntry = PaymentCalculation.builder()
                    .used(amount)
                    .principleSettled(paidPrincipalAmount-excessCollectionBalance)
                    .interestSettled(paidInterestAmount)
                    .penaltySettled(paidPenalty)
                    .chargesSettled(foreclosureChargesAmount)
                    .build();

            LendingLedger positiveEntry = ledgerAdjustmentService.adjustLendingLedger(loan, paymentAdjustedPositiveEntry,null, preclosureDescription  , payment.getSource(), payment.getTransferType(), payment.getTerminalOrderId());

            // Charges Apportionment Adjustment
            PaymentCalculation chargesAdjuest = adjustChargesService.checkAndAdjustChargesApportionment(loan, paidPenalty);
            chargesAdjuest.setPenaltySettled(paidPenalty);
            ledgerAdjustmentService.adjustPenaltyLedger(loan, chargesAdjuest, payment.getSource(), false, payment.getTerminalOrderId());

            if (surplusAmount > 0 && EDI_BY_EDI.name().equalsIgnoreCase(settlementMechanism) ) {
                log.info("Extra principle received for loanId:{} and extra amount:{}", loan.getId(), surplusAmount);
                adjustExtraAmountIfAny(loan,surplusAmount,payment,true);
            }
            if(loanForeClosureCharges != null && positiveEntry != null) {
                log.info("updating ledger id {} in loan foreclosure charges  {} ",positiveEntry.getId(),loanForeClosureCharges);
                loanForeClosureCharges.setLedgerId(positiveEntry.getId());
                loanForeClosureChargesDao.save(loanForeClosureCharges);
            }

//            if(surplusAmount > 0) adjustExtraAmountIfAny(loan, surplusAmount, payment, true);
            loanStatusService.processLoanClosure(LoanClosureDTO.builder()
                    .activeLoan(loan)
                    .lendingLedger(positiveEntry)
                    .orderId(payment.getOrderId())
                    .foreClosure(true)
                    .postCharges(postChargesToLender)
                    .chargeId(requestId)
                    .foreclosureCharges(foreclosureChargesAmount)
                    .build());
        }
    }

    private void markExcessNachAdjusted(LendingPaymentSchedule loan, List<LendingCollectionExcess> lendingCollectionExcessList) {
        log.info("Adjusting excess collection for loan in ledger : {}", loan.getId());
        ledgerAdjustmentService.createLendingLedgerForExcessCollectionOnForeclosure(loan, lendingCollectionExcessList);
        ledgerAdjustmentService.settleExcessCollectionBalance(loan.getId(), lendingCollectionExcessList);
    }


    private Integer getEDIHolidayInterestAmount(LendingPaymentSchedule lps) {
        try {
            List<LendingEDISchedule> lendingEDISchedules = lendingEDIScheduleDao.getByLoanIdAndEdiType(lps.getId(), "EDIHOLIDAY");
            if (lendingEDISchedules != null && !lendingEDISchedules.isEmpty()) {
                return lendingEDISchedules.stream().mapToInt(LendingEDISchedule::getTotalEdi).sum();
            }
        } catch (Exception ex) {
            log.error("Exception in getEDIHolidayInterestAmount for Loan ID {}, Exception is {}", lps.getId(), ex);
        }
        return 0;
    }

    public int getForeclosureAmount(LendingPaymentSchedule lendingPaymentSchedule) {
        log.info("Fetching Foreclosure amount for loanId: {}", lendingPaymentSchedule.getId());
        if (lendingPaymentSchedule == null || lendingPaymentSchedule.getStatus().equals("CLOSED")) {
            return 0;
        }
        LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
        log.info("LendingPrepayment: {} for loan: {}", lendingPrepayment, lendingPaymentSchedule.getId());
        double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
        log.info("Advance EDI Amount: {} for loan: {}", advanceEdiAmount, lendingPaymentSchedule.getId());

        Double excessCollectionBalance = excessNachService.getExcessCollectionBalanceAmount(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
        log.info("Excess Collection Balance: {} for loan: {}", excessCollectionBalance, lendingPaymentSchedule.getId());

        Double extraInterestofPerpetualDpdLoan = loanUtil.fetchExtraEdiInterestCollectionForPerpetualDpdLoan(lendingPaymentSchedule.getId());
        log.info("Extra Interest of Perpetual DPD Loan: {} for loan: {}", extraInterestofPerpetualDpdLoan, lendingPaymentSchedule.getId());

        log.info("Calculating Foreclosure amount for loanId: {}", lendingPaymentSchedule.getId());
        return (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() + (Objects.nonNull(lendingPaymentSchedule.getDuePenalty()) ? lendingPaymentSchedule.getDuePenalty() : 0)
                - (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0)
                + (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0)
                + (lendingPaymentSchedule.getDueOtherCharges() != null ? lendingPaymentSchedule.getDueOtherCharges() : 0)
                - advanceEdiAmount - excessCollectionBalance - extraInterestofPerpetualDpdLoan);
    }

    private void raiseDuesInLedger(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment) {
        double amount = 0;
        if (Objects.nonNull(payment.getAdvanceEdiAmount()) && payment.getAdvanceEdiAmount() > 0) {
            amount = payment.getAdvanceEdiAmount();
        } else if (Objects.nonNull(payment.getOtherAmount()) && payment.getOtherAmount() > 0) {
            amount = payment.getOtherAmount();
        }
        if (amount <= 0) {
            return;
        }

        double dueAmount = loan.getDueAmount();
        double duePrinciple = loan.getDuePrinciple();

        double raiseDueAmount = amount - dueAmount;
        double raiseDuePrinciple = amount - duePrinciple;

        int ediSkipCount = (int) (raiseDueAmount/loan.getEdiAmount());
        int ediRemainingCount = loan.getEdiRemainingCount();
        if (ediRemainingCount > 0 && ediRemainingCount < ediSkipCount) {
            ediSkipCount = ediRemainingCount;
        }
//        LPS Changes to raise advance EDI
        if (ediSkipCount >= 1) {
            Date nextEdiDate = loan.getNextEdiDate();
            LocalDate localDate = nextEdiDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

            loan.setNextEdiDate(Date.from(localDate.plusDays(ediSkipCount).atStartOfDay(ZoneId.systemDefault()).toInstant()) );
            loan.setEdiRemainingCount(ediRemainingCount - ediSkipCount);
        }

        loan.setDueAmount(amount);
        loan.setDuePrinciple(duePrinciple + raiseDuePrinciple);

//        Ledger Advance EDI
        LendingLedger lendingLedger = getLendingLedger(loan, raiseDueAmount, raiseDuePrinciple);
        lendingLedgerDao.save(lendingLedger);
    }

    private LendingLedger getLendingLedger(LendingPaymentSchedule loan, double raiseDueAmount, double raiseDuePrinciple) {
        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(loan.getMerchantId());
        lendingLedger.setLendingPaymentSchedule(loan);
        lendingLedger.setAmount(-1 * raiseDueAmount);
        lendingLedger.setPrinciple(-1 * raiseDuePrinciple);
        lendingLedger.setInterest(0.0);
        lendingLedger.setPenalty(0.0);
        lendingLedger.setOtherCharges(0.0);
        lendingLedger.setDescription(ADVANCE_EDI);
        lendingLedger.setTxnType("EDI");
        lendingLedger.setDate(new Date());
        return lendingLedger;
    }

    private void createLoanFundInEntry(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment) {
        log.info("Creating Lending Payin Details for loan: {}, payment: {}", loan.getId(), payment);
        try {
            if (payment != null && LoanPaymentUtil.isExcessAdjustmentEntry(payment.getTerminalOrderId())) {
                log.info("Skipping creation of Lending Payin Details for excess adjustment entry for loan: {}, payment: {}", loan.getId(), payment);
                return;
            }

            if (payment != null && "BHARATPE_NACH".equalsIgnoreCase(payment.getSource())) {
                log.info("Skipping creation of Lending Payin Details for NACH payment for loan: {}, payment: {}", loan.getId(), payment);
                return;
            }

            LendingPayinDetails lendingPayinDetails = LendingPayinDetails.builder()
                    .loanId(loan.getId())
                    .merchantId(loan.getMerchantId())
                    .nbfc(loan.getNbfc())
                    .source(payment.getSource())
                    .terminalOrderId(payment.getTerminalOrderId())
                    .totalAmount(payment.getOtherAmount())
                    .transferType(payment.getTransferType())
                    .build();

            LendingPayinDetails savedlendingPayinDetails = lendingPayinDetailsDao.save(lendingPayinDetails);
            log.info("Created Lending Payin Details: {} for loan: {}, payment: {}", savedlendingPayinDetails, loan.getId(), payment);

            if(lcaPayinRecordEligibleLenders.contains(loan.getNbfc())) {
                log.info("Creating Lending Collection Audit for Payin for loan: {}, payment: {}", loan.getId(), payment);
                createLendingCollectionAuditForPayin(loan, savedlendingPayinDetails, payment.getOtherAmount(), payment.getTerminalOrderId(), payment.getSource(), payment.getTransferType());
            }
        } catch (Exception e) {
            log.error("Exception in creating lending payin details for loan: {}, payment: {}, exception: {}: {}", loan.getId(), payment, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public void createLendingCollectionAuditForPayin(LendingPaymentSchedule lendingPaymentSchedule, LendingPayinDetails lendingPayinDetails, Double orderAmount, String txnId, String mode, String transferType) {
        log.info("Creating Lending Collection Audit for Payin for loanId: {}, payinDetailsId: {}, amount: {}, txnId: {}, mode: {}, transferType: {}",
                lendingPaymentSchedule.getId(), lendingPayinDetails.getId(), orderAmount, txnId, mode, transferType);

        Date ledgerDate = new Date();

        LendingCollectionAudit lendingCollectionAudit = LendingCollectionAudit.builder()
                .merchantId(lendingPaymentSchedule.getMerchantId())
                .merchantStoreId(lendingPaymentSchedule.getMerchantStoreId())
                .loanId(lendingPaymentSchedule.getId())
                .applicationId(lendingPaymentSchedule.getLoanApplication().getId())
                .bpLoanId(lendingPaymentSchedule.getLoanApplication().getExternalLoanId())
                .nbfcId(lendingPaymentSchedule.getLoanApplication().getNbfcId())
                .txnType("PAYIN")
                .transferType(transferType == null ? CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name() : transferType)
                .status("PENDING")
                .amount(orderAmount)
                .otherCharges(0D)
                .penalty(0D)
                .adjustmentMode(mode)
                .transferDate(ledgerDate)  // todo: check for other mode
                .terminalOrderId(txnId)
                .lender(lendingPaymentSchedule.getNbfc())
                .loanStatus(lendingPaymentSchedule.getStatus())
                .loanClosingDate(lendingPaymentSchedule.getClosingDate())
                .mobile(lendingPaymentSchedule.getMobile())
                .ledgerId(lendingPayinDetails.getId())
                .build();
        lendingCollectionAuditDao.save(lendingCollectionAudit);
        log.info("Created Lending Collection Audit for Payin: {} for loanId: {}, payinDetailsId: {}", lendingCollectionAudit, lendingPaymentSchedule.getId(), lendingPayinDetails.getId());
    }
}
