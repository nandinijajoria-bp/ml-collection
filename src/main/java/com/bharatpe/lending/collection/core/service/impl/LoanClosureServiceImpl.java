package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.service.LoanClosurePostingService;
import com.bharatpe.lending.collection.core.service.LoanClosureService;
import com.bharatpe.lending.common.dao.LoanForeClosureChargesDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.Lender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;

@Service
@Slf4j
public class LoanClosureServiceImpl implements LoanClosureService {

    @Autowired
    LoanClosurePostingService loanClosurePostingService;
    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;
    @Autowired
    LoanForeClosureChargesDao loanForeClosureChargesDao;

    @Override
    public void closeLoanAndUpdateLender(LendingPaymentSchedule loan, LendingLedger lendingLedger, Long orderId) {
        log.info("inside close loan and update lender for loanId {} ",loan.getId());
        loan = closeLoanAndUpdateStatus(loan);
        log.info("posting closure status to lender for loanId {} and loan-details {}",loan.getId(),loan);
        postClosureStatusToLender( loan,  lendingLedger,  orderId);
    }



    @Override
    public void foreClosureLoan(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger, Long orderId) {
        //TODO : need confrimation from buvnesh to check this logic
        log.info("inside foreclose loanid {} and loanDetails {}",activeLoan.getId(),activeLoan);
       Double totalPayableAmount = activeLoan.getLoanAmount() + activeLoan.getDueInterest() + activeLoan.getPaidInterest() + activeLoan.getDuePenalty() + activeLoan.getOtherCharges();
       log.info("updating total payable amount {} for loanId {} and loanDetails {} and  ",totalPayableAmount,activeLoan.getId(),activeLoan);
        activeLoan.setTotalPayableAmount(totalPayableAmount);
        if(activeLoan.getPaidAmount() >= totalPayableAmount) closeLoanAndUpdateLender(activeLoan,lendingLedger,orderId);
        log.info("paid-amount {} is less than total-payable amount {} for loanId {} and during processing loan closure",activeLoan.getPaidAmount() , activeLoan.getTotalPayableAmount() , activeLoan.getId());
    }


    private void postClosureStatusToLender(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger, Long orderId) {

        if (activeLoan.getNbfc().equalsIgnoreCase(Lender.ABFL.name())) {
            loanClosurePostingService.sendForeclosureEvent(activeLoan.getApplicationId(), activeLoan.getMobile(), lendingLedger);
        } else if (activeLoan.getNbfc().equalsIgnoreCase(Lender.PIRAMAL.name())) {
            loanClosurePostingService.postForeclosureReceiptPiramal(activeLoan, lendingLedger);
        } else if (Arrays.asList("USFB", "CAPRI").contains(activeLoan.getNbfc())) {
            loanClosurePostingService.postForeclosureReceipt(activeLoan, lendingLedger);
        } else if (Lender.TRILLIONLOANS.name().equalsIgnoreCase(activeLoan.getNbfc())) {
            loanClosurePostingService.sendForeclosureEventTrillionLoans(activeLoan.getApplicationId(), lendingLedger, orderId);
        } else if (Lender.LIQUILOANS_P2P.name().equalsIgnoreCase(activeLoan.getNbfc())
                || Lender.LIQUILOANS_P2P_OF.name().equalsIgnoreCase(activeLoan.getNbfc())
                || Lender.LIQUILOANS_NBFC.name().equalsIgnoreCase(activeLoan.getNbfc())) {
            loanClosurePostingService.sendForeclosureChargesEventLiquiLoans(activeLoan.getApplicationId(), activeLoan.getId(), lendingLedger.getId(), activeLoan.getNbfc(), orderId);
        }

    }

    private LendingPaymentSchedule closeLoanAndUpdateStatus(LendingPaymentSchedule loan) {
        log.info("update status closed for loanId {} and loanDetails {}",loan.getId(),loan);
        loan.setStatus("CLOSED");
        loan.setDueAmount(0.0);
        loan.setDueInterest(0.0);
        loan.setDuePrinciple(0.0);
        loan.setDueOtherCharges(0.0);
        loan.setDuePenalty(0.0);
        loan.setEdiRemainingCount(0);
        loan.setClosingDate(new Date());
        //TODO : do we need to check next edi_date also
        lendingPaymentScheduleDao.save(loan);
        return loan;
    }
}
