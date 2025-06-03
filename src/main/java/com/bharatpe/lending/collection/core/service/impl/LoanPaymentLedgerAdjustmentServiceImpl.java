package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.PaymentCalculation;
import com.bharatpe.lending.collection.core.service.LoanPaymentLedgerAdjustmentService;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.collection.core.utils.LoanPaymentUtil;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.dao.LendingPrepaymentDao;
import com.bharatpe.lending.common.dao.PenalChargesDao;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.entity.LendingPrepayment;
import com.bharatpe.lending.common.entity.PenalCharges;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.common.enums.CollectionTransferTypeEnum;
import com.bharatpe.lending.common.enums.TransferTypeModes;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.service.LendingCollectionAuditService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;

import static com.bharatpe.lending.common.enums.PerpetualDpdAdjusted.Y;
import static com.bharatpe.lending.constant.PaymentConstants.UPI_AUTOPAY_EXCESS_CREDIT_MODE;
import static com.bharatpe.lending.enums.SettlementDetailsStatus.INIT;

import static com.bharatpe.lending.loanV3.enums.piramal.PaymentTypePiramal.LPC_WO_GST;
import static com.bharatpe.lending.loanV3.enums.piramal.PaymentTypePiramal.NACH_BOUNCE_CHARGES;

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
    @Autowired
    @Qualifier("ConfluentKafkaTemplate")
    KafkaTemplate confluentKafkaTemplate;

    @Autowired
    private SettlementDetailsDao settlementDetailsDao;
    @Autowired
    LoanUtil loanUtil;
    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Autowired
    private EasyLoanUtil easyLoanUtil;
    @Autowired
    LendingCollectionAuditDao lendingCollectionAuditDao;

    @Value("${whitelisted.real.time.reciept.posting.lenders:TRILLIONLOANS}")
    String realTimeRecieptPostingWhitelistedLenders;

    @Value("#{'${whitelisted.real.time.reciept.posting.mode}'.split(',')}")
    Set<String> realTimeRecieptPostingWhitelistedPgModes;

    @Value("${whitelisted.real.time.reciept.percent.rollout:1}")
    Integer realTimeRecieptPostingPercentScaleUp;

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
        log.info("inside creating lending ledger and audit for loan {} and order {}",loan,order);
        LendingLedger lendingLedger = createLendingLedger(loan, paymentAdjustment, desc, adjustmentMode, transferType, bankReferenceNo);
        updateCollectionAuditAndOrder(lendingLedger, order);
        if( !"PAYU".equalsIgnoreCase(loan.getNbfc()) && ("UPI_AUTOPAY".equalsIgnoreCase(adjustmentMode) || "AUTO_PAY_UPI_EXCESS_ADJUSTED".equalsIgnoreCase(adjustmentMode)) && paymentAdjustment.getUsed() > 0){
            //push loanId to kafka
            if(lendingLedger!= null) confluentKafkaTemplate.send("autopayupi-posting", lendingLedger.getId());
        }
        try {
            if(lendingLedger != null){
            log.info("inside sending upi-real-time-posting for loanId: {} and ledgerId: {}", loan.getId(), lendingLedger.getId());
            LendingPaymentScheduleLendingCommon lendingPaymentScheduleLendingCommon = lendingPaymentScheduleLendingCommonDao.findById(loan.getId()).orElse(null);

            if (adjustmentMode != null && realTimeRecieptPostingWhitelistedPgModes.contains(adjustmentMode) && easyLoanUtil.percentScaleUp(loan.getMerchantId(), realTimeRecieptPostingPercentScaleUp)
                    && loan.getNbfc() != null   && realTimeRecieptPostingWhitelistedLenders.contains(loan.getNbfc()) && lendingPaymentScheduleLendingCommon != null
                    && !"Y".equalsIgnoreCase(lendingPaymentScheduleLendingCommon.getPerpetualDpdAdjusted())) {
                log.info("Real time reciept posting for UPI {}", lendingLedger);
                confluentKafkaTemplate.send("autopayupi-posting", lendingLedger.getId());
            }
            }
        }catch (Exception ex){
            log.error("Error while sending autopayupi-posting for loanId: {} and ledgerId: {}", loan, lendingLedger, ex);
        }
        return lendingLedger;
    }
    @Override
    public void updateCollectionAuditAndOrder(LendingLedger lendingLedger, LoanPaymentOrder order) {
        log.info("inside update audit for ledger {} and order {}",lendingLedger,order);
        if (Objects.nonNull(lendingLedger))lendingCollectionAuditService.sendCollectionAudit(lendingLedger);
        if (Objects.nonNull(order)) markOrderSuccess(order);
    }
    @Override
    public LendingLedger createLendingLedger(LendingPaymentSchedule loan, PaymentCalculation paymentAdjusted, String description, String source, String transferType, String terminalOrderId) {
        if(Objects.isNull(paymentAdjusted)) return null;

        Date ledgerDate;
        Optional<LendingPaymentScheduleLendingCommon> lendingPaymentScheduleLendingCommon = lendingPaymentScheduleLendingCommonDao.findById(loan.getId());
        if(lendingPaymentScheduleLendingCommon.isPresent() && Y.name().equalsIgnoreCase(lendingPaymentScheduleLendingCommon.get().getPerpetualDpdAdjusted()) && !("UPI_AUTOPAY".equalsIgnoreCase(source) || "AUTO_PAY_UPI_EXCESS_ADJUSTED".equalsIgnoreCase(source))){
            ledgerDate = DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), 1);
        }
        else{
            ledgerDate = DateTimeUtil.getCurrentDayStartTime();
        }

      LendingLedger lendingLedger =   createLendingLedger(loan, ledgerDate, paymentAdjusted.getUsed(), paymentAdjusted.getPrincipleSettled(),
                paymentAdjusted.getInterestSettled(), description, source, transferType, terminalOrderId, paymentAdjusted.getPenaltySettled(), paymentAdjusted.getChargesSettled());
       return lendingLedger;
    }

    @Override
    public LendingLedger createLendingLedger(LendingPaymentSchedule loan, Double amount, Double principle,
                                             Double interest, String description, String source, String transferType, String terminalOrderId, Double penaltyFee, Double charges) {
        if(amount == 0) return null;

        Long settlementId = null;
        try {
            if (loan.getSettlementInitiated()) {
                SettlementDetails details = settlementDetailsDao.findByLoanIdAndStatus(loan.getId(), INIT.name());
                if (Objects.nonNull(details)) {
                    settlementId = details.getId();
                }
            }
        } catch (Exception ex) {
            log.error("Multiple settlement initiated for loan id: {}, Stack: {}", loan.getId(), Arrays.asList(ex.getStackTrace()));
        }

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
        lendingLedger.setSettlementId(settlementId);
        lendingLedgerDao.save(lendingLedger);
        return lendingLedger;
    }

    @Override
    public LendingLedger createLendingLedger(LendingPaymentSchedule loan, Date ledgerDate, Double amount, Double principle,
                                             Double interest, String description, String source, String transferType, String terminalOrderId, Double penaltyFee, Double charges) {
        if(amount == 0) return null;

        Long settlementId = null;
        try {
            if (loan.getSettlementInitiated()) {
                SettlementDetails details = settlementDetailsDao.findByLoanIdAndStatus(loan.getId(), INIT.name());
                if (Objects.nonNull(details)) {
                    settlementId = details.getId();
                }
            }
        } catch (Exception ex) {
            log.error("Multiple settlement initiated for loan id: {}, Stack: {}", loan.getId(), Arrays.asList(ex.getStackTrace()));
        }
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
        lendingLedger.setSettlementId(settlementId);

        if(!ObjectUtils.isEmpty(source) && "LMS_PRECLOSURE".equals(source)){
            lendingLedger.setAdjustmentMode("DIRECT_TRANSFER");
            lendingLedger.setTransferType("DIRECT_TRANSFER_LENDER");
            if(amount > 0) {
                lendingLedger.setDescription("PRECLOSER_IMPS/NEFT");
            }
        }

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
            String transferType = (StringUtils.hasLength(lendingCollectionExcess.getTransferType())) ? lendingCollectionExcess.getTransferType() : CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name();
            String adjustmentMode = LoanPaymentUtil.getExcessAdjustedModeDesc(lendingCollectionExcess.getMode());
            LendingLedger excessCollectionLedger = createLendingLedger(activeLoan, paymentAdjusted,desc, adjustmentMode, transferType, desc);
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
                penalCharge.setDueNachBounce((double) Math.round(penalCharge.getDueNachBounce() - nachBounceAdjusted));
                penalCharge.setPaidNachBounce((double) Math.round(paidNachBounce));
                LendingApplicationLenderDetails lendingApplicationLenderDetails =
                        lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(loan.getApplicationId(), com.bharatpe.lending.common.enums.Status.ACTIVE.name());
                PenaltyFeeLedger nachBounceLedgerApplied = penaltyFeeLedgerDao.findTop1NachBounceOrderByIdDesc(loan.getId());
                if(lendingApplicationLenderDetails != null && Lender.PIRAMAL.name().equalsIgnoreCase(loan.getNbfc()) && nachBounceAdjusted > 0 && nachBounceLedgerApplied != null && !nachBounceLedgerApplied.getIsPosted()) {
                    loanUtil.piramalPenaltyPosting(lendingApplicationLenderDetails, nachBounceLedgerApplied,nachBounceLedgerApplied.getAmount()*-1,NACH_BOUNCE_CHARGES.name());
                }
            }

            if (Objects.nonNull(penalCharge.getDuePenalty())) {
                double paidPenalty = Objects.nonNull(penalCharge.getPaidPenalty()) ? penalCharge.getPaidPenalty() + netPenaltyAdjusted : netPenaltyAdjusted;

                penalCharge.setPaidPenalty((double) Math.round(paidPenalty));
                penalCharge.setDuePenalty((double) Math.round(penalCharge.getDuePenalty() - netPenaltyAdjusted));
                try {
                LendingApplicationLenderDetails lendingApplicationLenderDetails =
                        lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(loan.getApplicationId(), com.bharatpe.lending.common.enums.Status.ACTIVE.name());
                PenaltyFeeLedger penaltyFeeLedgerApplied = penaltyFeeLedgerDao.findTop1PenaltyFeeOrderByIdDesc(loan.getId());
                if(lendingApplicationLenderDetails != null && Lender.PIRAMAL.name().equalsIgnoreCase(loan.getNbfc()) && netPenaltyAdjusted > 0 && penaltyFeeLedgerApplied != null && !penaltyFeeLedgerApplied.getIsPosted()) {
                    loanUtil.postPiramalPenalty(loan,lendingApplicationLenderDetails);
                }}catch (Exception e){
                    log.error("Lending : Error while posting piramal penalty for loanId: {} and error: {}", loan, Arrays.asList(e.getStackTrace()), e);
                }

            }
            penalChargesDao.save(penalCharge);
        } catch (Exception e) {
            log.error("Exception occured while saving penal charge for loan: {} {} {}", loan.getId(), Arrays.asList(e.getStackTrace()), e);
        }
    }

    @Override
    public void createAutoPayUpiExcessCreditAuditEntry(LendingCollectionExcess lendingCollectionExcess, LendingPaymentSchedule lendingPaymentSchedule, Double refundAmount) {
        try {
            log.info("Creating AUTO PAY UPI excess credit entry for PAYU for excess entry {} , for loan {} , for amount {} ", lendingCollectionExcess, lendingPaymentSchedule, refundAmount);
            LendingCollectionAudit lendingCollectionAudit = LendingCollectionAudit.builder()
                    .merchantId(lendingPaymentSchedule.getMerchantId())
                    .merchantStoreId(lendingPaymentSchedule.getMerchantStoreId())
                    .loanId(lendingPaymentSchedule.getId())
                    .applicationId(lendingPaymentSchedule.getLoanApplication().getId())
                    .bpLoanId(lendingPaymentSchedule.getLoanApplication().getExternalLoanId())
                    .nbfcId(lendingPaymentSchedule.getLoanApplication().getNbfcId())
                    .txnType("EDI")
                    .description(lendingCollectionExcess.getTerminalOrderId())
                    .transferType(TransferTypeModes.DIRECT_TRANSFER_LENDER.name())
                    .status("PENDING")
                    .amount(refundAmount)
                    .otherCharges(0D)
                    .penalty(0D)
                    .adjustmentMode(UPI_AUTOPAY_EXCESS_CREDIT_MODE)
                    .transferDate(DateTimeUtil.getCurrentDayStartTime())
                    .terminalOrderId(lendingCollectionExcess.getTerminalOrderId())
                    .lender(lendingPaymentSchedule.getNbfc())
                    .loanStatus(lendingPaymentSchedule.getStatus())
                    .mobile(lendingPaymentSchedule.getMobile())
                    .ledgerId(lendingCollectionExcess.getId())
                    .build();
            lendingCollectionAuditDao.save(lendingCollectionAudit);
            if(lendingPaymentSchedule.getClosingDate() != null) lendingCollectionAudit.setLoanClosingDate(lendingPaymentSchedule.getClosingDate());
        } catch (Exception e) {
            log.error("Error in creating collection audit for excess credit entry for ledger id {}, {}", lendingCollectionExcess, Arrays.asList(e.getStackTrace()));
        }
    }

    public void creatingPenaltyInPenaltyLedger(LendingPaymentSchedule loan, double penaltyFee, String description, boolean isWaiveOff) {
        PenaltyFeeLedger penaltyFeeLedger = new PenaltyFeeLedger(loan.getMerchantId(), loan.getId(), -penaltyFee, description, isWaiveOff, loan.getNbfc(),false);
        penaltyFeeLedgerDao.save(penaltyFeeLedger);
    }

    @Override
    public LendingLedger createPenaltyLedger(LendingPaymentSchedule loan, double penaltyFee, String penaltyDescription) {
        if(ObjectUtils.isEmpty(penaltyFee)) {
            return null;
        }

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(loan.getMerchantId());
        if(loan.getMerchantStoreId() != null && loan.getMerchantStoreId() > 0) lendingLedger.setMerchantStoreId(loan.getMerchantStoreId());
        lendingLedger.setLendingPaymentSchedule(loan);
        lendingLedger.setDate(DateTimeUtil.getCurrentDayStartTime());
        lendingLedger.setTxnType("EDI");
        lendingLedger.setAmount(-penaltyFee);
        lendingLedger.setInterest(0d);
        lendingLedger.setPrinciple(0d);
        lendingLedger.setOtherCharges(0d);
        lendingLedger.setPenalty(-penaltyFee);
        lendingLedger.setDescription(penaltyDescription);
        lendingLedgerDao.save(lendingLedger);
        return lendingLedger;
    }
}
