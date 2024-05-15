package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;
import com.bharatpe.lending.collection.core.service.AdjustLoanBalanceService;
import com.bharatpe.lending.common.dto.SettleLoanPaymentDTO;
import com.bharatpe.lending.common.service.PaymentSettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;


@Service
@Slf4j
public class AdjustLoanBalanceByEdiByEdiServiceImpl implements AdjustLoanBalanceService {

    @Autowired
    PaymentSettlementService paymentSettlementService;

    @Override
    public PaymentCalculation adjustLoanBalance(LendingPaymentSchedule loan, double amount) {
        return adjustEdiSchedule(loan, amount, loan.getSettleAllPrinciple());
    }

    //==================================================================================================================
    // __________________________________     Calculation   ____________________________________________________________
    //==================================================================================================================
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
