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
import com.bharatpe.lending.enums.PaymentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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


    @Override
    public void processLoanClosure(LoanClosureDTO loanClosureDTO) {
        log.info("received request for closing the loanId {} and loanDetails {} ",loanClosureDTO.getActiveLoan().getId(),loanClosureDTO.getActiveLoan());
        loanClosureService.closeLoanAndUpdateLender(loanClosureDTO.getActiveLoan(),loanClosureDTO.getLendingLedger(),loanClosureDTO.getOrderId());
    }



    @Override
    public void waiverSettleLoan(LendingPaymentSchedule activeLoan, Double amount, String bankRefNo, String source, String terminalOrderId) {
        log.info("inside settle loan for loanId {}",activeLoan.getId());

        List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(activeLoan.getMerchantId(), activeLoan.getId(), "ACTIVE");
        Double excessCollectionBalance = 0D;
        for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
            if(lendingCollectionExcess.getAmount() > 0){
                excessCollectionBalance += lendingCollectionExcess.getAmount();
            }
        }
        Double dueAmount = amount - activeLoan.getDueInterest() + activeLoan.getOtherCharges() + activeLoan.getInterest() - activeLoan.getPaidInterest();
        ledgerAdjustmentService.createLendingLedger(activeLoan, -1 * (amount + excessCollectionBalance), -1 * (amount + excessCollectionBalance),
                0d, "PREPAYMENT", source, "SETTLED", terminalOrderId, 0d, 0d);
        activeLoan.setStatus("SETTLED");
        activeLoan.setDueAmount(dueAmount);

//        activeLoan.setClosingDate(new Date());
        //settle excess collection balance only
        if(excessCollectionBalance > 0D) {
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + excessCollectionBalance);
            activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + excessCollectionBalance);

            log.info("Adjusting excess collection for loan in ledger : {}, amount : {}", activeLoan.getId(), excessCollectionBalance);
            ledgerAdjustmentService.createLendingLedgerForExcessCollectionOnForeclosure(activeLoan, lendingCollectionExcessList);
            ledgerAdjustmentService.settleExcessCollectionBalance(activeLoan.getId(), lendingCollectionExcessList);

        }
        LendingLedger lendingLedger = ledgerAdjustmentService.createLendingLedger(
                activeLoan,  amount + excessCollectionBalance,
                amount + excessCollectionBalance, 0d,  getDescription(bankRefNo, true,false), source,
                "SETTLED", terminalOrderId, 0d, 0d);

        lendingPaymentScheduleDao.save(activeLoan);
    }
    private String getDescription(String bankRRN, boolean preclosure, boolean preclosureWithCharges) {
        String preclosureDescription = (preclosureWithCharges) ? "PRECLOSER_WITH_CHARGES_UPI : " : "PRECLOSER_UPI : ";
        return preclosure ? preclosureDescription + bankRRN : "PREPAYMENT : " + bankRRN;
    }

}
