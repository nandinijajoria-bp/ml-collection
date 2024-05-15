package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;
import com.bharatpe.lending.collection.core.service.AdjustLoanBalanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;


@Service
@Slf4j
public class AdjustLoanBalanceByNPAServiceImpl implements AdjustLoanBalanceService {


    @Override
    public PaymentCalculation adjustLoanBalance(LendingPaymentSchedule activeLoan, double amount) {
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
    // __________________________________     Calculation   ____________________________________________________________
    //==================================================================================================================

    //TODO: add null check when updating paidIntrest,P,C
    private PaymentCalculation adjustInterest(LendingPaymentSchedule activeLoan, double amount) {
        double interestPaid = 0;
        log.info("LoanAdjustment#{} adjustInterest is started for loan {} with amount :{}",activeLoan.getId(), activeLoan, amount);
        if (amount > 0D && activeLoan.getDueInterest() != null && activeLoan.getDueInterest() > 0D) {
            interestPaid = Math.min(activeLoan.getDueInterest(), amount);
            activeLoan.setDueInterest(activeLoan.getDueInterest() - interestPaid);
            activeLoan.setPaidInterest((activeLoan.getPaidInterest() != null ? activeLoan.getPaidInterest() : 0) + interestPaid);

            activeLoan.setDueAmount(activeLoan.getDueAmount() - interestPaid);
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + interestPaid);
            log.info("LoanAdjustment#{} Adjusted due interest of amount:{} for loan:{}",activeLoan.getId(), interestPaid, activeLoan);
        }
        log.info("LoanAdjustment#{} adjustInterest is completed for loan {} with adjustment  received :{} adjusted :{} balance {}",activeLoan.getId(), activeLoan, amount, interestPaid, amount - interestPaid);
        return PaymentCalculation.builder()
                .received(amount)
                .used(interestPaid)
                .balance(amount - interestPaid)
                .build();
    }


    private PaymentCalculation adjustPrinciple(LendingPaymentSchedule activeLoan, double amount) {
        double principalPaid = 0;
        log.info("LoanAdjustment#{} adjustPrinciple is started for loan {} with amount :{}",activeLoan.getId(), activeLoan, amount);
        if (amount > 0D && activeLoan.getDuePrinciple() != null && activeLoan.getDuePrinciple() > 0D) {
            principalPaid = Math.min(activeLoan.getDuePrinciple(), amount);

            activeLoan.setDuePrinciple(activeLoan.getDuePrinciple() - principalPaid);
            activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + principalPaid);

            activeLoan.setDueAmount(activeLoan.getDueAmount() - principalPaid);
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + principalPaid);
            log.info("LoanAdjustment#{} adjustPrinciple of amount:{} for loan:{}",activeLoan.getId(), principalPaid, activeLoan);
        }
        log.info("LoanAdjustment#{} adjustPrinciple is completed for loan {} with adjustment  received :{} adjusted :{} balance {}",activeLoan.getId(), activeLoan, amount, principalPaid, amount - principalPaid);
        return PaymentCalculation.builder()
                .received(amount)
                .used(principalPaid)
                .balance(amount - principalPaid)
                .build();
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
            activeLoan.setPaidOtherCharges(activeLoan.getPaidOtherCharges() + chargesPaid);

            activeLoan.setDueAmount(activeLoan.getDueAmount() - chargesPaid);
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + chargesPaid);
            log.info("LoanAdjustment#{} adjustOtherCharges of amount:{} for loan:{}",activeLoan.getId(), chargesPaid, activeLoan);
        }

        log.info("LoanAdjustment#{} adjustOtherCharges is completed for loan {} with adjustment  received :{} adjusted :{} balance {}",activeLoan.getId(), activeLoan, amount, chargesPaid, amount - chargesPaid);
        return PaymentCalculation.builder()
                .received(amount)
                .used(chargesPaid)
                .balance(amount - chargesPaid)
                .build();
    }


}
