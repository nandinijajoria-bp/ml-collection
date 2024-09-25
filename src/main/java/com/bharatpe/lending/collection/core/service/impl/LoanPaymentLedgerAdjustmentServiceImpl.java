package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;
import com.bharatpe.lending.collection.core.service.LoanPaymentLedgerAdjustmentService;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.CollectionTransferTypeEnum;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.service.LendingCollectionAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

import static com.bharatpe.lending.common.enums.PerpetualDpdAdjusted.Y;


@Service
@Slf4j
public class LoanPaymentLedgerAdjustmentServiceImpl implements LoanPaymentLedgerAdjustmentService {

    public static final String LOAN_PAYMENT_ORDER_OWNER = "LOAN";

    public static final String EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX = "_adjust_";

    @Autowired
    LendingPrepaymentDao lendingPrepaymentDao;

    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;

    @Autowired
    LoanPaymentOrderDao loanPaymentOrderDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingCollectionAuditService lendingCollectionAuditService;

    @Autowired
    PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    PenalChargesDao penalChargesDao;

    @Autowired
    LendingPaymentScheduleLendingCommonDao lendingPaymentScheduleLendingCommonDao;

    @Override
    public LendingCollectionExcess adjustNachLedger(LendingCollectionExcess lendingCollectionExcess, PaymentCalculation paymentAdjusted) {
        log.info("adjustNachLedger: initiating nach : {} payment : {}", lendingCollectionExcess, paymentAdjusted);
        lendingCollectionExcess.setAmount(lendingCollectionExcess.getAmount() - paymentAdjusted.getUsed());
        lendingCollectionExcess.setDeductedAmount(lendingCollectionExcess.getDeductedAmount() + paymentAdjusted.getUsed());

        lendingCollectionExcess.setDeductionCount(lendingCollectionExcess.getDeductionCount() + 1);
        if (lendingCollectionExcess.getAmount() < 1D){
            lendingCollectionExcess.setStatus("CLOSED");
        }
        lendingCollectionExcessDao.save(lendingCollectionExcess);
        log.info("adjustNachLedger: complete nach : {} payment : {}", lendingCollectionExcess, paymentAdjusted);
        return lendingCollectionExcess;
    }
    @Override
    public void adjustAdvanceEdiLedger(LendingPaymentSchedule loan, PaymentCalculation advanceAdjustment) {
        LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(loan.getMerchantId(), loan.getId());
        if (lendingPrepayment != null) {
            lendingPrepayment.setAdvanceEdiCount(lendingPrepayment.getAdvanceEdiCount() - (int)(advanceAdjustment.getUsed()/loan.getEdiAmount()));
            lendingPrepayment.setAdvanceEdiAmount(lendingPrepayment.getAdvanceEdiAmount() - advanceAdjustment.getUsed());
            lendingPrepaymentDao.save(lendingPrepayment);
        }
    }
    @Override
    public LendingLedger adjustLendingLedger(LendingPaymentSchedule loan, PaymentCalculation paymentAdjustment, LoanPaymentOrder order, String desc, String adjustmentMode, String transferType, String bankReferenceNo) {
        LendingLedger lendingLedger = createLendingLedger(loan, paymentAdjustment, desc, adjustmentMode, transferType, bankReferenceNo);
        updateCollectionAuditAndOrder(lendingLedger, order);
        return lendingLedger;
    }
    @Override
    public void updateCollectionAuditAndOrder(LendingLedger lendingLedger, LoanPaymentOrder order) {
        if (Objects.nonNull(lendingLedger))lendingCollectionAuditService.sendCollectionAudit(lendingLedger);
        if (Objects.nonNull(order)) markOrderSuccess(order);
    }
    @Override
    public LendingLedger createLendingLedger(LendingPaymentSchedule loan, PaymentCalculation paymentAdjusted, String description, String source, String transferType, String terminalOrderId) {
        if(Objects.isNull(paymentAdjusted)) return null;

        Date ledgerDate;
        Optional<LendingPaymentScheduleLendingCommon> lendingPaymentScheduleLendingCommon = lendingPaymentScheduleLendingCommonDao.findById(loan.getId());
        if(lendingPaymentScheduleLendingCommon.isPresent() && Y.name().equalsIgnoreCase(lendingPaymentScheduleLendingCommon.get().getPerpetualDpdAdjusted())){
            ledgerDate = DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), 1);
        }
        else{
            ledgerDate = DateTimeUtil.getCurrentDayStartTime();
        }
        return createLendingLedger(loan, ledgerDate, paymentAdjusted.getUsed(), paymentAdjusted.getPrincipleSettled(),
                paymentAdjusted.getInterestSettled(), description, source, transferType, terminalOrderId, paymentAdjusted.getPenaltySettled(), paymentAdjusted.getChargesSettled());
    }

    @Override
    public LendingLedger createLendingLedger(LendingPaymentSchedule loan, Double amount, Double principle,
                                             Double interest, String description, String source, String transferType, String terminalOrderId, Double penaltyFee, Double charges) {
        if(amount == 0) return null;

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(loan.getMerchantId());
        if(loan.getMerchantStoreId() != null && loan.getMerchantStoreId() > 0) lendingLedger.setMerchantStoreId(loan.getMerchantStoreId());
        lendingLedger.setLendingPaymentSchedule(loan);
        lendingLedger.setDate(DateTimeUtil.getCurrentDayStartTime());
        lendingLedger.setTxnType("EDI");
        lendingLedger.setAmount(amount);
        lendingLedger.setInterest(interest);
        lendingLedger.setPrinciple(principle);
        lendingLedger.setOtherCharges(charges);
        lendingLedger.setPenalty(penaltyFee);
        lendingLedger.setAdjustmentMode(source);
        lendingLedger.setDescription(description);
        lendingLedger.setTransferType(transferType);
        lendingLedger.setTerminalOrderId(terminalOrderId);
        lendingLedgerDao.save(lendingLedger);
        return lendingLedger;
    }

    @Override
    public LendingLedger createLendingLedger(LendingPaymentSchedule loan, Date ledgerDate, Double amount, Double principle,
                                             Double interest, String description, String source, String transferType, String terminalOrderId, Double penaltyFee, Double charges) {
        if(amount == 0) return null;

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(loan.getMerchantId());
        if(loan.getMerchantStoreId() != null && loan.getMerchantStoreId() > 0) lendingLedger.setMerchantStoreId(loan.getMerchantStoreId());
        lendingLedger.setLendingPaymentSchedule(loan);
        lendingLedger.setDate(ledgerDate);
        lendingLedger.setTxnType("EDI");
        lendingLedger.setAmount(amount);
        lendingLedger.setInterest(interest);
        lendingLedger.setPrinciple(principle);
        lendingLedger.setOtherCharges(charges);
        lendingLedger.setPenalty(penaltyFee);
        lendingLedger.setAdjustmentMode(source);
        lendingLedger.setDescription(description);
        lendingLedger.setTransferType(transferType);
        lendingLedger.setTerminalOrderId(terminalOrderId);
        lendingLedgerDao.save(lendingLedger);
        return lendingLedger;
    }

    @Override
    public LoanPaymentOrder createLoanPaymentOrder(LendingPaymentSchedule loan, double orderAmount, String paymentReferenceNo, String status, String source, String orderId) {
        log.info("createLoanPaymentOrder : creating LPO loanId:{} amount:{} status:{} source :{}", loan.getId(), orderAmount, status, source);
        LoanPaymentOrder order = new LoanPaymentOrder();
        order.setMerchantId(loan.getMerchantId());
        order.setMerchantStoreId(loan.getMerchantStoreId());
        order.setOwner(LOAN_PAYMENT_ORDER_OWNER);
        order.setOwnerId(loan.getId());
        order.setOrderId(orderId);
        order.setAmount(orderAmount);
        order.setBankRefNo(paymentReferenceNo);
        order.setStatus(status);
        order.setSource(source);
        return loanPaymentOrderDao.save(order);
    }
    @Override
    public void markOrderSuccess(LoanPaymentOrder order) {
        log.info("markOrderSuccess : LPO updating  orderId:{} and currentStatus :{} loanId : {}", order.getId(), order.getStatus(), order.getOwnerId());
        order.setStatus("SUCCESS");
        loanPaymentOrderDao.save(order);
        log.info("markOrderSuccess : LPO  updated orderId:{} and newStatus :{} loanId : {}", order.getId(), order.getStatus(), order.getOwnerId());
    }

    @Override
    public void createLendingLedgerForExcessCollectionOnForeclosure(LendingPaymentSchedule activeLoan, List<LendingCollectionExcess> lendingCollectionExcessList){
        if(ObjectUtils.isEmpty(lendingCollectionExcessList))return;
        List<LendingLedger> lendingLedgersListExcessCollection = new ArrayList<>();
        for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
            String desc = lendingCollectionExcess.getTerminalOrderId() + EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX + (lendingCollectionExcess.getDeductionCount() + 1);
            PaymentCalculation paymentAdjusted = PaymentCalculation.builder()
                    .used(lendingCollectionExcess.getAmount())
                    .principleSettled(lendingCollectionExcess.getAmount())
                    .build();
            LendingLedger excessCollectionLedger = createLendingLedger(activeLoan, paymentAdjusted,desc,"EXCESS_NACH_ADJUSTED", CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name(), desc);
            if (Objects.nonNull(excessCollectionLedger)) lendingCollectionAuditService.sendCollectionAudit(excessCollectionLedger);
            lendingLedgersListExcessCollection.add(excessCollectionLedger);
        }
    }
    @Override
    public void settleExcessCollectionBalance(Long loanId, List<LendingCollectionExcess> lendingCollectionExcessList){
        if(ObjectUtils.isEmpty(lendingCollectionExcessList))return;
        log.info("settling excess collection upon foreclosure for loanId:{}, {}", loanId, lendingCollectionExcessList);
        for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
            lendingCollectionExcess.setDeductedAmount(lendingCollectionExcess.getDeductedAmount() + lendingCollectionExcess.getAmount());
            lendingCollectionExcess.setAmount(0D);
            lendingCollectionExcess.setDeductionCount(lendingCollectionExcess.getDeductionCount() + 1);
            lendingCollectionExcess.setStatus("CLOSED");
            lendingCollectionExcessDao.save(lendingCollectionExcess);
        }
    }

    @Override
    public void adjustPenaltyLedger(LendingPaymentSchedule loan, double amount, String source, boolean waveOff) {
        if (amount > 0.5) {
            PenaltyFeeLedger penaltyFeeLedger = new PenaltyFeeLedger(loan.getMerchantId(), loan.getId(), amount, source, waveOff, loan.getNbfc());
            penaltyFeeLedgerDao.save(penaltyFeeLedger);
            savePenalCharges(loan, amount);
        }
    }

    public void savePenalCharges(LendingPaymentSchedule loan, Double penaltyAdjusted) {
        try {
            PenalCharges penalCharge = penalChargesDao.findByLoanId(loan.getId());
            if (ObjectUtils.isEmpty(penalCharge)) {
                return;
            }
            double nachBounceAdjusted = 0;
            double netPenaltyAdjusted = 0;
            if (Objects.nonNull(penalCharge.getDueNachBounce())) {
                nachBounceAdjusted = penalCharge.getDueNachBounce() < penaltyAdjusted ? penalCharge.getDueNachBounce() : penaltyAdjusted;
                netPenaltyAdjusted = penaltyAdjusted - nachBounceAdjusted;
                double paidNachBounce = Objects.nonNull(penalCharge.getPaidNachBounce()) ? penalCharge.getPaidNachBounce() + nachBounceAdjusted : nachBounceAdjusted;
                penalCharge.setDueNachBounce(penalCharge.getDueNachBounce() - nachBounceAdjusted);
                penalCharge.setPaidNachBounce(paidNachBounce);
            }

            if (Objects.nonNull(penalCharge.getDuePenalty())) {
                double paidPenalty = Objects.nonNull(penalCharge.getPaidPenalty()) ? penalCharge.getPaidPenalty() + netPenaltyAdjusted : netPenaltyAdjusted;
                penalCharge.setPaidPenalty(paidPenalty);
                penalCharge.setDuePenalty(penalCharge.getDuePenalty() - netPenaltyAdjusted);
            }
            penalChargesDao.save(penalCharge);
        } catch (Exception e) {
            log.error("Exception occured while saving penal charge for loan: {} {} {}", loan.getId(), Arrays.asList(e.getStackTrace()), e);
        }
    }
}
