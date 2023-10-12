package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.HightpvLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingCollectionAuditDao;
import com.bharatpe.lending.common.entity.HightpvLenderDetails;
import com.bharatpe.lending.common.entity.LendingCollectionAudit;
import com.bharatpe.lending.common.query.dao.LendingApplicationDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.bharatpe.lending.constant.LendingConstants.UPI;
import static com.bharatpe.lending.constant.LendingConstants.UPI_AUTOPAY_ADJUSTMENT_MODE;

@Slf4j
@Service
public class LendingCollectionAuditService {
    @Autowired
    LendingApplicationDaoSlave lendingApplicationDaoSlave;

    @Autowired
    LendingCollectionAuditDao lendingCollectionAuditDao;

    @Autowired
    HightpvLenderDetailsDao hightpvLenderDetailsDao;

    private String getAdjustmentMode(String adjustmentModeFromLedger) {
        if ((UPI_AUTOPAY_ADJUSTMENT_MODE).equalsIgnoreCase(adjustmentModeFromLedger))
            return UPI;

        return adjustmentModeFromLedger;
    }

    public void sendCollectionAudit(LendingLedger lendingLedger){
        try {
            if(ObjectUtils.isEmpty(lendingLedger) || lendingLedger.getAmount() <= 0 ||
                    (Objects.nonNull(lendingLedger.getAdjustmentMode()) && "EXCEPTION-WAIVER".equalsIgnoreCase(lendingLedger.getAdjustmentMode())))return;

            String bpLoanId = null;
            String nbfcId = null;

            if (!ObjectUtils.isEmpty(lendingLedger.getLendingPaymentSchedule().getApplicationId())) {
                Optional<LendingApplicationSlave> lendingApplicationSlave = lendingApplicationDaoSlave.findById(lendingLedger.getLendingPaymentSchedule().getApplicationId());
                if (lendingApplicationSlave.isPresent()) {
                    bpLoanId = lendingApplicationSlave.get().getExternalLoanId();
                    nbfcId = lendingApplicationSlave.get().getNbfcId();
                }
            }
            else{
                HightpvLenderDetails hightpvLenderDetails = hightpvLenderDetailsDao.findByLpsId(lendingLedger.getLendingPaymentSchedule().getId());
                if(!ObjectUtils.isEmpty(hightpvLenderDetails)){
                    log.info("fetching from hightpv lender details {}, {}", hightpvLenderDetails.getNbfcId(), hightpvLenderDetails.getExternalLoanId());
                    bpLoanId = hightpvLenderDetails.getExternalLoanId();
                    nbfcId = hightpvLenderDetails.getNbfcId();
                }
                else {
                    log.info("nbfc details not found for ledger {}", lendingLedger.getId());
                    return;
                }
            }
                LendingCollectionAudit lendingCollectionAudit = LendingCollectionAudit.builder()
                        .merchantId(lendingLedger.getMerchantId())
                        .merchantStoreId(lendingLedger.getMerchantStoreId())
                        .loanId(lendingLedger.getLendingPaymentSchedule().getId())
                        .ledgerId(lendingLedger.getId())
                        .applicationId(lendingLedger.getLendingPaymentSchedule().getApplicationId())
                        .bpLoanId(bpLoanId)
                        .nbfcId(nbfcId)
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
                        .adjustmentMode(getAdjustmentMode(lendingLedger.getAdjustmentMode()))
                        .transferType(lendingLedger.getTransferType())
                        .terminalOrderId(lendingLedger.getTerminalOrderId())
                        .lender(lendingLedger.getLendingPaymentSchedule().getNbfc())
                        .loanStatus(lendingLedger.getLendingPaymentSchedule().getStatus())
                        .loanClosingDate(lendingLedger.getLendingPaymentSchedule().getClosingDate())
                        .mobile(lendingLedger.getLendingPaymentSchedule().getMobile())
                        .build();
                lendingCollectionAuditDao.save(lendingCollectionAudit);
        } catch (Exception e) {
            log.error("Error in creating collection audit for ledger id {}, {}", lendingLedger.getId(), Arrays.asList(e.getStackTrace()));
        }
    }

    public void sendCollectionAudit(LendingLedger lendingLedger, LendingPaymentSchedule lendingPaymentSchedule){
        try {
            if(ObjectUtils.isEmpty(lendingLedger) || lendingLedger.getAmount() <= 0)return;

            String bpLoanId = null;
            String nbfcId = null;
            Optional<LendingApplicationSlave> lendingApplicationSlave = lendingApplicationDaoSlave.findById(lendingLedger.getLendingPaymentSchedule().getApplicationId());
            if (lendingApplicationSlave.isPresent()) {
                bpLoanId = lendingApplicationSlave.get().getExternalLoanId();
                nbfcId = lendingApplicationSlave.get().getNbfcId();
            }
            else{
                HightpvLenderDetails hightpvLenderDetails = hightpvLenderDetailsDao.findByLpsId(lendingPaymentSchedule.getId());
                if(!ObjectUtils.isEmpty(hightpvLenderDetails)){
                    log.info("fetching from hightpv lender details {}, {}", hightpvLenderDetails.getNbfcId(), hightpvLenderDetails.getExternalLoanId());
                    bpLoanId = hightpvLenderDetails.getExternalLoanId();
                    nbfcId = hightpvLenderDetails.getNbfcId();
                }
                else{
                    log.info("nbfc details not found for ledger {}", lendingLedger.getId());
                    return;
                }
            }
            LendingCollectionAudit lendingCollectionAudit = LendingCollectionAudit.builder()
                    .merchantId(lendingLedger.getMerchantId())
                    .merchantStoreId(lendingLedger.getMerchantStoreId())
                    .loanId(lendingPaymentSchedule.getId())
                    .ledgerId(lendingLedger.getId())
                    .applicationId(lendingPaymentSchedule.getApplicationId())
                    .bpLoanId(bpLoanId)
                    .nbfcId(nbfcId)
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
                    .mobile(lendingPaymentSchedule.getMobile())
                    .build();
            lendingCollectionAuditDao.save(lendingCollectionAudit);
        } catch (Exception e) {
            log.error("Error in creating collection audit for ledger id {}, {}", lendingLedger.getId(), Arrays.asList(e.getStackTrace()));
        }
    }
}
