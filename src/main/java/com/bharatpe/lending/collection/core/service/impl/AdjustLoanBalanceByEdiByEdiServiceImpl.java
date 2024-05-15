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

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


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
    public PaymentCalculation adjustEdiSchedule(LendingPaymentSchedule loan, double amount, boolean adjustPrincipleFirst) {
        PaymentCalculation settleLoanPaymentDTO = settleEDIPrincipleAndInterest(loan, amount, adjustPrincipleFirst);

        PaymentCalculation penalty = adjustPenalty(loan, settleLoanPaymentDTO.getBalance());
        settleLoanPaymentDTO.setBalance(settleLoanPaymentDTO.getBalance() - penalty.getUsed());

        PaymentCalculation charges = adjustOtherCharges(loan, settleLoanPaymentDTO.getBalance());
        settleLoanPaymentDTO.setBalance(settleLoanPaymentDTO.getBalance() - charges.getUsed());

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

    private PaymentCalculation settleEDIPrincipleAndInterest(LendingPaymentSchedule loan, double amount, Boolean settleAllPrinciple) {
        PaymentCalculation settleLoanPaymentDTO = settleLoanDuePayment(loan.getId(), loan.getEdiCount(), loan.getEdiRemainingCount(), settleAllPrinciple, amount);
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
                .build();
    }

    public PaymentCalculation settleLoanDuePayment(Long loanId, Integer ediCount, Integer ediRemainingCount, Boolean settleAllPrinciple, Double amountBeingPaid) {
        Integer ediCreatedTillDate = ediCount - ediRemainingCount;
        log.info("ediCreatedTillDate : {} for loanId : {}", ediCreatedTillDate, loanId);

        Double paidPrinciple = 0d;
        Double paidInterest = 0d;
        Double remainingAmountToSettle = amountBeingPaid;
        boolean settleAllPrincipleFirst = false;

        List<LendingEDIScheduleLendingCommon> unpaidSchedulesForALoan = lendingEDIScheduleLendingCommonDao.findUnpaidSchedulesForALoan(loanId, ediCreatedTillDate);
        Optional<LendingEDIScheduleLendingCommon> firstPendingSchedule = unpaidSchedulesForALoan.stream().findFirst();

        if (firstPendingSchedule.isPresent()) {
            log.info("firstPendingSchedule for loanId : {} with id : {} is {}", loanId, firstPendingSchedule.get().getId(), firstPendingSchedule);
            final long dateDiffInDays = LoanPaymentUtil.getDateDiffInDays(firstPendingSchedule.get().getDate(), new Date());
            log.info("dateDiffInDays between first pending schedule and now for loanId : {} with id : {} is {}", loanId, firstPendingSchedule.get().getId(), dateDiffInDays);
            if (dateDiffInDays > 90 || (Objects.nonNull(settleAllPrinciple) && settleAllPrinciple) )
                settleAllPrincipleFirst = true;
        }

        PaymentCalculation paymentCalculation;
        if (settleAllPrincipleFirst) {
            paymentCalculation =  settleAllPrincipleFirstThenAllInterest(loanId, (Slice<LendingEDIScheduleLendingCommon>) unpaidSchedulesForALoan, remainingAmountToSettle);
        } else {
            paymentCalculation =  settleEdiByEdi(loanId, (Slice<LendingEDIScheduleLendingCommon>) unpaidSchedulesForALoan, remainingAmountToSettle);
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
                .build();
    }


    public PaymentCalculation settlePreClosureLoanPayment(Long loanId, Integer ediCount, Integer ediRemainingCount, Boolean settleAllPrinciple, Double amountBeingPaid) {

        // settle all due amount first
        final PaymentCalculation settleLoanPaymentDTO = settleLoanDuePayment(loanId, ediCount, ediRemainingCount, settleAllPrinciple, amountBeingPaid);

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
            PaymentCalculation principalAdjusted =  settleAllPrinciple(loanId, unpaidSchedulesForALoan, remainingAmountToSettle);
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


    public PaymentCalculation settleLoanPayment(LendingPaymentSchedule activeLoan, Boolean settleAllPrinciple, Double amountBeingPaid) {

        double ediCanBePaid = getHowManyCanBePaid(amountBeingPaid, activeLoan.getEdiAmount());
        // +2 is because  ediCanBePaid = 3.8 ... 3 complete and 0.8 is partially adjusted
        // but let say in some previous iteration loan was paid 0.4  so  0.8 + 0.4 = 1.2 (edi can be adjust in addition to 3 complete)
        int totalEdiAdjustment = (int) ediCanBePaid + 2;
        long loanId = activeLoan.getId();
        int ediCount = (Objects.nonNull(activeLoan.getEdiCount())) ? activeLoan.getEdiCount() : 0;
        int ediRemainingCount = (Objects.nonNull(activeLoan.getEdiRemainingCount())) ? activeLoan.getEdiRemainingCount() : 0;

        Integer ediCreatedTillDate = ediCount - ediRemainingCount;
        log.info("ediCreatedTillDate : {} for loanId : {}", ediCreatedTillDate, loanId);

        Double paidPrinciple = 0d;
        Double paidInterest = 0d;
        Double remainingAmountToSettle = amountBeingPaid;
        boolean settleAllPrincipleFirst = Objects.nonNull(settleAllPrinciple) ? settleAllPrinciple : false;

        int pageCount = 0;
        boolean continueAmountDeduction = true;

        int pageSize = Math.min(PAGE_SIZE, totalEdiAdjustment);

        while(continueAmountDeduction) {
            Pageable pageable = PageRequest.of(pageCount, pageSize);
            Slice<LendingEDIScheduleLendingCommon> unpaidSchedulesForALoan = lendingEDIScheduleLendingCommonDao.findAllUnpaidSchedulesForALoan(loanId, pageable);
            PaymentCalculation paymentCalculation;
            if (settleAllPrincipleFirst) {
                paymentCalculation =  settleAllPrincipleFirstThenAllInterest(loanId, unpaidSchedulesForALoan, remainingAmountToSettle);
            } else {
                paymentCalculation =  settleEdiByEdi(loanId, unpaidSchedulesForALoan, remainingAmountToSettle);
            }

            remainingAmountToSettle = paymentCalculation.getBalance();
            paidPrinciple += paymentCalculation.getPrincipleSettled();
            paidInterest += paymentCalculation.getInterestSettled();

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

    private PaymentCalculation settleEdiByEdi(Long loanId, Slice<LendingEDIScheduleLendingCommon> unpaidSchedulesForALoan, Double amount) {
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

    private PaymentCalculation settleAllPrincipleFirstThenAllInterest(Long loanId, Slice<LendingEDIScheduleLendingCommon> unpaidSchedulesForALoan, double amount) {
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


    private PaymentCalculation settleAllPrinciple(Long loanId, Slice<LendingEDIScheduleLendingCommon> unpaidSchedulesForALoan, double amount) {
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

    private double getHowManyCanBePaid(Double amountBeingPaid, Double ediAmount) {
        return amountBeingPaid/ediAmount;
    }

}
