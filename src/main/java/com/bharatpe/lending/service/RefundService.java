package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.CommonResponse;
import com.bharatpe.lending.dto.LendingPayoutRequest;
import com.bharatpe.lending.dto.LendingPayoutResponse;
import com.bharatpe.lending.dto.NachRefundRequest;
import com.bharatpe.lending.enums.LendingPayoutType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class RefundService {

    private final Logger logger = LoggerFactory.getLogger(RefundService.class);

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    public CommonResponse nachRefund(NachRefundRequest refundRequest) {
        try {
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(refundRequest.getLoanId(), refundRequest.getMerchantId());
            if (lendingPaymentSchedule == null) {
                logger.info("Loan not found for id:{}", refundRequest.getLoanId());
                return new CommonResponse(false, "Loan not found");
            }
            boolean success = false;
            if (lendingPaymentSchedule.getStatus().equals("INACTIVE") && lendingPaymentSchedule.getPaidAmount() > 0D) {
                logger.info("Refund paid amount:{} for inactive loan:{}",lendingPaymentSchedule.getPaidAmount(), lendingPaymentSchedule.getId());
                String orderId = "INACTIVE_REFUND" + System.currentTimeMillis();
                Double refundAmount = lendingPaymentSchedule.getPaidAmount();
                LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, refundAmount, LendingPayoutType.LENDING_NACH_REFUND, lendingPaymentSchedule.getMerchant().getId());
                LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
                if (lendingPayoutResponse != null) {
                    success = true;
                    String bankRefNo = lendingPayoutResponse.getData() != null ? lendingPayoutResponse.getData().getBankReferenceNo() : null;
                    createLendingLedger(lendingPaymentSchedule, DateTimeUtil.getCurrentDayStartTime(), "LOAN_REFUND", -refundAmount, -lendingPaymentSchedule.getPaidPrinciple(), -lendingPaymentSchedule.getPaidInterest(), 0D, 0D, bankRefNo, "REFUND");
                    lendingPaymentSchedule.setDueAmount(0D);
                    lendingPaymentSchedule.setDueInterest(0D);
                    lendingPaymentSchedule.setDuePrinciple(0D);
                    lendingPaymentScheduleDao.save(lendingPaymentSchedule);
                }
            } else if (lendingPaymentSchedule.getDueAmount() < 0D && lendingPaymentSchedule.getStatus().equals("CLOSED")) {
                logger.info("Refund due amount:{} for loan:{}",lendingPaymentSchedule.getDueAmount(), lendingPaymentSchedule.getId());
                String orderId = "NACH_REFUND" + System.currentTimeMillis();
                Double refundAmount = -1 * lendingPaymentSchedule.getDueAmount();
                Double principle = -1 * lendingPaymentSchedule.getDuePrinciple();
                Double interest = -1 * lendingPaymentSchedule.getDueInterest();
                LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, refundAmount, LendingPayoutType.LENDING_NACH_REFUND, lendingPaymentSchedule.getMerchant().getId());
                LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
                if (lendingPayoutResponse != null) {
                    success = true;
                    String bankRefNo = lendingPayoutResponse.getData() != null ? lendingPayoutResponse.getData().getBankReferenceNo() : null;
                    createLendingLedger(lendingPaymentSchedule, DateTimeUtil.getCurrentDayStartTime(), "LOAN_REFUND", -refundAmount, -principle, -interest, 0D, 0D, bankRefNo, "REFUND");
                    lendingPaymentSchedule.setDueAmount(lendingPaymentSchedule.getDueAmount() + refundAmount);
                    lendingPaymentSchedule.setDueInterest(lendingPaymentSchedule.getDueInterest() + interest);
                    lendingPaymentSchedule.setDuePrinciple(lendingPaymentSchedule.getDuePrinciple() + principle);
                    lendingPaymentScheduleDao.save(lendingPaymentSchedule);
                }
            }
            if (success) {
                return new CommonResponse(true, "Loan refund success for loanId:" + lendingPaymentSchedule.getId());
            } else {
                return new CommonResponse(false, "Loan refund failed for loanId:" + lendingPaymentSchedule.getId());
            }
        } catch (Exception e) {
            logger.error("Exception in nach refund for loanId:{}", refundRequest.getLoanId(), e);
        }
        return new CommonResponse(false, "Something went wrong");
    }

    public void createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Date date, String txnType, Double amount, Double principle, Double interest, Double otherCharges, Double penalty, String description, String adjustmentMode) {
        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchant(lendingPaymentSchedule.getMerchant());
        if(lendingPaymentSchedule.getMerchantStoreId() != null && lendingPaymentSchedule.getMerchantStoreId() > 0){
            lendingLedger.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
        }
        lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
        lendingLedger.setDate(date);
        lendingLedger.setTxnType(txnType);
        lendingLedger.setAmount(amount);
        lendingLedger.setInterest(interest);
        lendingLedger.setOtherCharges(otherCharges);
        lendingLedger.setPenalty(penalty);
        lendingLedger.setPrinciple(principle);
        lendingLedger.setDescription(description);
        lendingLedger.setAdjustmentMode(adjustmentMode);
        lendingLedgerDao.save(lendingLedger);
    }
}
