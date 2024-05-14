package com.bharatpe.lending.collection.service.impl;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.service.LoanClosureService;
import com.bharatpe.lending.collection.service.LoanStatusService;
import com.bharatpe.lending.collection.utils.Utility;
import com.bharatpe.lending.collection.dto.LoanClosureDTO;
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
    Utility collectionUtils;
    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;
    @Autowired
    LoanClosureService loanClosureService;


    @Override
    public void processLoanClosure(LoanClosureDTO loanClosureDTO) {
        log.info("received request for closing the loanId {} and loanDetails {} ",loanClosureDTO.getActiveLoan().getId(),loanClosureDTO.getActiveLoan());
       if(PaymentType.FORECLOSURE.name().equalsIgnoreCase(loanClosureDTO.getPaymentType()))
           loanClosureService.foreClosureLoan(loanClosureDTO.getActiveLoan(),loanClosureDTO.getLendingLedger(),loanClosureDTO.getOrderId());
        loanClosureService.closeLoanAndUpdateLender(loanClosureDTO.getActiveLoan(),loanClosureDTO.getLendingLedger(),loanClosureDTO.getOrderId());
    }



    @Override
    public void waiverSettleLoan(LendingPaymentSchedule activeLoan, Double amount, String bankRefNo, String source, String terminalOrderId, Double excessCollectionBalance, List<LendingCollectionExcess> lendingCollectionExcessList) {
        log.info("inside settle loan for loanId {}",activeLoan.getId());
        Double dueAmount = amount - activeLoan.getDueInterest() + activeLoan.getOtherCharges() + activeLoan.getInterest() - activeLoan.getPaidInterest();
        collectionUtils.createLendingLedger(activeLoan, -1 * (amount + excessCollectionBalance), -1 * (amount + excessCollectionBalance),
                0d, "PREPAYMENT", source, "SETTLED", terminalOrderId, 0d);
        activeLoan.setStatus("SETTLED");
        activeLoan.setDueAmount(dueAmount);

//        activeLoan.setClosingDate(new Date());
        //settle excess collection balance only
        if(excessCollectionBalance > 0D) {
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + excessCollectionBalance);
            activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + excessCollectionBalance);

            log.info("Adjusting excess collection for loan in ledger : {}, amount : {}", activeLoan.getId(), excessCollectionBalance);
            collectionUtils.createLendingLedgerForExcessCollectionOnForeclosure(activeLoan, lendingCollectionExcessList);
            collectionUtils.settleExcessCollectionBalance(activeLoan.getId(), lendingCollectionExcessList);

        }
        LendingLedger lendingLedger = collectionUtils.createLendingLedger(
                activeLoan,  amount + excessCollectionBalance,
                amount + excessCollectionBalance, 0d,  getDescription(bankRefNo, true,false), source,
                "SETTLED", terminalOrderId, 0d);

        lendingPaymentScheduleDao.save(activeLoan);
    }
    private String getDescription(String bankRRN, boolean preclosure, boolean preclosureWithCharges) {
        String preclosureDescription = (preclosureWithCharges) ? "PRECLOSER_WITH_CHARGES_UPI : " : "PRECLOSER_UPI : ";
        return preclosure ? preclosureDescription + bankRRN : "PREPAYMENT : " + bankRRN;
    }

}
