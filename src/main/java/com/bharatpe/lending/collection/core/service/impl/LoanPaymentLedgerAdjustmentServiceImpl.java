package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;
import com.bharatpe.lending.collection.core.service.LoanPaymentLedgerAdjustmentService;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.dao.LendingPrepaymentDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.entity.LendingPrepayment;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.service.LendingCollectionAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


@Service
@Slf4j
public class LoanPaymentLedgerAdjustmentServiceImpl implements LoanPaymentLedgerAdjustmentService {

    public static final String LOAN_PAYMENT_ORDER_ID_PREFIX = "LOAN";

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
    public void adjustLendingLedger(LendingPaymentSchedule loan, PaymentCalculation paymentAdjustment, LoanPaymentOrder order, String desc, String adjustmentMode, String transferType, String bankReferenceNo) {
        LendingLedger lendingLedger = createLendingLedger(loan, paymentAdjustment, desc, adjustmentMode, transferType, bankReferenceNo);
        updateCollectionAuditAndOrder(lendingLedger, order);
    }
    @Override
    public void updateCollectionAuditAndOrder(LendingLedger lendingLedger, LoanPaymentOrder order) {
        if (Objects.nonNull(lendingLedger))lendingCollectionAuditService.sendCollectionAudit(lendingLedger);
        if (Objects.nonNull(order)) markOrderSuccess(order);
    }
    @Override
    public LendingLedger createLendingLedger(LendingPaymentSchedule loan, PaymentCalculation paymentAdjusted, String description, String source, String transferType, String terminalOrderId) {
        if(Objects.isNull(paymentAdjusted)) return null;

        return createLendingLedger(loan, paymentAdjusted.getUsed(), paymentAdjusted.getPrincipleSettled(),
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
    public LoanPaymentOrder createLoanPaymentOrder(LendingPaymentSchedule loan, double orderAmount, String paymentReferenceNo, String status, String source) {
        String orderId = LOAN_PAYMENT_ORDER_ID_PREFIX + loan.getId() + System.currentTimeMillis();
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
            LendingLedger excessCollectionLedger = createLendingLedger(activeLoan, paymentAdjusted,desc,"EXCESS_NACH_ADJUSTED", "EXTERNAL", desc);
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

}
