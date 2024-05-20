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
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.dao.LendingPrepaymentDao;
import com.bharatpe.lending.common.dao.LoanForeClosureChargesDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.entity.LendingPrepayment;
import com.bharatpe.lending.common.entity.LoanForeClosureCharges;
import com.bharatpe.lending.common.enums.CollectionTransferTypeEnum;
import com.bharatpe.lending.common.enums.LoanPaymentMode;
import com.bharatpe.lending.common.enums.LoanSettlementMechanism;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.enums.WaiverType;
import com.bharatpe.lending.loanV2.service.ExcessNachService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.bharatpe.lending.common.enums.LoanPaymentMode.*;
import static com.bharatpe.lending.common.enums.LoanSettlementMechanism.*;


@Service
@Slf4j
public class LoanPaymentServiceImpl implements LoanPaymentService {


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

    @Override
    public LendingPaymentSchedule adjustMoney(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment) {
        log.info("adjustMoney for loan: {} and payment {} started ", loan, payment);

        if( Objects.nonNull(payment) && Objects.nonNull(payment.getSource()) && WaiverType.SCHEME1.name().equals(payment.getSource())){
            log.info("Waiver Settlement V2: active Loan: {}, amount: {}, bankRefNo: {}, source: {}, transferType: {}, terminal Order Id: {}, orderId: {}", loan, payment.getOtherAmount(), payment.getBankRefNumber(), payment.getSource(), payment.getTransferType(), payment.getTerminalOrderId(), payment.getOrderId());

            loanStatusService.waiverSettleLoan(loan, payment.getOtherAmount(), payment.getBankRefNumber(), payment.getSource(), payment.getTerminalOrderId());
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
                    if (NACH.equals(paymentMode))
                        adjustNachPaymentAndLedger(loan, payment.isAdjustNach(), settlementMechanism.name());
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
        LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(String.valueOf(payment.getOrderId()));
        ledgerAdjustmentService.adjustLendingLedger(loan, otherAdjustment, order, payment.getRemark(), payment.getOwner(), payment.getTransferType(), payment.getTerminalOrderId());
    }

    // TODO : not usable  - fix it
    private void adjustAdvancePaymentAndLedger(LendingPaymentSchedule loan, double advanceEdiAmount, String mode) {
        PaymentCalculation advanceAdjustment = adjustPayment(loan, advanceEdiAmount, mode);
        LoanPaymentOrder order = ledgerAdjustmentService.createLoanPaymentOrder(loan, advanceAdjustment.getUsed(), null, CreditConstants.PaymentStatus.PENDING.name(), "ADVANCE_EDI");
        ledgerAdjustmentService.adjustAdvanceEdiLedger(loan, advanceAdjustment);
        //todo:ask how to adjust in ledger
        ledgerAdjustmentService.adjustLendingLedger(loan, advanceAdjustment, order, null, null, null, null);
    }

    private void adjustNachPaymentAndLedger(LendingPaymentSchedule loan, boolean adjustNach, String mode) {
        log.info("adjustNach : checking eligibility nach for loanId :{} and nach settlement required :{} and mode : {} ", loan.getId(), adjustNach, mode);
        if (adjustNach) {
            List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(loan.getMerchantId(), loan.getId(), "ACTIVE");
            for (LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList) {
                log.info("adjustNachWithIPC : processing excess nach for loanId :{} and NachId :{}", loan.getId(), lendingCollectionExcess.getId());
                double penaltyFee = Objects.nonNull(loan.getDuePenalty()) ? loan.getDuePenalty() : 0d;
                Double deductionAmount = Math.min(lendingCollectionExcess.getAmount(), (loan.getDueAmount() + penaltyFee));
                if (deductionAmount < 1D) continue;
                if (Objects.isNull(lendingCollectionExcess.getTerminalOrderId())) continue;

                //Creating loan payment order for deduction from excess nach credit
                String source = LOAN_PAYMENT_ORDER_SOURCE_EXCESS_NACH + lendingCollectionExcess.getId();
                LoanPaymentOrder order = ledgerAdjustmentService.createLoanPaymentOrder(loan, deductionAmount, lendingCollectionExcess.getTerminalOrderId(), CreditConstants.PaymentStatus.PENDING.name(), source);

                PaymentCalculation nachAdjustment = adjustPayment(loan, lendingCollectionExcess.getAmount(), mode);
                String status = (deductionAmount == nachAdjustment.getUsed()) ? "OK" : "MISMATCH";
                log.info("adjustNachWithIPC :  order vs ledger status: {} order: {} and nachAdjustment: {}", status, order, nachAdjustment);

                ledgerAdjustmentService.adjustNachLedger(lendingCollectionExcess, nachAdjustment);
                String terminalOrderId = lendingCollectionExcess.getTerminalOrderId() + EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX + lendingCollectionExcess.getDeductionCount().toString();
                ledgerAdjustmentService.adjustLendingLedger(loan, nachAdjustment, order, terminalOrderId, "EXCESS_NACH_ADJUSTED", CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name(), terminalOrderId);
            }
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
            double remainingBalance = (loan.getPaidPrinciple() + paidPrincipalAmount) - loan.getLoanAmount();
            if (remainingBalance > 0) {
                log.info("Extra principle received for loanId:{} and extra amount:{}", loan.getId(), remainingBalance);
                paidPrincipalAmount -= remainingBalance;
                paidInterestAmount += remainingBalance;
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
            PaymentCalculation paymentAdjusted = PaymentCalculation.builder()
                    .used(-1 * Math.abs(amount - loan.getDueAmount() + advanceEdiAmount + excessCollectionBalance))
                    .principleSettled(-1 * Math.abs(amount - loan.getDueAmount() - ediHolidayInterestAmount + advanceEdiAmount + excessCollectionBalance - paidPenalty - foreclosureChargesAmount))
                    .interestSettled(Double.valueOf(ediHolidayInterestAmount))
                    .penaltySettled(-1 * paidPenalty)
                    .chargesSettled(-1 * foreclosureChargesAmount)
                    .build();
            ledgerAdjustmentService.createLendingLedger(loan, paymentAdjusted, description, payment.getOwner(), payment.getTransferType(), payment.getTerminalOrderId());
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

            loan.setDueOtherCharges(0D);
            loan.setOtherCharges((loan.getOtherCharges() != null ? loan.getOtherCharges() : 0) + foreclosureChargesAmount);
            loan.setPaidOtherCharges((loan.getPaidOtherCharges() != null ? loan.getPaidOtherCharges() : 0) + foreclosureChargesAmount);
            loan.setStatus("CLOSED");
            loan.setClosingDate(new Date());
            // todo: add positive ledger and foreclosure
            LendingLedger positiveEntry = ledgerAdjustmentService.createLendingLedger(loan, paymentAdjusted, description, payment.getOwner(), payment.getTransferType(), payment.getTerminalOrderId());
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
