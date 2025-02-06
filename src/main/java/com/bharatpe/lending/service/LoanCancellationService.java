package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.dao.LendingRefundAuditDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.entity.LendingRefundAudit;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.exceptions.InvalidDataException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;


@Service
@Slf4j
public class LoanCancellationService {
    private static final String LOAN_CANCELLED = "CANCELLED";
    private static final String DESCRIPTION ="LOAN_CANCELLED";
    private static final String TRANSFER_TYPE ="LMS_CANCELLED";
    private static final String TRANSACTION_TYPE = "EDI";
    private static final Double PENALTY = 0D;
    private static final Double OTHER_CHARGES= 0D;
    private static final Double ZERO = 0D;
    private static final String SOURCE = "LMS-CANCEL";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String CLOSED_STATUS = "CLOSED";

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;
    @Autowired
    LendingApplicationDao lendingApplicationDao;
    @Autowired
    LendingLedgerDao lendingLedgerDao;
    @Autowired
    LendingRefundAuditDao lendingRefundAuditDao;
    @Autowired
    DateTimeUtil dateTimeUtil;
    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;

    @Transactional
    public void cancelLoan(String externalLoanId, Long lmsBulkExportId) throws Exception {
        try{
            LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(externalLoanId);
            if(ObjectUtils.isEmpty(lendingApplication)){
                throw new InvalidDataException("Loan application not found in lending application table !");
            }
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(lendingApplication.getId());
            String status = lendingPaymentSchedule.getStatus();
            if(!ACTIVE_STATUS.equals(status)){
                log.error("LoanCancellationService -> cancelLoan ->  Loan cancellation failed for loan id - {} as the status is {}", lendingPaymentSchedule.getId(), status);
                throw new InvalidDataException("Loan application status is not as expected !");
            }
            updateLendingLedgerAndRefundAudit(lmsBulkExportId, lendingPaymentSchedule);
            updateLendingCollectionExcess(lendingPaymentSchedule, lmsBulkExportId);
            updateLendingPaymentSchedule(lendingPaymentSchedule);
        } catch (Exception e) {
            log.error("LoanCancellationService -> cancelLoan ->  Loan cancellation failed for externalLoanId - {} | lmsBulkExportId - {} | exception - {}",externalLoanId, lmsBulkExportId, e.getMessage());
            throw e;
        }
    }

    private void updateLendingCollectionExcess(LendingPaymentSchedule lendingPaymentSchedule, Long lmsBulkExportId) {
        long loanId = lendingPaymentSchedule.getId();
        long merchantId = lendingPaymentSchedule.getMerchantId();
        List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdOrderByIdAsc(merchantId,loanId);
        if(lendingCollectionExcessList.isEmpty()){
            log.info("LoanCancellationService -> updateLendingCollectionExcess -> No entries in lending collection excess for loan id {}, merchant id {} ", loanId, merchantId);
            return;
        }
        for(LendingCollectionExcess lce : lendingCollectionExcessList){
            if(ACTIVE_STATUS.equals(lce.getStatus())){
                log.info("LoanCancellationService -> updateLendingCollectionExcess -> Creating refund in lending refund audit for loan id {}, merchant id {} ", loanId, merchantId);
                createLoanRefund(lendingPaymentSchedule, lce.getAmount(), lmsBulkExportId, lce.getTerminalOrderId(), lce.getMode());
                lce.setStatus(CLOSED_STATUS);
                lendingCollectionExcessDao.save(lce);
            }
        }
    }

    private void updateLendingPaymentSchedule(LendingPaymentSchedule lendingPaymentSchedule) throws Exception{
        lendingPaymentSchedule.setStatus(LOAN_CANCELLED);
        lendingPaymentSchedule.setEdiRemainingCount(0);
        lendingPaymentSchedule.setPaidAmount(ZERO);
        lendingPaymentSchedule.setPaidPrinciple(ZERO);
        lendingPaymentSchedule.setPaidInterest(ZERO);
        lendingPaymentSchedule.setDueAmount(ZERO);
        lendingPaymentSchedule.setDuePrinciple(ZERO);
        lendingPaymentSchedule.setDueInterest(ZERO);
        lendingPaymentSchedule.setDuePenalty(ZERO);
        lendingPaymentSchedule.setPaidPenalty(ZERO);
        lendingPaymentScheduleDao.save(lendingPaymentSchedule);
        log.info("LoanCancellationService -> updateLendingPaymentSchedule -> updated lending payment schedule successfully for loan id - {}",lendingPaymentSchedule.getId());
    }

    private void updateLendingLedgerAndRefundAudit(Long lmsBulkExportId, LendingPaymentSchedule lendingPaymentSchedule) {
        Long loan_id = lendingPaymentSchedule.getId();
        Double totalPayableAmount = lendingPaymentSchedule.getTotalPayableAmount();
        Double totalLoanAmount = lendingPaymentSchedule.getLoanAmount();
        Double totalInterest = lendingPaymentSchedule.getInterest();
        Long merchantId = lendingPaymentSchedule.getMerchantId();
        Map<String, Object> negativeEdi= lendingLedgerDao.totalNegativeEdiAmount(loan_id);
        Map<String, Object>  positiveEdi = lendingLedgerDao.totalPositiveEdiAmount(loan_id);
        Double newNegativeEdiAmount = totalPayableAmount-(-1*(((BigDecimal) negativeEdi.get("amount")).doubleValue()));
        log.info("LoanCancellationService -> updateLendingLedgerAndRefundAudit -> newNegativeEdiAmount - {}", newNegativeEdiAmount);
        Double newPositiveEdiAmount = totalPayableAmount-(((BigDecimal) positiveEdi.get("amount")).doubleValue());
        log.info("LoanCancellationService -> updateLendingLedgerAndRefundAudit -> newPositiveEdiAmount - {}", newPositiveEdiAmount);
        Double newNegativeEdiPrinciple = totalLoanAmount-(-1*(((BigDecimal) negativeEdi.get("principle")).doubleValue()));
        log.info("LoanCancellationService -> updateLendingLedgerAndRefundAudit -> newNegativeEdiPrinciple - {}", newNegativeEdiPrinciple);
        Double newPositiveEdiPrinciple = totalLoanAmount-(((BigDecimal) positiveEdi.get("principle")).doubleValue());
        log.info("LoanCancellationService -> updateLendingLedgerAndRefundAudit -> newPositiveEdiPrinciple - {}", newPositiveEdiPrinciple);
        Double newNegativeEdiInterest = totalInterest-(-1*(((BigDecimal) negativeEdi.get("interest")).doubleValue()));
        log.info("LoanCancellationService -> updateLendingLedgerAndRefundAudit -> newNegativeEdiInterest - {}", newNegativeEdiInterest);
        Double newPositiveEdiInterest = totalInterest-(((BigDecimal) positiveEdi.get("interest")).doubleValue());
        log.info("LoanCancellationService -> updateLendingLedgerAndRefundAudit -> newPositiveEdiInterest - {}", newPositiveEdiInterest);

        if((((BigDecimal) positiveEdi.get("amount")).doubleValue())>0){
            log.info("LoanCancellationService -> updateLendingLedgerAndRefundAudit -> Creating entries in refund audit for loan id {}", loan_id);
            List<LendingLedger> positiveEntries = lendingLedgerDao.positiveEdiEntries(loan_id);
            for(LendingLedger entry : positiveEntries){
                String terminalOrderId = entry.getTerminalOrderId();
                createLoanRefund(lendingPaymentSchedule, entry.getAmount(), lmsBulkExportId, (ObjectUtils.isEmpty(terminalOrderId) ? entry.getId().toString() : terminalOrderId), entry.getAdjustmentMode());
            }
        }
        createLendingLedger(lendingPaymentSchedule,-1*newNegativeEdiAmount,-1*newNegativeEdiPrinciple,-1*newNegativeEdiInterest,merchantId);
        createLendingLedger(lendingPaymentSchedule,newPositiveEdiAmount,newPositiveEdiPrinciple,newPositiveEdiInterest,merchantId );
    }

    private void createLoanRefund(LendingPaymentSchedule loan, double amount, Long lmsBulkExportId, String terminalOrderId, String mode) {
        LendingRefundAudit lendingRefundAudit = new LendingRefundAudit();
        lendingRefundAudit.setLoanId(loan.getId());
        lendingRefundAudit.setMerchantId(loan.getMerchantId());
        lendingRefundAudit.setMode(mode);
        lendingRefundAudit.setBankRefNo(terminalOrderId);
        lendingRefundAudit.setRefundAmount(amount);
        lendingRefundAudit.setSource(SOURCE);
        lendingRefundAudit.setLmsBulkExportRef(String.valueOf(lmsBulkExportId));
        lendingRefundAudit.setOrderAmount(amount);
        lendingRefundAuditDao.save(lendingRefundAudit);
        log.info("LoanCancellationService -> createLoanRefund -> entry added to lending refund audit successfully for loan id - {}, amount - {}",loan.getId(),amount);
    }

    private void createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Double amount, Double principle,
                                              Double interest, Long merchantId) {
        if(amount == 0) {
            return;
        }

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
        lendingLedger.setDate(dateTimeUtil.getCurrentDate());
        lendingLedger.setAmount(amount);
        lendingLedger.setInterest(interest);
        lendingLedger.setOtherCharges(OTHER_CHARGES);
        lendingLedger.setPenalty(PENALTY);
        lendingLedger.setPrinciple(principle);
        lendingLedger.setTransferType(TRANSFER_TYPE);
        lendingLedger.setDescription(DESCRIPTION);
        lendingLedger.setTxnType(TRANSACTION_TYPE);
        lendingLedger.setMerchantId(merchantId);
        lendingLedgerDao.save(lendingLedger);
        log.info("LoanCancellationService -> createLendingLedger -> entry added to lending ledger successfullyfor loan id - {}",lendingPaymentSchedule.getId());
    }
}
