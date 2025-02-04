package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;
import com.bharatpe.lending.collection.core.service.AdjustLoanBalanceService;
import com.bharatpe.lending.collection.core.utils.LoanPaymentUtil;
import com.bharatpe.lending.common.dao.LendingEDIScheduleLendingCommonDao;
import com.bharatpe.lending.common.entity.LendingEDIScheduleLendingCommon;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

import static com.bharatpe.lending.common.enums.LoanSettlementMechanism.EDI_BY_EDI;


@Service
@Slf4j
public class AdjustLoanBalanceByEdiByEdiServiceImpl implements AdjustLoanBalanceService {

    private static final int PAGE_SIZE = 50;

    @Autowired
    LendingEDIScheduleLendingCommonDao lendingEDIScheduleLendingCommonDao;

    @Override
    public PaymentCalculation adjustLoanBalance(LendingPaymentSchedule loan, double amount) {
        return adjustEdiSchedule(loan, amount, loan.getSettleAllPrinciple());
    }

    //==================================================================================================================
    // __________________________________     Calculation   ____________________________________________________________
    //==================================================================================================================
    public PaymentCalculation adjustEdiSchedule(LendingPaymentSchedule loan, double amount, Boolean adjustPrincipleFirst) {
        if (adjustPrincipleFirst == null) adjustPrincipleFirst = false;

        PaymentCalculation settleLoanPaymentDTO = settleEDIPrincipleAndInterest(loan, amount, adjustPrincipleFirst);

        PaymentCalculation penalty = adjustPenalty(loan, settleLoanPaymentDTO.getBalance());
        settleLoanPaymentDTO.setBalance(settleLoanPaymentDTO.getBalance() - penalty.getUsed());

        PaymentCalculation charges = adjustOtherCharges(loan, settleLoanPaymentDTO.getBalance());
        settleLoanPaymentDTO.setBalance(settleLoanPaymentDTO.getBalance() - charges.getUsed());

        if (adjustPrincipleFirst) checkForNPA(loan);
        if (settleLoanPaymentDTO.isAllDuePaid() && checkForTerminalEdiAmountDiff(loan)) adjustExtraBalance(loan, settleLoanPaymentDTO);

        return  PaymentCalculation.builder()
                .received(amount)
                .used(amount - settleLoanPaymentDTO.getBalance())
                .balance(settleLoanPaymentDTO.getBalance())
                .principleSettled(settleLoanPaymentDTO.getPrincipleSettled())
                .interestSettled(settleLoanPaymentDTO.getInterestSettled())
                .penaltySettled(penalty.getUsed())
                .chargesSettled(charges.getUsed())
                .build();
    }

    private boolean checkForTerminalEdiAmountDiff(LendingPaymentSchedule loan) {
        log.info("checkForTerminalEdiAmountDiff for loanId : {} loan: {}", loan.getId(), loan);
        return loan != null && loan.getEdiRemainingCount() == 0 && EDI_BY_EDI.name().equalsIgnoreCase(loan.getSettlementMechanism())
                && ((loan.getEdiAmount() * loan.getEdiCount()) -  loan.getTotalPayableAmount() == loan.getDueAmount())
                && Objects.equals(loan.getPaidPrinciple(), loan.getLoanAmount());
    }

    // refer LC- 474 Case 2  https://bharatpe.atlassian.net/wiki/x/KgDGE
    private void adjustExtraBalance(LendingPaymentSchedule loan, PaymentCalculation settleLoanPaymentDTO) {
        log.info("adjustExtraBalance for loanId : {} loan: {}", loan.getId(), loan);

        double extraAmount = Math.max(loan.getEdiAmount() * loan.getEdiCount() - loan.getTotalPayableAmount(), 0);
        log.info("adjustExtraBalance diff in lps for loanId : {} extraAmount: {}", loan.getId(), extraAmount);
        extraAmount = Math.min(extraAmount, loan.getDueInterest()); // expected = due_amount == due_interest
        log.info("adjustExtraBalance post dueInterest for loanId : {} extraAmount: {}, dueInterest:{}", loan.getId(), extraAmount, loan.getDueInterest());

        extraAmount = Math.min(extraAmount, settleLoanPaymentDTO.getBalance());
        log.info("adjustExtraBalance post balance for loanId : {} extraAmount: {}, balance:{}", loan.getId(), extraAmount, settleLoanPaymentDTO.getBalance());

        if (extraAmount > 0) {
            log.info("adjustExtraBalance pre settleLoanPaymentDTO for extraAmount : {} balance: {}, interest:{}", extraAmount, settleLoanPaymentDTO.getBalance(), settleLoanPaymentDTO.getInterestSettled());
            settleLoanPaymentDTO.setBalance(Math.max(settleLoanPaymentDTO.getBalance() - extraAmount, 0));
            settleLoanPaymentDTO.setInterestSettled(settleLoanPaymentDTO.getInterestSettled() + extraAmount);
            log.info("adjustExtraBalance post settleLoanPaymentDTO for extraAmount : {} balance: {}, interest:{}", extraAmount, settleLoanPaymentDTO.getBalance(), settleLoanPaymentDTO.getInterestSettled());

            log.info("adjustExtraBalance pre interest for extraAmount : {} dueInterest: {}, paidInterest:{}", extraAmount, loan.getDueInterest(), loan.getPaidInterest());
            loan.setDueInterest(Math.max(loan.getDueInterest() - extraAmount, 0));
            loan.setPaidInterest((loan.getPaidInterest() != null ? loan.getPaidInterest() : 0) + extraAmount);
            log.info("adjustExtraBalance post interest for extraAmount : {} balance: {}, paidInterest:{}", extraAmount, loan.getDueInterest(), loan.getPaidInterest());

            log.info("adjustExtraBalance pre amount for extraAmount: {} due: {}, paid:{}", extraAmount, loan.getDueAmount(), loan.getPaidAmount());
            loan.setDueAmount(Math.max(loan.getDueAmount() - extraAmount, 0));
            loan.setPaidAmount(loan.getPaidAmount() + extraAmount);
            log.info("adjustExtraBalance post amount for extraAmount: {} due: {}, paid:{}", extraAmount, loan.getDueAmount(), loan.getPaidAmount());
        }
    }

    private void checkForNPA(LendingPaymentSchedule activeLoan) {
        // switch back to IPC if all due is paid
        if (activeLoan.getDueAmount() <= 0) {
            activeLoan.setSettleAllPrinciple(false);
        }
    }

    private PaymentCalculation settleEDIPrincipleAndInterest(LendingPaymentSchedule loan, double amount, Boolean settleAllPrinciple) {
        PaymentCalculation settleLoanPaymentDTO = settleLoanDuePayment(loan.getId(), loan.getEdiCount(), loan.getEdiRemainingCount(), settleAllPrinciple, amount, loan.getSettlementInitiated());
        double paidPrincipalAmount = settleLoanPaymentDTO.getPrincipleSettled();
        double paidInterestAmount = settleLoanPaymentDTO.getInterestSettled();

        loan.setDuePrinciple(loan.getDuePrinciple() - paidPrincipalAmount);
        loan.setPaidPrinciple((loan.getPaidPrinciple() != null ? loan.getPaidPrinciple() : 0) + paidPrincipalAmount);

        loan.setDueInterest(loan.getDueInterest() - paidInterestAmount);
        loan.setPaidInterest((loan.getPaidInterest() != null ? loan.getPaidInterest() : 0) + paidInterestAmount);

        loan.setDueAmount(loan.getDueAmount() - (paidPrincipalAmount + paidInterestAmount));
        loan.setPaidAmount(loan.getPaidAmount() + paidPrincipalAmount + paidInterestAmount);

        return settleLoanPaymentDTO;
    }

    private PaymentCalculation adjustPenalty(LendingPaymentSchedule activeLoan, double amount) {
        double penaltyPaid = 0;
        log.info("LoanAdjustment#{} adjustPenalty is started for loan {} with amount :{}",activeLoan.getId(), activeLoan, amount);

        if (amount > 0D && activeLoan.getDuePenalty() != null && activeLoan.getDuePenalty() > 0D) {
            penaltyPaid = Math.min(activeLoan.getDuePenalty(), amount);
            activeLoan.setDuePenalty(activeLoan.getDuePenalty() - penaltyPaid);
            activeLoan.setPaidPenalty((Objects.nonNull(activeLoan.getPaidPenalty()) ? activeLoan.getPaidPenalty() : 0d) + penaltyPaid);
            log.info("LoanAdjustment#{} adjustPenalty of amount:{} for loan:{}",activeLoan.getId(), penaltyPaid, activeLoan);
        }

        log.info("LoanAdjustment#{} adjustPenalty is completed for loan {} with adjustment  received :{} adjusted :{} balance {}",activeLoan.getId(), activeLoan, amount, penaltyPaid, amount - penaltyPaid);
        return PaymentCalculation.builder()
                .received(amount)
                .used(penaltyPaid)
                .balance(amount - penaltyPaid)
                .penaltySettled(penaltyPaid)
                .build();
    }

    private PaymentCalculation adjustOtherCharges(LendingPaymentSchedule activeLoan, double amount) {
        double chargesPaid = 0;
        log.info("LoanAdjustment#{} adjustOtherCharges is started for loan {} with amount :{}",activeLoan.getId(), activeLoan, amount);

        if (amount > 0D && activeLoan.getDueOtherCharges() != null && activeLoan.getDueOtherCharges() > 0D) {
            chargesPaid = Math.min(activeLoan.getDueOtherCharges(), amount);

            activeLoan.setDueOtherCharges(activeLoan.getDueOtherCharges() - chargesPaid);
            activeLoan.setPaidOtherCharges((Objects.nonNull(activeLoan.getPaidOtherCharges()) ? activeLoan.getPaidOtherCharges() : 0) + chargesPaid);

            activeLoan.setDueAmount(activeLoan.getDueAmount() - chargesPaid);
            activeLoan.setPaidAmount((Objects.nonNull(activeLoan.getPaidAmount()) ? activeLoan.getPaidAmount() : 0) + chargesPaid);
            log.info("LoanAdjustment#{} adjustOtherCharges of amount:{} for loan:{}",activeLoan.getId(), chargesPaid, activeLoan);
        }

        log.info("LoanAdjustment#{} adjustOtherCharges is completed for loan {} with adjustment  received :{} adjusted :{} balance {}",activeLoan.getId(), activeLoan, amount, chargesPaid, amount - chargesPaid);
        return PaymentCalculation.builder()
                .received(amount)
                .used(chargesPaid)
                .balance(amount - chargesPaid)
                .chargesSettled(chargesPaid)
                .build();
    }

    public PaymentCalculation settleLoanDuePayment(Long loanId, Integer ediCount, Integer ediRemainingCount, Boolean settleAllPrinciple, Double amountBeingPaid, boolean settlementInitiated) {
        Integer ediCreatedTillDate = ediCount - ediRemainingCount;
        log.info("ediCreatedTillDate : {} for loanId : {}", ediCreatedTillDate, loanId);
//        For settlement initiated cases the received amount needs to be adjusted for future schedules also, if possible
//        (in case if enough amount received).
        if(settlementInitiated) {
            ediCreatedTillDate = ediCount;
        }

        Double paidPrinciple = 0d;
        Double paidInterest = 0d;
        Double remainingAmountToSettle = amountBeingPaid;

        List<LendingEDIScheduleLendingCommon> unpaidSchedulesForALoan = lendingEDIScheduleLendingCommonDao.findUnpaidSchedulesForALoan(loanId, ediCreatedTillDate);

        PaymentCalculation paymentCalculation;
        if (settleAllPrinciple != null && settleAllPrinciple) {
            paymentCalculation =  settleAllPrincipleFirstThenAllInterest(loanId, unpaidSchedulesForALoan, remainingAmountToSettle);
        } else {
            paymentCalculation =  settleEdiByEdi(loanId, unpaidSchedulesForALoan, remainingAmountToSettle);
        }

        remainingAmountToSettle = paymentCalculation.getBalance();
        paidPrinciple += paymentCalculation.getPrincipleSettled();
        paidInterest += paymentCalculation.getInterestSettled();

        log.info("paidPrinciple : {}, paidInterest : {}, remainingAmountToSettle : {} for loanId : {}", paidPrinciple, paidInterest, remainingAmountToSettle, loanId);
        return PaymentCalculation.builder()
                .received(amountBeingPaid)
                .used(paidPrinciple + paidInterest)
                .balance(remainingAmountToSettle)
                .interestSettled(paidInterest)
                .principleSettled(paidPrinciple)
                .allDuePaid(ediRemainingCount == 0 && checkIfAllDuePaid(loanId, unpaidSchedulesForALoan, ediCount))
                .build();
    }

    // in case of edi by edi loan if terminal edi is repaid is fully
    // then no further payment will be adjusted irrespective of payment mode...
    private boolean checkIfAllDuePaid(long loanId, List<LendingEDIScheduleLendingCommon> edis, int ediCount) {
        log.info("starting checkIfAllDuePaid for loanId : {}", loanId);
        try {
            if (!CollectionUtils.isEmpty(edis)) {
                LendingEDIScheduleLendingCommon schedule = edis.stream().filter(_edi -> _edi.getInstallmentNumber() == ediCount).findAny().orElse(null);
                if (schedule != null) {
                    log.info("checkIfAllDuePaid for loanId : {} schId : {} installment_no :{}", loanId, schedule.getId(), schedule.getInstallmentNumber());
                    return Objects.equals(schedule.getPrinciple(), schedule.getPaidPrinciple()) && Objects.equals(schedule.getInterest(), schedule.getPaidInterest());
                }
            }
        } catch (Exception e) {
            log.error("Exception in the loan checkIfAllDuePaid {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        log.info("checkIfAllDuePaid for loanId : {} output: false ", loanId);
        return false;
    }

    // adjustExtraBalance is not required - as we explicitly mark loan due as zero
    // also in case of foreclosure the last edi will not have any paid_interest
    public PaymentCalculation settlePreClosureLoanPayment(Long loanId, Integer ediCount, Integer ediRemainingCount, Boolean settleAllPrinciple, Double amountBeingPaid) {

        // settle all due amount first
        final PaymentCalculation settleLoanPaymentDTO = settleLoanDuePayment(loanId, ediCount, ediRemainingCount, settleAllPrinciple, amountBeingPaid, false);

        log.info("Remaining amount to settle in after all due are settled for loanId : {} is : {}", loanId, amountBeingPaid);

        double paidPrinciple = settleLoanPaymentDTO.getPrincipleSettled();
        double paidInterest = settleLoanPaymentDTO.getInterestSettled();
        double remainingAmountToSettle = settleLoanPaymentDTO.getBalance();

        int pageCount = 0;
        boolean continueAmountDeduction = true;

        // settle principal of the remaining schedules since its a preclosure
        while(continueAmountDeduction) {
            Pageable pageable = PageRequest.of(pageCount, PAGE_SIZE);
            Slice<LendingEDIScheduleLendingCommon> unpaidSchedulesForALoan = lendingEDIScheduleLendingCommonDao.findAllUnpaidSchedulesForALoan(loanId, pageable);
            PaymentCalculation principalAdjusted =  settleAllPrinciple(loanId, unpaidSchedulesForALoan.getContent(), remainingAmountToSettle);
            remainingAmountToSettle = principalAdjusted.getBalance();
            paidPrinciple += principalAdjusted.getPrincipleSettled();
            if (unpaidSchedulesForALoan.hasNext() && remainingAmountToSettle > 0) {
                pageCount++;
            } else {
                continueAmountDeduction = false;
            }
        }

        log.info("paidPrinciple : {}, paidInterest : {}, remainingAmountToSettle : {} for loanId : {}", paidPrinciple, paidInterest, remainingAmountToSettle, loanId);
        return PaymentCalculation.builder()
                .received(amountBeingPaid)
                .used(paidPrinciple + paidInterest)
                .balance(remainingAmountToSettle)
                .interestSettled(paidInterest)
                .principleSettled(paidPrinciple)
                .build();
    }

    private PaymentCalculation settleEdiByEdi(Long loanId, List<LendingEDIScheduleLendingCommon> unpaidSchedulesForALoan, Double amount) {
        log.info("settling Edi by Edi for loanId : {}", loanId);
        double paidPrinciple = 0;
        double paidInterest = 0;
        double balance = amount;

        for (LendingEDIScheduleLendingCommon schedule : unpaidSchedulesForALoan) {
            log.info("remainingAmountToSettle : {} for loanId : {} and scheduleId : {}", balance, loanId, schedule.getId());
            PaymentCalculation interestAdjusted = settleEdiScheduleInterest(loanId, schedule, balance);
            balance = interestAdjusted.getBalance();
            paidInterest += interestAdjusted.getUsed();

            if (balance > 0) { //Adjust principal
                PaymentCalculation principleAdjusted = settleEdiSchedulePrinciple(loanId, schedule, balance);
                balance = principleAdjusted.getBalance();
                paidPrinciple += principleAdjusted.getUsed();
            }

            if (balance <= 0.0) {
                break;
            }
        }

        lendingEDIScheduleLendingCommonDao.saveAll(unpaidSchedulesForALoan);
        log.info("settleAllPrincipleFirstThenAllInterest: paidPrinciple : {}, paidInterest : {}, remainingAmountToSettle : {} for loanId : {}", paidPrinciple, paidInterest, balance, loanId);
        return PaymentCalculation.builder()
                .received(amount)
                .used(paidPrinciple + paidInterest)
                .balance(balance)
                .interestSettled(paidInterest)
                .principleSettled(paidPrinciple)
                .build();

    }

    private PaymentCalculation settleAllPrincipleFirstThenAllInterest(Long loanId, List<LendingEDIScheduleLendingCommon> unpaidSchedulesForALoan, double amount) {
        log.info("settling all principle first for loanId : {}", loanId);
        double paidPrinciple = 0;
        double paidInterest = 0;
        double balance = amount;
        // settle complete due principle first
        for (LendingEDIScheduleLendingCommon schedule : unpaidSchedulesForALoan) {
            PaymentCalculation principleAdjusted = settleEdiSchedulePrinciple(loanId, schedule, balance);
            balance = principleAdjusted.getBalance();
            paidPrinciple += principleAdjusted.getUsed();
            if (balance <= 0.0) {
                break;
            }
        }

        // settle interest
        if (balance > 0) {
            for (LendingEDIScheduleLendingCommon schedule : unpaidSchedulesForALoan) {
                PaymentCalculation interestAdjusted = settleEdiScheduleInterest(loanId, schedule, balance);
                balance = interestAdjusted.getBalance();
                paidInterest += interestAdjusted.getUsed();
                if (balance <= 0.0) {
                    break;
                }
            }
        }

        lendingEDIScheduleLendingCommonDao.saveAll(unpaidSchedulesForALoan);
        log.info("settleAllPrincipleFirstThenAllInterest: paidPrinciple : {}, paidInterest : {}, remainingAmountToSettle : {} for loanId : {}", paidPrinciple, paidInterest, balance, loanId);
        return PaymentCalculation.builder()
                .received(amount)
                .used(paidPrinciple + paidInterest)
                .balance(balance)
                .interestSettled(paidInterest)
                .principleSettled(paidPrinciple)
                .build();
    }


    private PaymentCalculation settleAllPrinciple(Long loanId, List<LendingEDIScheduleLendingCommon> unpaidSchedulesForALoan, double amount) {
        log.info("settling all principle for loanId : {}", loanId);
        double paidPrinciple = 0;
        double paidInterest = 0;
        double balance = amount;
        // settle complete due principle
        for (LendingEDIScheduleLendingCommon schedule : unpaidSchedulesForALoan) {
            PaymentCalculation principleAdjusted = settleEdiSchedulePrinciple(loanId, schedule, balance);
            balance = principleAdjusted.getBalance();
            paidPrinciple += principleAdjusted.getUsed();
            if (balance <= 0.0) {
                break;
            }
        }

        lendingEDIScheduleLendingCommonDao.saveAll(unpaidSchedulesForALoan);
        log.info("settleAllPrinciple: paidPrinciple : {}, paidInterest : {}, remainingAmountToSettle : {} for loanId : {}", paidPrinciple, paidInterest, balance, loanId);
        return PaymentCalculation.builder()
                .received(amount)
                .used(paidPrinciple + paidInterest)
                .balance(balance)
                .interestSettled(paidInterest)
                .principleSettled(paidPrinciple)
                .build();
    }

    private PaymentCalculation settleEdiScheduleInterest(Long loanId, LendingEDIScheduleLendingCommon schedule, double balance) {
        log.info("remainingAmountToSettle : {} for loanId : {} and scheduleId : {}", balance, loanId, schedule.getId());
        double previouslyPaidInterestInSchedule = Objects.nonNull(schedule.getPaidInterest()) ? schedule.getPaidInterest() : 0;

        log.info("previouslyPaidInterestInSchedule : {} for loanId : {} and scheduleId : {}", previouslyPaidInterestInSchedule, loanId, schedule.getId());
        double remainingInterest = schedule.getInterest() - previouslyPaidInterestInSchedule;

        log.info("remainingInterest : {} for loanId : {} and scheduleId : {}", remainingInterest, loanId, schedule.getId());
        double interestAdjusted = Math.min(balance, remainingInterest);
        log.info("settledInterest : {} for loanId : {} and scheduleId : {}", interestAdjusted, loanId, schedule.getId());

        schedule.setPaidInterest(previouslyPaidInterestInSchedule + interestAdjusted);
        balance -= interestAdjusted;

        return PaymentCalculation.builder()
                .received(interestAdjusted + balance)
                .used(interestAdjusted)
                .balance(balance)
                .interestSettled(interestAdjusted)
                .build();

    }

    private PaymentCalculation settleEdiSchedulePrinciple(Long loanId, LendingEDIScheduleLendingCommon schedule, double balance) {
        log.info("remainingAmountToSettle : {} for loanId : {} and scheduleId : {}", balance, loanId, schedule.getId());
        double previouslyPaidPrincipleInSchedule = Objects.nonNull(schedule.getPaidPrinciple()) ? schedule.getPaidPrinciple() : 0;

        log.info("paidPrincipleInSchedule : {} for loanId : {} and scheduleId : {}", previouslyPaidPrincipleInSchedule, loanId, schedule.getId());
        double remainingPrinciple = schedule.getPrinciple() - previouslyPaidPrincipleInSchedule;

        log.info("remainingPrinciple : {} for loanId : {} and scheduleId : {}", remainingPrinciple, loanId, schedule.getId());
        double principalAdjusted = Math.min(balance, remainingPrinciple);
        log.info("settledPrinciple : {} for loanId : {} and scheduleId : {}", principalAdjusted, loanId, schedule.getId());

        schedule.setPaidPrinciple(previouslyPaidPrincipleInSchedule + principalAdjusted);
        balance -= principalAdjusted;

        return PaymentCalculation.builder()
                .received(principalAdjusted + balance)
                .used(principalAdjusted)
                .balance(balance)
                .principleSettled(principalAdjusted)
                .build();
    }

}
