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
import com.bharatpe.lending.common.enums.CollectionTransferTypeEnum;
import com.bharatpe.lending.common.enums.LoanPaymentMode;
import com.bharatpe.lending.common.enums.LoanSettlementMechanism;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.WaiverType;
import com.bharatpe.lending.loanV2.service.ExcessNachService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.bharatpe.lending.common.enums.LoanPaymentMode.*;
import static com.bharatpe.lending.common.enums.LoanSettlementMechanism.*;
import static com.bharatpe.lending.common.enums.TransferTypeModes.DIRECT_TRANSFER_LENDER;
import static com.bharatpe.lending.service.PaymentService.UPI_AUTO_PAY;


@Service
@Slf4j
public class LoanPaymentServiceImpl implements LoanPaymentService {

    public static final String LOAN_PAYMENT_ORDER_ID_PREFIX = "LOAN";

    //Allowed -  Arrays.asList(NACH, ADVANCE, OTHER)
    public static final List<LoanPaymentMode> PAYMENT_ADJUSTMENT_PREFRENCE_LIST = Arrays.asList(NACH, OTHER);

    public static final String LOAN_PAYMENT_ORDER_SOURCE_EXCESS_NACH = "EXCESS_NACH";

    public static final String EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX = "_adjust_";

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

    @Override
    @Transactional
    public LendingPaymentSchedule adjustMoney(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment) {
        log.info("adjustMoney for loan: {} and payment {} started ", loan, payment);

        if (Objects.isNull(loan) || Objects.isNull(payment)) return loan;

        List<String> waiverList = Arrays.asList(WaiverType.EXCEPTION.name(), WaiverType.DECEASED_SCHEME.name(), WaiverType.SCHEME1.name(), WaiverType.SCHEME.name());
        if (Objects.nonNull(payment.getSource()) && waiverList.contains(payment.getSource()) &&
                (loan.getNbfc().equalsIgnoreCase(Lender.ABFL.name()) || loan.getNbfc().equalsIgnoreCase(Lender.PIRAMAL.name()))) {
            List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(loan.getMerchantId(), loan.getId(), "ACTIVE");
            Double excessCollectionBalance = 0D;
            for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
                if(lendingCollectionExcess.getAmount() > 0){
                    excessCollectionBalance += lendingCollectionExcess.getAmount();
                }
            }
            loanStatusService.waiverSettlement(loan, payment.getOtherAmount(), payment.getBankRefNo(), payment.getSource(), "SETTLED", payment.getTerminalOrderId(), excessCollectionBalance, lendingCollectionExcessList);
            return loan;
        }
        String mechanism = LoanPaymentUtil.getLoanSettlementMechanism(loan);
        adjustMoney(loan, payment, mechanism);
        log.info("adjustMoney for loan: {} and payment {} complete", loan, payment);
        return loan;
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
        if (!CollectionUtils.isEmpty(loanPaymentModes) && settlementMechanism != null) {
            boolean loanForeClosed = checkForLoanForeClosure(loan, payment, settlementMechanism.name());
            if (!loanForeClosed) {
                for (LoanPaymentMode paymentMode : loanPaymentModes) {
                    log.info("adjustMoney for loanId: {} paymentMode {} by mechanism {} ", loan.getId(), paymentMode, settlementMechanism.name());
                    if (NACH.equals(paymentMode) && payment.isAdjustExcessNach())
                        adjustExcessNachBalanceAndLedger(loan, settlementMechanism.name());
                    if (ADVANCE.equals(paymentMode))
                        adjustAdvancePaymentAndLedger(loan, payment.getAdvanceEdiAmount(), settlementMechanism.name());
                    if (OTHER.equals(paymentMode))
                        adjustOtherPaymentAndLedger(loan, payment, settlementMechanism.name());
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
        ledgerAdjustmentService.adjustLendingLedger(loan, otherAdjustment, order, payment.getDescription(), payment.getSource(), payment.getTransferType(), payment.getTerminalOrderId());
        ledgerAdjustmentService.adjustPenaltyLedger(loan, otherAdjustment.getPenaltySettled(), payment.getSource(), false);
        if (payment.isUpdateGlobalTxnlimit()) updateGlobaltxnLimit(loan.getMerchantId(), "CREDIT", otherAdjustment.getPrincipleSettled());
    }

    private void updateGlobaltxnLimit(Long merchantId, String mode, double amount) {
        log.info("amount:{} in lending global limit for merchant:{} type :{}", amount, merchantId, mode);
        if(StringUtils.hasLength(mode) && amount > 0) {
            notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(merchantId, mode, amount));
        }
    }

    private void adjustExtraAmountIfAny(LendingPaymentSchedule loan, double balance, LoanPaymentDetailDTO payment, boolean refund) {
        if (balance > 0) {
            refund = refund || "CLOSED".equalsIgnoreCase(loan.getStatus());
            if (refund) {
                createLoanRefund(loan, balance, payment);
            } else {
                createLoanExcess(loan, balance, payment);
            }
        }
    }

    private void createLoanExcess(LendingPaymentSchedule loan, double amount, LoanPaymentDetailDTO payment) {
        log.info("Creating Excess Nach Ledger for merchant:{}", loan.getMerchantId());
        LendingCollectionExcess lendingCollectionExcess = new LendingCollectionExcess();
        lendingCollectionExcess.setLoanId(loan.getId());
        lendingCollectionExcess.setMerchantId(loan.getMerchantId());
        lendingCollectionExcess.setStatus("ACTIVE");
        lendingCollectionExcess.setAmount(amount);
        lendingCollectionExcess.setExcessNachCreditAmount(amount);
        lendingCollectionExcess.setTerminalOrderId(payment.getTerminalOrderId());
        lendingCollectionExcess.setDeductionCount(0);
        lendingCollectionExcess.setCreditDate(new Date());
        lendingCollectionExcess.setDeductedAmount(0D);
        lendingCollectionExcessDao.save(lendingCollectionExcess);
        log.info("Creating Excess Nach Collection credit entry of amount:{} for merchant:{}", amount, loan.getMerchantId());
        sendExcessNachCollectionSMS(loan.getMerchantId(), loan.getId());
        createLendingCollectionAuditForExcessNachCredit(loan, payment.getTerminalOrderId(), amount, lendingCollectionExcess.getId());
    }

    private void createLendingCollectionAuditForExcessNachCredit(LendingPaymentSchedule lendingPaymentSchedule, String txnId, Double amount, Long refId){
        LendingCollectionAudit lendingCollectionAudit = LendingCollectionAudit.builder()
                .merchantId(lendingPaymentSchedule.getMerchantId())
                .merchantStoreId(lendingPaymentSchedule.getMerchantStoreId())
                .loanId(lendingPaymentSchedule.getId())
                .applicationId(lendingPaymentSchedule.getLoanApplication().getId())
                .bpLoanId(lendingPaymentSchedule.getLoanApplication().getExternalLoanId())
                .nbfcId(lendingPaymentSchedule.getLoanApplication().getNbfcId())
                .txnType("EXCESS_NACH_CREDIT")
                .transferType(DIRECT_TRANSFER_LENDER.name())
                .status("PENDING")
                .amount(amount)
                .otherCharges(0D)
                .penalty(0D)
                .adjustmentMode("EXCESS_NACH_CREDIT")
                .transferDate(DateTimeUtil.getCurrentDayStartTime())
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
        lendingRefundAudit.setOrderAmount(0d);
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
            String source = LOAN_PAYMENT_ORDER_SOURCE_EXCESS_NACH + lendingCollectionExcess.getId();
            String orderId = LOAN_PAYMENT_ORDER_ID_PREFIX + loan.getId() + System.currentTimeMillis();
            LoanPaymentOrder order = ledgerAdjustmentService.createLoanPaymentOrder(loan, deductionAmount, lendingCollectionExcess.getTerminalOrderId(), CreditConstants.PaymentStatus.PENDING.name(), source, orderId);

            PaymentCalculation nachAdjustment = adjustPayment(loan, lendingCollectionExcess.getAmount(), mode);
            String status = (deductionAmount == nachAdjustment.getUsed()) ? "OK" : "MISMATCH";
            log.info("adjustExcessNachBalanceAndLedger :  order vs ledger status: {} order: {} and nachAdjustment: {}", status, order, nachAdjustment);

            ledgerAdjustmentService.adjustNachLedger(lendingCollectionExcess, nachAdjustment);
            String terminalOrderId = lendingCollectionExcess.getTerminalOrderId() + EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX + lendingCollectionExcess.getDeductionCount().toString();
            ledgerAdjustmentService.adjustLendingLedger(loan, nachAdjustment, order, terminalOrderId, "EXCESS_NACH_ADJUSTED", CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name(), terminalOrderId);
            ledgerAdjustmentService.adjustPenaltyLedger(loan, nachAdjustment.getPenaltySettled(), source, false);
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
        Integer principalDueAmount = getForeclosureAmount(loan);
        Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(loan);
        double amount = payment.getOtherAmount();
        if (principalDueAmount + ediHolidayInterestAmount - amount <= 1D) {
            foreCloseLoan(loan, payment, settlementMechanism, principalDueAmount, ediHolidayInterestAmount);
            return "CLOSED".equalsIgnoreCase(loan.getStatus());
        }
        return false;
    }

    private void foreCloseLoan(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment, String settlementMechanism, Integer principalDueAmount, Integer ediHolidayInterestAmount) {
        double amount = payment.getOtherAmount();
        if (principalDueAmount + ediHolidayInterestAmount - amount <= 1D) {  //foreClosure
            LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(loan.getMerchantId(), loan.getId());
            double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
            double excessCollectionBalance = 0;
            List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(loan.getMerchantId(), loan.getId(), "ACTIVE");
            lendingCollectionExcessList.sort((l1, l2) -> {
                if (UPI_AUTO_PAY.equalsIgnoreCase(l1.getMode())) {
                    return UPI_AUTO_PAY.equalsIgnoreCase(l2.getMode()) ? Long.compare(l1.getId(), l2.getId()) : 1;
                } else {
                    return UPI_AUTO_PAY.equalsIgnoreCase(l2.getMode()) ? -1 : Long.compare(l1.getId(), l2.getId());
                }
            });
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
            ledgerAdjustmentService.adjustPenaltyLedger(loan, paidPenalty, payment.getSource(), false);

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
        if (lendingPaymentSchedule == null || lendingPaymentSchedule.getStatus().equals("CLOSED")) {
            return 0;
        }
        LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
        double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;

        Double excessCollectionBalance = excessNachService.getExcessCollectionBalanceAmount(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());

        return (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() + (Objects.nonNull(lendingPaymentSchedule.getDuePenalty()) ? lendingPaymentSchedule.getDuePenalty() : 0)
                - (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0)
                + (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0)
                + (lendingPaymentSchedule.getDueOtherCharges() != null ? lendingPaymentSchedule.getDueOtherCharges() : 0)
                - advanceEdiAmount - excessCollectionBalance);
    }
}
