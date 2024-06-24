package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.service.LoanClosureService;
import com.bharatpe.lending.collection.core.service.LoanPaymentLedgerAdjustmentService;
import com.bharatpe.lending.collection.core.service.LoanStatusService;
import com.bharatpe.lending.collection.core.dto.internal.LoanClosureDTO;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;


@Service
@Slf4j
public class LoanStatusServiceImpl implements LoanStatusService {

    @Autowired
    LoanPaymentLedgerAdjustmentService ledgerAdjustmentService;
    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;
    @Autowired
    LoanClosureService loanClosureService;
    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;

    @Autowired
    LoanPaymentLedgerAdjustmentService loanPaymentLedgerAdjustmentService;

    @Override
    public void processLoanClosure(LoanClosureDTO loanClosureDTO) {
        log.info("received request for closing the loanId {} and loanDetails {} ",loanClosureDTO.getActiveLoan().getId(),loanClosureDTO.getActiveLoan());
        loanClosureService.closeLoanAndUpdateLender(loanClosureDTO.getActiveLoan(),loanClosureDTO.getLendingLedger(),loanClosureDTO.getOrderId());
    }



    @Override
    public void waiverSettlement(LendingPaymentSchedule activeLoan, Double amount, String bankRefNo, String source,
                                 String transferType, String terminalOrderId, Double excessCollectionBalance, List<LendingCollectionExcess> lendingCollectionExcessList) {

        loanPaymentLedgerAdjustmentService.createLendingLedger(activeLoan, -1 * (amount + excessCollectionBalance), -1 * (amount + excessCollectionBalance),
                0d, "PREPAYMENT", source, transferType, terminalOrderId, 0d,0d);
        activeLoan.setStatus("CLOSED");
        activeLoan.setClosingDate(new Date());
        //settle excess collection balance only
        if(excessCollectionBalance > 0D) {
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + excessCollectionBalance);
            activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + excessCollectionBalance);

            log.info("Adjusting excess collection for loan in ledger : {}, amount : {}", activeLoan.getId(), excessCollectionBalance);
            loanPaymentLedgerAdjustmentService.createLendingLedgerForExcessCollectionOnForeclosure(activeLoan, lendingCollectionExcessList);
            loanPaymentLedgerAdjustmentService.settleExcessCollectionBalance(activeLoan.getId(), lendingCollectionExcessList);

        }
        LendingLedger lendingLedger = loanPaymentLedgerAdjustmentService.createLendingLedger(
                activeLoan,  amount + excessCollectionBalance,
                amount + excessCollectionBalance, 0d,  getDescription(bankRefNo, true, false), source,
                transferType, terminalOrderId, 0d,0D);

        lendingPaymentScheduleDao.save(activeLoan);
    }
    private String getDescription(String bankRRN, boolean preclosure, boolean preclosureWithCharges) {
        String preclosureDescription = (preclosureWithCharges) ? "PRECLOSER_WITH_CHARGES_UPI : " : "PRECLOSER_UPI : ";
        return preclosure ? preclosureDescription + bankRRN : "PREPAYMENT : " + bankRRN;
    }

}
