package com.bharatpe.lending.service;


import com.bharatpe.common.dao.LendingEDIScheduleDao;
import com.bharatpe.common.entities.LendingEDISchedule;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.dao.LendingPrepaymentDao;
import com.bharatpe.lending.common.dao.LoanForeClosureChargesDao;
import com.bharatpe.lending.common.dto.SettleLoanPaymentDTO;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.entity.LendingPrepayment;
import com.bharatpe.lending.common.entity.LoanForeClosureCharges;
import com.bharatpe.lending.common.enums.CollectionTransferTypeEnum;
import com.bharatpe.lending.common.enums.LoanPaymentMode;
import com.bharatpe.lending.common.enums.LoanSettlementMechanism;
import com.bharatpe.lending.common.service.PaymentSettlementService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.dto.LoanPaymentDetailDto;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.loanV2.service.ExcessNachService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
;import java.util.*;

import static com.bharatpe.lending.common.enums.LoanPaymentMode.*;
import static com.bharatpe.lending.common.enums.LoanSettlementMechanism.*;

@Service
@Slf4j
public class LoanPaymentService {

    public static final String DEFAULT_LOAN_SETTLEMENT_MECHANISM = IPC.name();

    //Allowed -  Arrays.asList(NACH, ADVANCE, OTHER)
    public static final List<LoanPaymentMode> PAYMENT_ADJUSTMENT_PREFRENCE_LIST = Arrays.asList(NACH, OTHER);

    public static final String LOAN_PAYMENT_ORDER_ID_PREFIX = "LOAN";

    public static final String LOAN_PAYMENT_ORDER_OWNER = "LOAN";
    public static final String LOAN_PAYMENT_ORDER_SOURCE_EXCESS_NACH = "EXCESS_NACH";

    public static final String EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX = "_adjust_";
    Logger logger = LoggerFactory.getLogger(LoanPaymentService.class);

    @Autowired
    LendingPrepaymentDao lendingPrepaymentDao;

    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;

    @Autowired
    LoanPaymentOrderDao loanPaymentOrderDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingCollectionAuditService lendingCollectionAuditService;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    PaymentSettlementService paymentSettlementService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingEDIScheduleDao lendingEDIScheduleDao;

    @Autowired
    ExcessNachService excessNachService;

    @Autowired
    LoanForeClosureChargesDao loanForeClosureChargesDao;

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class PaymentCalculation {
        double received;
        double used;
        double balance;
        double principleSettled;
        double interestSettled;
        double penaltySettled;
        double chargesSettled;
    }


    LendingPaymentSchedule adjustMoney(LendingPaymentSchedule loan, LoanPaymentDetailDto payment) {
        logger.info("adjustMoney for loan: {} and payment {} started ", loan, payment);
        String mechanism  = getLoanSettlementMechanism(loan);
        adjustMoney(loan, payment, mechanism);
        logger.info("adjustMoney for loan: {} and payment {} complete", loan, payment);
        return loan;
    }

    private LendingPaymentSchedule adjustMoney(LendingPaymentSchedule loan, LoanPaymentDetailDto payment, String settlementMechanism) {
        if (StringUtils.hasLength(settlementMechanism)) {
            if (IPC.name().equalsIgnoreCase(settlementMechanism)) return adjustMoney(loan, payment, PAYMENT_ADJUSTMENT_PREFRENCE_LIST, IPC);
            if (EDI_BY_EDI.name().equalsIgnoreCase(settlementMechanism)) return adjustMoney(loan, payment, PAYMENT_ADJUSTMENT_PREFRENCE_LIST, EDI_BY_EDI);
            if (NPA.name().equalsIgnoreCase(settlementMechanism)) return adjustMoney(loan, payment, PAYMENT_ADJUSTMENT_PREFRENCE_LIST, NPA);
        }
        return loan;
    }
    private LendingPaymentSchedule adjustMoney(LendingPaymentSchedule loan, LoanPaymentDetailDto payment, List<LoanPaymentMode> loanPaymentModes, LoanSettlementMechanism settlementMechanism) {
        if (!CollectionUtils.isEmpty(loanPaymentModes) && settlementMechanism != null) {
            if (payment.isForeCloser()) {
                foreCloseLoan(loan, payment, settlementMechanism.name());
            } else {
                for (LoanPaymentMode paymentMode : loanPaymentModes) {
                    logger.info("adjustMoney for loanId: {} paymentMode {} by mechanism {} ", loan.getId(), paymentMode, settlementMechanism.name());
                    if (NACH.equals(paymentMode)) adjustNachPaymentAndLedger(loan, payment.isAdjustNach(), settlementMechanism.name());
                    if (ADVANCE.equals(paymentMode)) adjustAdvancePaymentAndLedger(loan, payment.getAdvanceEdiAmount(), settlementMechanism.name());
                    if (OTHER.equals(paymentMode)) adjustOtherPaymentAndLedger(loan, payment, settlementMechanism.name());
                }
            }
            lendingPaymentScheduleDao.save(loan);
        }
        return loan;
    }

    LendingPaymentSchedule adjustMoneyWithNachAndPrepayment(LendingPaymentSchedule loan, LoanPaymentDetailDto payment) {
        payment.setAdjustNach(true);

        logger.info("adjustMoneyWithNachAndPrepayment for loanId: {} checking prepayment balance ", loan.getId());
        LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(loan.getMerchantId(), loan.getId());
        logger.info("adjustMoneyWithNachAndPrepayment for loanId: {} found prepayment balance :{}", loan.getId(), lendingPrepayment);
        double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0;
        payment.setAdvanceEdiAmount(advanceEdiAmount);

        return adjustMoney(loan, payment);
    }

    //==================================================================================================================
    // _______________________________________      IPC   ______________________________________________________________
    //==================================================================================================================
    private PaymentCalculation adjustIPC(LendingPaymentSchedule activeLoan, double amount) {
        double balance=amount;
        PaymentCalculation interest = adjustInterest(activeLoan, balance);
        balance = interest.getBalance();

        PaymentCalculation principle = adjustPrinciple(activeLoan, balance);
        balance = principle.getBalance();

        PaymentCalculation penalty = adjustPenalty(activeLoan, balance);
        balance = penalty.getBalance();

        PaymentCalculation charges = adjustOtherCharges(activeLoan, balance);
        balance = charges.getBalance();

        return  PaymentCalculation.builder()
                .received(amount)
                .used(amount - balance)
                .balance(balance)
                .principleSettled(principle.getUsed())
                .interestSettled(interest.getUsed())
                .penaltySettled(penalty.getUsed())
                .chargesSettled(charges.getUsed())
                .build();
    }

    //==================================================================================================================
    // _______________________________________      NPA   ______________________________________________________________
    //==================================================================================================================
    private PaymentCalculation adjustNPA(LendingPaymentSchedule loan, double amount) {
        return adjustEdiSchedule(loan, amount, true);
    }

    //==================================================================================================================
    // __________________________________     EDI BY EDI   _____________________________________________________________
    //==================================================================================================================
    private PaymentCalculation adjustEDIBYEDI(LendingPaymentSchedule loan, double amount) {
        return adjustEdiSchedule(loan, amount, loan.getSettleAllPrinciple());
    }
    private PaymentCalculation adjustEdiSchedule(LendingPaymentSchedule loan, double amount, boolean adjustPrincipleFirst) {
        SettleLoanPaymentDTO settleLoanPaymentDTO = settleEDIPrincipleAndInterest(loan, amount, adjustPrincipleFirst);

        PaymentCalculation penalty = adjustPenalty(loan, settleLoanPaymentDTO.getRemainingBalance());
        settleLoanPaymentDTO.setRemainingBalance(settleLoanPaymentDTO.getRemainingBalance() - penalty.getUsed());

        PaymentCalculation charges = adjustOtherCharges(loan, settleLoanPaymentDTO.getRemainingBalance());
        settleLoanPaymentDTO.setRemainingBalance(settleLoanPaymentDTO.getRemainingBalance() - charges.getUsed());

        return  PaymentCalculation.builder()
                .received(amount)
                .used(amount - settleLoanPaymentDTO.getRemainingBalance())
                .balance(settleLoanPaymentDTO.getRemainingBalance())
                .principleSettled(settleLoanPaymentDTO.getPaidPrinciple())
                .interestSettled(settleLoanPaymentDTO.getPaidInterest())
                .penaltySettled(penalty.getUsed())
                .chargesSettled(charges.getUsed())
                .build();
    }

    private SettleLoanPaymentDTO settleEDIPrincipleAndInterest(LendingPaymentSchedule loan, double amount, Boolean settleAllPrinciple) {
        SettleLoanPaymentDTO settleLoanPaymentDTO = paymentSettlementService.settleLoanPayment(loan.getId(), loan.getEdiCount(), loan.getEdiRemainingCount(), settleAllPrinciple, amount);
        double paidPrincipalAmount = settleLoanPaymentDTO.getPaidPrinciple();
        double paidInterestAmount = settleLoanPaymentDTO.getPaidInterest();

        loan.setDuePrinciple(loan.getDuePrinciple() - paidPrincipalAmount);
        loan.setPaidPrinciple((loan.getPaidPrinciple() != null ? loan.getPaidPrinciple() : 0) + paidPrincipalAmount);

        loan.setDueInterest(loan.getDueInterest() - paidInterestAmount);
        loan.setPaidInterest((loan.getPaidInterest() != null ? loan.getPaidInterest() : 0) + paidInterestAmount);

        loan.setDueAmount(loan.getDueAmount() - (paidPrincipalAmount + paidInterestAmount));
        loan.setPaidAmount(loan.getPaidAmount() + paidPrincipalAmount + paidInterestAmount);

        return settleLoanPaymentDTO;
    }


    //==================================================================================================================
    // __________________________________     Payment Adjustment    ____________________________________________________
    //==================================================================================================================

    private void adjustOtherPaymentAndLedger(LendingPaymentSchedule loan, LoanPaymentDetailDto payment, String mode) {
        PaymentCalculation otherAdjustment = adjustPayment(loan, payment.getOtherAmount(), mode);
        LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(String.valueOf(payment.getOrderId()));
        adjustLendingLedger(loan, otherAdjustment, order, payment.getRemark(), payment.getOwner(), payment.getTransferType(), payment.getTerminalOrderId());
    }

    // TODO : not usable  - fix it
    private void adjustAdvancePaymentAndLedger(LendingPaymentSchedule loan, double advanceEdiAmount, String mode) {
        PaymentCalculation advanceAdjustment = adjustPayment(loan, advanceEdiAmount, mode);
        LoanPaymentOrder order = createLoanPaymentOrder(loan, advanceAdjustment.getUsed(), null, CreditConstants.PaymentStatus.PENDING.name(), "ADVANCE_EDI");
        adjustAdvanceEdiLedger(loan, advanceAdjustment);
        //todo:ask how to adjust in ledger
        adjustLendingLedger(loan, advanceAdjustment, order, null, null, null, null);
    }

    private void adjustNachPaymentAndLedger(LendingPaymentSchedule loan, boolean adjustNach, String mode) {
        log.info("adjustNach : checking eligibility nach for loanId :{} and nach settlement required :{} and mode : {} ", loan.getId(), adjustNach, mode);
        if (adjustNach) {
            List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(loan.getMerchantId(), loan.getId(), "ACTIVE");
            for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList) {
                log.info("adjustNachWithIPC : processing excess nach for loanId :{} and NachId :{}", loan.getId(), lendingCollectionExcess.getId());
                double penaltyFee = Objects.nonNull(loan.getDuePenalty()) ? loan.getDuePenalty() : 0d;
                Double deductionAmount = Math.min(lendingCollectionExcess.getAmount(), (loan.getDueAmount() + penaltyFee));
                if(deductionAmount < 1D) continue;
                if(Objects.isNull(lendingCollectionExcess.getTerminalOrderId())) continue;

                //Creating loan payment order for deduction from excess nach credit
                String source =  LOAN_PAYMENT_ORDER_SOURCE_EXCESS_NACH + lendingCollectionExcess.getId();
                LoanPaymentOrder order = createLoanPaymentOrder(loan, deductionAmount, lendingCollectionExcess.getTerminalOrderId(), CreditConstants.PaymentStatus.PENDING.name(), source);

                PaymentCalculation nachAdjustment = adjustPayment(loan, lendingCollectionExcess.getAmount(), mode);
                String status = (deductionAmount == nachAdjustment.getUsed()) ? "OK" : "MISMATCH";
                log.info("adjustNachWithIPC :  order vs ledger status: {} order: {} and nachAdjustment: {}", status, order, nachAdjustment);

                adjustNachLedger(lendingCollectionExcess, nachAdjustment);
                String terminalOrderId = lendingCollectionExcess.getTerminalOrderId() + EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX + lendingCollectionExcess.getDeductionCount().toString();
                adjustLendingLedger(loan, nachAdjustment, order, terminalOrderId,"EXCESS_NACH_ADJUSTED", CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name(), terminalOrderId);
            }
        }
    }

    private PaymentCalculation adjustPayment(LendingPaymentSchedule loan, double amount, String mode) {
        if (EDI_BY_EDI.name().equalsIgnoreCase(mode)) return adjustEDIBYEDI(loan, amount);
        if (NPA.name().equalsIgnoreCase(mode)) return adjustNPA(loan, amount);
        return adjustIPC(loan, amount);
    }

    private void foreCloseLoan(LendingPaymentSchedule loan, LoanPaymentDetailDto payment, String settlementMechanism) {
        Integer principalDueAmount = loanUtil.getForeclosureAmount(loan);
        Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(loan);
        double amount = payment.getOtherAmount();
        if(principalDueAmount + ediHolidayInterestAmount - amount <= 1D) { //foreClosure
            LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(loan.getMerchantId(), loan.getId());
            double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
            double excessCollectionBalance = 0;
            List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(loan.getMerchantId(), loan.getId(), "ACTIVE");
            for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
                if(lendingCollectionExcess.getAmount() > 0){
                    excessCollectionBalance += lendingCollectionExcess.getAmount();
                }
            }

            LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(payment.getOrderId());
            boolean preclosureWithCharges = false;
            double foreclosureChargesAmount = 0.0;
            if(loanForeClosureCharges != null) {
                if (loanForeClosureCharges.getTax() == null) loanForeClosureCharges.setTax(0.0);
                foreclosureChargesAmount = loanForeClosureCharges.getAmount() + loanForeClosureCharges.getTax();
                preclosureWithCharges = true;
                logger.info("foreclosure charges exist for the orderId {} and charges : {} amount : {}",payment.getOrderId(), loanForeClosureCharges, foreclosureChargesAmount);
            }

            logger.info("Received pre closure amount:{} for loan:{}", amount, loan.getId());
            double paidInterestAmount = (loan.getDueInterest() != null ? loan.getDueInterest() : 0);
            double paidPenalty = Objects.nonNull(loan.getDuePenalty()) ? loan.getDuePenalty() : 0;
            double paidPrincipalAmount = amount - paidInterestAmount + excessCollectionBalance - paidPenalty - foreclosureChargesAmount;
            double remainingBalance = (loan.getPaidPrinciple() + paidPrincipalAmount) - loan.getLoanAmount();
            if (remainingBalance > 0) {
                logger.info("Extra principle received for loanId:{} and extra amount:{}", loan.getId(), remainingBalance);
                paidPrincipalAmount -= remainingBalance;
                paidInterestAmount += remainingBalance;
            }

            logger.info("Adjusting breakup amount for loan:{} is principle:{} and interest:{} and foreclosureCharges : {}", loan.getId(), paidPrincipalAmount, paidInterestAmount, foreclosureChargesAmount);
            if (EDI_BY_EDI.name().equalsIgnoreCase(settlementMechanism)) {
                double totalAmount = payment.getOtherAmount() + excessCollectionBalance + advanceEdiAmount;
                paymentSettlementService.settlePreclosureLoanPayment(loan.getId(), loan.getEdiCount(), loan.getEdiRemainingCount(), loan.getSettleAllPrinciple(), totalAmount);
            } else {
                //IPC
                //No Actions
            }
            logger.info("Adjusted breakup amount for loan:{} is principle:{} and interest:{} and penalty: {} and foreclosureCharges : {}", loan.getId(),
                    paidPrincipalAmount, paidInterestAmount, paidPenalty, foreclosureChargesAmount);
            String description = (preclosureWithCharges) ? "PREPAYMENT_WITH_CHARGES" : "PREPAYMENT";

            if (Objects.isNull(loan.getDueAmount())) loan.setDueAmount(0d);
            PaymentCalculation paymentAdjusted = PaymentCalculation.builder()
                    .used(-1 * Math.abs(amount - loan.getDueAmount() + advanceEdiAmount + excessCollectionBalance))
                    .principleSettled(-1 * Math.abs(amount - loan.getDueAmount() - ediHolidayInterestAmount + advanceEdiAmount + excessCollectionBalance - paidPenalty - foreclosureChargesAmount))
                    .interestSettled(Double.valueOf(ediHolidayInterestAmount))
                    .penaltySettled(-1*paidPenalty)
                    .chargesSettled(-1*foreclosureChargesAmount)
                    .build();
            createLendingLedger(loan, paymentAdjusted, description, payment.getOwner(), payment.getTransferType(), payment.getTerminalOrderId());
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
            loan.setPaidOtherCharges((loan.getPaidOtherCharges() != null ? loan.getPaidOtherCharges() : 0)+ foreclosureChargesAmount);

            // todo: add positive ledger and foreclosure

            loan.setStatus("CLOSED");
            loan.setClosingDate(new Date());
            //call foreclosureService
        }
    }

    private void markExcessNachAdjusted(LendingPaymentSchedule loan, List<LendingCollectionExcess> lendingCollectionExcessList) {
        logger.info("Adjusting excess collection for loan in ledger : {}", loan.getId());
        createLendingLedgerForExcessCollectionOnForeclosure(loan, lendingCollectionExcessList);
        settleExcessCollectionBalance(loan.getId(), lendingCollectionExcessList);
    }

    private void foreCloseLoanIPC(LendingPaymentSchedule loan, LoanPaymentDetailDto payment, List<LoanPaymentMode> paymentAdjustmentPrefrenceList, LoanSettlementMechanism loanSettlementMechanism) {
    }

    //==================================================================================================================
    // __________________________________     Calculation   ____________________________________________________________
    //==================================================================================================================

    //TODO: add null check when updating paidIntrest,P,C
    private PaymentCalculation adjustInterest(LendingPaymentSchedule activeLoan, double amount) {
        double interestPaid = 0;
        logger.info("LoanAdjustment#{} adjustInterest is started for loan {} with amount :{}",activeLoan.getId(), activeLoan, amount);
        if (amount > 0D && activeLoan.getDueInterest() != null && activeLoan.getDueInterest() > 0D) {
            interestPaid = Math.min(activeLoan.getDueInterest(), amount);
            activeLoan.setDueInterest(activeLoan.getDueInterest() - interestPaid);
            activeLoan.setPaidInterest((activeLoan.getPaidInterest() != null ? activeLoan.getPaidInterest() : 0) + interestPaid);

            activeLoan.setDueAmount(activeLoan.getDueAmount() - interestPaid);
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + interestPaid);
            logger.info("LoanAdjustment#{} Adjusted due interest of amount:{} for loan:{}",activeLoan.getId(), interestPaid, activeLoan);
        }
        logger.info("LoanAdjustment#{} adjustInterest is completed for loan {} with adjustment  received :{} adjusted :{} balance {}",activeLoan.getId(), activeLoan, amount, interestPaid, amount - interestPaid);
        return PaymentCalculation.builder()
                .received(amount)
                .used(interestPaid)
                .balance(amount - interestPaid)
                .build();
    }


    private PaymentCalculation adjustPrinciple(LendingPaymentSchedule activeLoan, double amount) {
        double principalPaid = 0;
        logger.info("LoanAdjustment#{} adjustPrinciple is started for loan {} with amount :{}",activeLoan.getId(), activeLoan, amount);
        if (amount > 0D && activeLoan.getDuePrinciple() != null && activeLoan.getDuePrinciple() > 0D) {
            principalPaid = Math.min(activeLoan.getDuePrinciple(), amount);

            activeLoan.setDuePrinciple(activeLoan.getDuePrinciple() - principalPaid);
            activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + principalPaid);

            activeLoan.setDueAmount(activeLoan.getDueAmount() - principalPaid);
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + principalPaid);
            logger.info("LoanAdjustment#{} adjustPrinciple of amount:{} for loan:{}",activeLoan.getId(), principalPaid, activeLoan);
        }
        logger.info("LoanAdjustment#{} adjustPrinciple is completed for loan {} with adjustment  received :{} adjusted :{} balance {}",activeLoan.getId(), activeLoan, amount, principalPaid, amount - principalPaid);
        return PaymentCalculation.builder()
                .received(amount)
                .used(principalPaid)
                .balance(amount - principalPaid)
                .build();
    }


    private PaymentCalculation adjustPenalty(LendingPaymentSchedule activeLoan, double amount) {
        double penaltyPaid = 0;
        logger.info("LoanAdjustment#{} adjustPenalty is started for loan {} with amount :{}",activeLoan.getId(), activeLoan, amount);

        if (amount > 0D && activeLoan.getDuePenalty() != null && activeLoan.getDuePenalty() > 0D) {
            penaltyPaid = Math.min(activeLoan.getDuePenalty(), amount);
            activeLoan.setDuePenalty(activeLoan.getDuePenalty() - penaltyPaid);
            activeLoan.setPaidPenalty((Objects.nonNull(activeLoan.getPaidPenalty()) ? activeLoan.getPaidPenalty() : 0d) + penaltyPaid);
            logger.info("LoanAdjustment#{} adjustPenalty of amount:{} for loan:{}",activeLoan.getId(), penaltyPaid, activeLoan);
        }

        logger.info("LoanAdjustment#{} adjustPenalty is completed for loan {} with adjustment  received :{} adjusted :{} balance {}",activeLoan.getId(), activeLoan, amount, penaltyPaid, amount - penaltyPaid);
        return PaymentCalculation.builder()
                .received(amount)
                .used(penaltyPaid)
                .balance(amount - penaltyPaid)
                .build();
    }

    private PaymentCalculation adjustOtherCharges(LendingPaymentSchedule activeLoan, double amount) {
        double chargesPaid = 0;
        logger.info("LoanAdjustment#{} adjustOtherCharges is started for loan {} with amount :{}",activeLoan.getId(), activeLoan, amount);

        if (amount > 0D && activeLoan.getDueOtherCharges() != null && activeLoan.getDueOtherCharges() > 0D) {
            chargesPaid = Math.min(activeLoan.getDueOtherCharges(), amount);

            activeLoan.setDueOtherCharges(activeLoan.getDueOtherCharges() - chargesPaid);
            activeLoan.setPaidOtherCharges(activeLoan.getPaidOtherCharges() + chargesPaid);

            activeLoan.setDueAmount(activeLoan.getDueAmount() - chargesPaid);
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + chargesPaid);
            logger.info("LoanAdjustment#{} adjustOtherCharges of amount:{} for loan:{}",activeLoan.getId(), chargesPaid, activeLoan);
        }

        logger.info("LoanAdjustment#{} adjustOtherCharges is completed for loan {} with adjustment  received :{} adjusted :{} balance {}",activeLoan.getId(), activeLoan, amount, chargesPaid, amount - chargesPaid);
        return PaymentCalculation.builder()
                .received(amount)
                .used(chargesPaid)
                .balance(amount - chargesPaid)
                .build();
    }


    //==================================================================================================================
    // _______________________________________    LEDGER   _____________________________________________________________
    //==================================================================================================================

    private LendingCollectionExcess adjustNachLedger(LendingCollectionExcess lendingCollectionExcess, PaymentCalculation paymentAdjusted) {
        logger.info("adjustNachLedger: initiating nach : {} payment : {}", lendingCollectionExcess, paymentAdjusted);
        lendingCollectionExcess.setAmount(lendingCollectionExcess.getAmount() - paymentAdjusted.getUsed());
        lendingCollectionExcess.setDeductedAmount(lendingCollectionExcess.getDeductedAmount() + paymentAdjusted.getUsed());

        lendingCollectionExcess.setDeductionCount(lendingCollectionExcess.getDeductionCount() + 1);
        if (lendingCollectionExcess.getAmount() < 1D){
            lendingCollectionExcess.setStatus("CLOSED");
        }
        lendingCollectionExcessDao.save(lendingCollectionExcess);
        logger.info("adjustNachLedger: complete nach : {} payment : {}", lendingCollectionExcess, paymentAdjusted);
        return lendingCollectionExcess;
    }

    private void adjustAdvanceEdiLedger(LendingPaymentSchedule loan, PaymentCalculation advanceAdjustment) {
        LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(loan.getMerchantId(), loan.getId());
        if (lendingPrepayment != null) {
            lendingPrepayment.setAdvanceEdiCount(lendingPrepayment.getAdvanceEdiCount() - (int)(advanceAdjustment.getUsed()/loan.getEdiAmount()));
            lendingPrepayment.setAdvanceEdiAmount(lendingPrepayment.getAdvanceEdiAmount() - advanceAdjustment.getUsed());
            lendingPrepaymentDao.save(lendingPrepayment);
        }
    }

    private void adjustLendingLedger(LendingPaymentSchedule loan, PaymentCalculation paymentAdjustment, LoanPaymentOrder order, String desc, String adjustmentMode, String transferType, String bankReferenceNo) {
        LendingLedger lendingLedger = createLendingLedger(loan, paymentAdjustment, desc, adjustmentMode, transferType, bankReferenceNo);
        updateCollectionAuditAndOrder(lendingLedger, order);
    }
    private void updateCollectionAuditAndOrder(LendingLedger lendingLedger, LoanPaymentOrder order) {
        if (Objects.nonNull(lendingLedger))lendingCollectionAuditService.sendCollectionAudit(lendingLedger);
        if (Objects.nonNull(order)) markOrderSuccess(order);
    }

    private LendingLedger createLendingLedger(LendingPaymentSchedule loan, PaymentCalculation paymentAdjusted, String description, String adjustmentMode, String transferType, String terminalOrderId) {
        if(paymentAdjusted.getUsed() == 0) return null;

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(loan.getMerchantId());
        if(loan.getMerchantStoreId() != null && loan.getMerchantStoreId() > 0) lendingLedger.setMerchantStoreId(loan.getMerchantStoreId());
        lendingLedger.setLendingPaymentSchedule(loan);
        lendingLedger.setDate(DateTimeUtil.getCurrentDayStartTime());
        lendingLedger.setTxnType("EDI");
        lendingLedger.setAmount(paymentAdjusted.getUsed());
        lendingLedger.setInterest(paymentAdjusted.getInterestSettled());
        lendingLedger.setPrinciple(paymentAdjusted.getPrincipleSettled());
        lendingLedger.setOtherCharges(paymentAdjusted.getChargesSettled());
        lendingLedger.setPenalty(paymentAdjusted.getPenaltySettled());
        lendingLedger.setAdjustmentMode(adjustmentMode);
        lendingLedger.setDescription(description);
        lendingLedger.setTransferType(transferType);
        lendingLedger.setTerminalOrderId(terminalOrderId);
        lendingLedgerDao.save(lendingLedger);
        return lendingLedger;
    }


    private LoanPaymentOrder createLoanPaymentOrder(LendingPaymentSchedule loan, double orderAmount, String paymentReferenceNo, String status, String source) {
        String orderId = LOAN_PAYMENT_ORDER_ID_PREFIX + loan.getId() + System.currentTimeMillis();
        log.info("createLoanPaymentOrder : creating LPO loanId:{} amount:{} status:{} source :{}", loan.getId(), orderAmount, status, source);
        LoanPaymentOrder order = new LoanPaymentOrder();
        order.setMerchantId(loan.getMerchantId());
        order.setMerchantStoreId(loan.getMerchantStoreId());
        order.setOwner(LOAN_PAYMENT_ORDER_OWNER);
        order.setOwnerId(loan.getId());
        order.setOrderId(orderId);
        order.setAmount(orderAmount);
        order.setBankRefNo(paymentReferenceNo);
        order.setStatus(status);
        order.setSource(source);
        return loanPaymentOrderDao.save(order);
    }

    private void markOrderSuccess(LoanPaymentOrder order) {
        log.info("markOrderSuccess : LPO updating  orderId:{} and currentStatus :{} loanId : {}", order.getId(), order.getStatus(), order.getOwnerId());
        order.setStatus("SUCCESS");
        loanPaymentOrderDao.save(order);
        log.info("markOrderSuccess : LPO  updated orderId:{} and newStatus :{} loanId : {}", order.getId(), order.getStatus(), order.getOwnerId());
    }


    private void createLendingLedgerForExcessCollectionOnForeclosure(LendingPaymentSchedule activeLoan, List<LendingCollectionExcess> lendingCollectionExcessList){
        if(ObjectUtils.isEmpty(lendingCollectionExcessList))return;
        List<LendingLedger> lendingLedgersListExcessCollection = new ArrayList<>();
        for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
            String desc = lendingCollectionExcess.getTerminalOrderId() + EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX + (lendingCollectionExcess.getDeductionCount() + 1);
            PaymentCalculation paymentAdjusted = PaymentCalculation.builder()
                    .used(lendingCollectionExcess.getAmount())
                    .principleSettled(lendingCollectionExcess.getAmount())
                    .build();
            LendingLedger excessCollectionLedger = createLendingLedger(activeLoan, paymentAdjusted,desc,"EXCESS_NACH_ADJUSTED", "EXTERNAL", desc);
            lendingLedgersListExcessCollection.add(excessCollectionLedger);
        }
    }

    private void settleExcessCollectionBalance(Long loanId, List<LendingCollectionExcess> lendingCollectionExcessList){
        if(ObjectUtils.isEmpty(lendingCollectionExcessList))return;
        logger.info("settling excess collection upon foreclosure for loanId:{}, {}", loanId, lendingCollectionExcessList);
        for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
            lendingCollectionExcess.setDeductedAmount(lendingCollectionExcess.getDeductedAmount() + lendingCollectionExcess.getAmount());
            lendingCollectionExcess.setAmount(0D);
            lendingCollectionExcess.setDeductionCount(lendingCollectionExcess.getDeductionCount() + 1);
            lendingCollectionExcess.setStatus("CLOSED");
            lendingCollectionExcessDao.save(lendingCollectionExcess);
        }
    }

    private String getLoanSettlementMechanism(LendingPaymentSchedule loan) {
        logger.info("getLoanSettlementMechanism for loanId: {} is {}", loan.getId(), loan.getSettlementMechanism());

        int dpdCount = LoanUtil.calculateDPD(loan.getEdiAmount(), loan.getDueAmount());
        logger.info("getLoanSettlementMechanism for loanId: {} dpd is  {}", loan.getId(), dpdCount);

        if (dpdCount > 90) {
            logger.info("getLoanSettlementMechanism for loanId: {} is a NPA with dpd : {} and mechanism is {}", loan.getId(), dpdCount, NPA.name());
            return NPA.name();
        }

        String mechanism = getOrDefaultSettlementMechanismFromLoan(loan.getSettlementMechanism(), DEFAULT_LOAN_SETTLEMENT_MECHANISM);
        logger.info("getLoanSettlementMechanism for loanId: {} calculated mechanism is {}", loan.getId(), mechanism);

        if (EDI_BY_EDI.name().equalsIgnoreCase(mechanism)) {
            logger.info("getLoanSettlementMechanism for loanId: {} is {}", loan.getId(), EDI_BY_EDI.name());
            return EDI_BY_EDI.name();
        }

        if (IPC.name().equalsIgnoreCase(mechanism)) {
            logger.info("getLoanSettlementMechanism for loanId: {} is {}", loan.getId(), IPC.name());
            return IPC.name();
        }

        logger.info("getLoanSettlementMechanism for loanId: {} can't determine mechanism is {} so using default mechanism {} ", loan.getId(), mechanism, DEFAULT_LOAN_SETTLEMENT_MECHANISM);
        return DEFAULT_LOAN_SETTLEMENT_MECHANISM;
    }


    public static String getOrDefaultSettlementMechanismFromLoan(String name, String defaultMechanism) {
        LoanSettlementMechanism loanSettlementMechanism = EnumUtils.getEnumIgnoreCase(LoanSettlementMechanism.class, name);
        return loanSettlementMechanism != null ? loanSettlementMechanism.name() : defaultMechanism;
    }

    private Integer getEDIHolidayInterestAmount(LendingPaymentSchedule lps) {
        try {
            List<LendingEDISchedule> lendingEDISchedules = lendingEDIScheduleDao.getByLoanIdAndEdiType(lps.getId(), "EDIHOLIDAY");
            if (lendingEDISchedules != null && !lendingEDISchedules.isEmpty()) {
                return lendingEDISchedules.stream().mapToInt(LendingEDISchedule::getTotalEdi).sum();
            }
        } catch(Exception ex) {
            logger.error("Exception in getEDIHolidayInterestAmount for Loan ID {}, Exception is {}", lps.getId(), ex);
        }
        return 0;
    }

}