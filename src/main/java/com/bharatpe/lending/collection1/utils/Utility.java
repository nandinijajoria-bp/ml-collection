package com.bharatpe.lending.collection1.utils;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.enums.CollectionTransferTypeEnum;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.LendingCollectionAuditService;
import com.bharatpe.lending.service.PaymentService;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.bharatpe.lending.constant.PaymentConstants.EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX;

@Component
public class Utility {
    Logger logger = LoggerFactory.getLogger(PaymentService.class);

    ExecutorService notificationExecutor = Executors.newFixedThreadPool(10);

    @Autowired
    LoanUtil loanUtil;
    @Autowired
    LendingLedgerDao lendingLedgerDao;
    @Autowired
    LendingCollectionAuditService lendingCollectionAuditService;

    @Autowired
    APIGatewayService apiGatewayService;
    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;


    public void createLendingLedgerForExcessCollectionOnForeclosure(LendingPaymentSchedule activeLoan, List<LendingCollectionExcess> lendingCollectionExcessList){
        if(ObjectUtils.isEmpty(lendingCollectionExcessList))return;
        List<LendingLedger> lendingLedgersListExcessCollection = new ArrayList<>();
        for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
            String desc = lendingCollectionExcess.getTerminalOrderId() + EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX + (lendingCollectionExcess.getDeductionCount() + 1);
            LendingLedger excessCollectionLedger = createLendingLedger(activeLoan, lendingCollectionExcess.getAmount(),
                    lendingCollectionExcess.getAmount(), 0d,  desc,
                    "EXCESS_NACH_ADJUSTED", "EXTERNAL", desc, 0D
            );
            lendingLedgersListExcessCollection.add(excessCollectionLedger);
        }
    }

    public LendingLedger createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Double amount, Double principle,
                                             Double interest, String description, String source, String transferType, String terminalOrderId, Double penaltyFee) {
        return createLendingLedger(lendingPaymentSchedule, amount, principle, interest, description, source, transferType, terminalOrderId, penaltyFee, null);
    }
    private LendingLedger createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Double amount, Double principle,
                                              Double interest, String description, String source, String transferType, String terminalOrderId, Double penaltyFee, Double otherCharges) {
        if(amount == 0) {
            return null;
        }

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(lendingPaymentSchedule.getMerchantId());
        if(lendingPaymentSchedule.getMerchantStoreId() != null && lendingPaymentSchedule.getMerchantStoreId() > 0) {
            lendingLedger.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
        }

        lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
        lendingLedger.setDate(getCurrenntDate());
        lendingLedger.setTxnType("EDI");
        lendingLedger.setAmount(amount);
        lendingLedger.setInterest(interest);
        lendingLedger.setOtherCharges((otherCharges != null) ? otherCharges : 0);
        lendingLedger.setPenalty(penaltyFee);
        lendingLedger.setPrinciple(principle);
        if (source != null) {
            lendingLedger.setAdjustmentMode(source);
        } else {
            lendingLedger.setAdjustmentMode("UPI");
        }

        if(!ObjectUtils.isEmpty(source) && source.equals("BHARATPE_NACH")){
            Boolean isBpNachDone = false;
            isBpNachDone = loanUtil.isNachToBeRefunded(lendingPaymentSchedule.getLoanApplication());
            if(!isBpNachDone){
                transferType = "EXTERNAL";
            }
        }

        lendingLedger.setDescription(description);
        lendingLedger.setTerminalOrderId(terminalOrderId);
        lendingLedger.setTransferType(Objects.nonNull(transferType) && transferType.equals("EXTERNAL") ?
                CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name() : CollectionTransferTypeEnum.TRANSFER_BY_BP.name());

        lendingLedgerDao.save(lendingLedger);
        lendingCollectionAuditService.sendCollectionAudit(lendingLedger);

        if(amount > 0 && principle > 0) {
            logger.info("Credit principle:{} in lending global limit for merchant:{}", principle, lendingLedger.getMerchantId());
            notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(lendingLedger.getMerchantId(), "CREDIT", principle));
        }
        return lendingLedger;

    }
    public void settleExcessCollectionBalance(Long loanId, List<LendingCollectionExcess> lendingCollectionExcessList){
        if(ObjectUtils.isEmpty(lendingCollectionExcessList))return;
        logger.info("settling excess collection upon foreclosure for loanId:{}, {}", loanId, lendingCollectionExcessList);
        for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
            lendingCollectionExcess.setDeductedAmount(lendingCollectionExcess.getDeductedAmount() + lendingCollectionExcess.getAmount());
            lendingCollectionExcess.setAmount(0D);
            lendingCollectionExcess.setDeductionCount(lendingCollectionExcess.getDeductionCount() + 1);
            lendingCollectionExcess.setStatus("CLOSED");
            lendingCollectionExcessDao.save(lendingCollectionExcess);
        }
    }

    private Date getCurrenntDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        return cal.getTime();
    }
}
