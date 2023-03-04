package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingCollectionAuditDao;
import com.bharatpe.lending.common.entity.LendingCollectionAudit;
import com.bharatpe.lending.common.query.dao.LendingApplicationDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.persistence.Column;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class LendingCollectionAuditService {
    @Autowired
    LendingApplicationDaoSlave lendingApplicationDaoSlave;

    @Autowired
    LendingCollectionAuditDao lendingCollectionAuditDao;

    public void sendCollectionAudit(LendingLedger lendingLedger){
        try {
            if(ObjectUtils.isEmpty(lendingLedger) || lendingLedger.getAmount() <= 0)return;
            Optional<LendingApplicationSlave> lendingApplicationSlave = lendingApplicationDaoSlave.findById(lendingLedger.getLendingPaymentSchedule().getApplicationId());
            if (!lendingApplicationSlave.isPresent()) {
                return;
            }
            LendingCollectionAudit lendingCollectionAudit = LendingCollectionAudit.builder()
                    .merchantId(lendingLedger.getMerchantId())
                    .merchantStoreId(lendingLedger.getMerchantStoreId())
                    .loanId(lendingLedger.getLendingPaymentSchedule().getId())
                    .ledgerId(lendingLedger.getId())
                    .applicationId(lendingLedger.getLendingPaymentSchedule().getApplicationId())
                    .bpLoanId(lendingApplicationSlave.get().getExternalLoanId())
                    .nbfcId(lendingApplicationSlave.get().getNbfcId())
                    .settlementId(lendingLedger.getSettlementId())
                    .txnType(lendingLedger.getTxnType())
                    .transferType(lendingLedger.getTransferType())
                    .transferDate(lendingLedger.getDate())
                    .status("PENDING")
                    .amount(lendingLedger.getAmount())
                    .description(lendingLedger.getDescription())
                    .principle(lendingLedger.getPrinciple())
                    .interest(lendingLedger.getInterest())
                    .otherCharges(lendingLedger.getOtherCharges())
                    .penalty(lendingLedger.getPenalty())
                    .adjustmentMode(lendingLedger.getAdjustmentMode())
                    .transferType(lendingLedger.getTransferType())
                    .terminalOrderId(lendingLedger.getTerminalOrderId())
                    .lender(lendingLedger.getLendingPaymentSchedule().getNbfc())
                    .loanStatus(lendingLedger.getLendingPaymentSchedule().getStatus())
                    .loanClosingDate(lendingLedger.getLendingPaymentSchedule().getClosingDate())
                    .build();
            lendingCollectionAuditDao.save(lendingCollectionAudit);
        } catch (Exception e) {
            log.error("Error in creating collection audit for ledger id {}", lendingLedger.getId());
        }
    }

    public void sendCollectionAudit(LendingLedger lendingLedger, LendingPaymentSchedule lendingPaymentSchedule){
        try {
            if(ObjectUtils.isEmpty(lendingLedger) || lendingLedger.getAmount() <= 0)return;
            Optional<LendingApplicationSlave> lendingApplicationSlave = lendingApplicationDaoSlave.findById(lendingLedger.getLendingPaymentSchedule().getApplicationId());
            if (!lendingApplicationSlave.isPresent()) {
                return;
            }
            LendingCollectionAudit lendingCollectionAudit = LendingCollectionAudit.builder()
                    .merchantId(lendingLedger.getMerchantId())
                    .merchantStoreId(lendingLedger.getMerchantStoreId())
                    .loanId(lendingPaymentSchedule.getId())
                    .ledgerId(lendingLedger.getId())
                    .applicationId(lendingPaymentSchedule.getApplicationId())
                    .bpLoanId(lendingApplicationSlave.get().getExternalLoanId())
                    .nbfcId(lendingApplicationSlave.get().getNbfcId())
                    .settlementId(lendingLedger.getSettlementId())
                    .txnType(lendingLedger.getTxnType())
                    .transferType(lendingLedger.getTransferType())
                    .transferDate(lendingLedger.getDate())
                    .status("PENDING")
                    .amount(lendingLedger.getAmount())
                    .description(lendingLedger.getDescription())
                    .principle(lendingLedger.getPrinciple())
                    .interest(lendingLedger.getInterest())
                    .otherCharges(lendingLedger.getOtherCharges())
                    .penalty(lendingLedger.getPenalty())
                    .adjustmentMode(lendingLedger.getAdjustmentMode())
                    .transferType(lendingLedger.getTransferType())
                    .terminalOrderId(lendingLedger.getTerminalOrderId())
                    .lender(lendingPaymentSchedule.getNbfc())
                    .loanStatus(lendingPaymentSchedule.getStatus())
                    .loanClosingDate(lendingPaymentSchedule.getClosingDate())
                    .build();
            lendingCollectionAuditDao.save(lendingCollectionAudit);
        } catch (Exception e) {
            log.error("Error in creating collection audit for ledger id {}", lendingLedger.getId());
        }
    }
}
