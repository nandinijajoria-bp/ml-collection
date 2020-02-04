package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.dto.IneligibleRequestDTO;
import com.bharatpe.lending.dto.IneligibleResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class IneligibleDetailsService {

    private Logger logger = LoggerFactory.getLogger(IneligibleDetailsService.class);

    @Autowired
    private MerchantSummaryDao merchantSummaryDao;

    @Autowired
    private MerchantLoanRequestDoa merchantLoanRequestDoa;

    @Autowired
    private MerchantDao merchantDao;

    @Autowired
    MerchantLoanRequestAuditTrailDoa merchantLoanRequestAuditTrailDoa;

    @Autowired
    ScoreCategoryMasterDao scoreCategoryMasterDao;

    public IneligibleResponseDTO fetchIneligibleLoanDetails(Merchant merchant, IneligibleRequestDTO ineligibleRequestDTO) {
        logger.debug("Fetching Ineligible Loan Details for merchantId : {}", merchant.getId());
        MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
        int previousLoanCount = (merchantSummary != null && merchantSummary.getTotalLoansCount() != null) ? merchantSummary.getTotalLoansCount() : 0;
        IneligibleResponseDTO ineligibleResponseDTO = new IneligibleResponseDTO(previousLoanCount);
        MerchantLoanRequest merchantLoanRequest = merchantLoanRequestDoa.getMerchantLoanRequest(merchant.getId());
        ScoreCategoryMaster scoreCategoryMaster = null;
        if (merchant.getBusinessCategory() != null && !merchant.getBusinessCategory().trim().equalsIgnoreCase("")) {
            scoreCategoryMaster = scoreCategoryMasterDao.getByCategory(merchant.getBusinessCategory());
        }
        if (scoreCategoryMaster == null) {
            Object[] objects = scoreCategoryMasterDao.getCategoryAverage();
            scoreCategoryMaster = new ScoreCategoryMaster();
            scoreCategoryMaster.setTxnCount((double)objects[0]);
            scoreCategoryMaster.setAvgDailyTpv((double)objects[1]);
        }
        if (ineligibleRequestDTO != null && ineligibleRequestDTO.getPanCard() != null && !ineligibleRequestDTO.getPanCard().trim().equalsIgnoreCase("")) {
            logger.info("New Ineligible Loan request with panCard : {} and merchantId : {}", ineligibleRequestDTO.getPanCard(), merchant.getId());
            merchantLoanRequestDoa.deleteByMerchantId(merchant.getId());
            merchantLoanRequest = calculateTarget(merchantSummary, merchant.getId(), ineligibleRequestDTO.getPanCard(), scoreCategoryMaster);
            MerchantLoanRequestAuditTrail merchantLoanRequestAuditTrail = MerchantLoanRequestAuditTrail.createObject(merchantLoanRequest);
            merchantLoanRequestAuditTrailDoa.save(merchantLoanRequestAuditTrail);
        }
        if (merchantLoanRequest != null) {
            calculateIneligibleLoanDetails(merchantSummary, merchantLoanRequest, ineligibleResponseDTO);
        }
        return ineligibleResponseDTO;
    }

    private void calculateIneligibleLoanDetails(MerchantSummary merchantSummary, MerchantLoanRequest merchantLoanRequest, IneligibleResponseDTO ineligibleResponseDTO) {
        Map<String, Object> transactionCountDetails = new HashMap<>();
        Map<String, Object> transactionAmountDetails = new HashMap<>();
        Map<String, Object> loanDetails = new HashMap<>();
        int currentTxnCount = (merchantSummary != null && merchantSummary.getDailyTxnCount() != null) ? merchantSummary.getDailyTxnCount() : 0;
        double currentTxnValue = (merchantSummary != null && merchantSummary.getDailyTxnAmount() != null) ? merchantSummary.getDailyTxnAmount() : 0;
        int onGoingTransactions = currentTxnCount - merchantLoanRequest.getInitialTransactionCount();
        double onGoingAmount = currentTxnValue - merchantLoanRequest.getInitialTransactionAmount();
        int transactionCountLeft = Math.max(merchantLoanRequest.getTargetTransactionCount() - onGoingTransactions, 0);
        double transactionAmountLeft = Math.max(merchantLoanRequest.getTargetTransactionAmount() - onGoingAmount, 0);
        Calendar c = Calendar.getInstance();
        c.setTime(merchantLoanRequest.getCreatedAt());
        c.add(Calendar.DATE, 30);
        Date unlockDate = c.getTime();
        if (transactionCountLeft == 0 && transactionAmountLeft == 0) {
            ineligibleResponseDTO.setEligible(true);
        } else {
            ineligibleResponseDTO.setEligible(false);
        }
//        long gracePeriod = TimeUnit.DAYS.convert(new Date().getTime() - unlockDate.getTime(), TimeUnit.MILLISECONDS);
//        if (merchantLoanRequest.getRequestedLoanAmount() >= 10000 && merchantLoanRequest.getRequestedLoanAmount() < 200000 && gracePeriod > 6) {//if grace period is more than 7 days then start a new loan cycle
//            return;
//        }
//        if (merchantLoanRequest.getRequestedLoanAmount() >= 200000 && merchantLoanRequest.getRequestedLoanAmount() < 400000 && gracePeriod > 20) {//if grace period is more than 7 days then start a new loan cycle
//            return;
//        }
//        if (merchantLoanRequest.getRequestedLoanAmount() >= 400000 && gracePeriod > 29) {//if grace period is more than 7 days then start a new loan cycle
//            return;
//        }
        transactionCountDetails.put("txn_left", merchantLoanRequest.getTargetTransactionCount().equals(0) ? 0 : transactionCountLeft);
        transactionCountDetails.put("txn_ongoing", merchantLoanRequest.getTargetTransactionCount().equals(0) ? currentTxnCount : onGoingTransactions);
        transactionCountDetails.put("txn_total", merchantLoanRequest.getTargetTransactionCount().equals(0) ? currentTxnCount : merchantLoanRequest.getTargetTransactionCount());
        transactionAmountDetails.put("txn_left", merchantLoanRequest.getTargetTransactionCount().equals(0) ? 0 : transactionAmountLeft);
        transactionAmountDetails.put("txn_ongoing", merchantLoanRequest.getTargetTransactionCount().equals(0) ? currentTxnValue : onGoingAmount);
        transactionAmountDetails.put("txn_total", merchantLoanRequest.getTargetTransactionCount().equals(0) ? currentTxnValue : merchantLoanRequest.getTargetTransactionAmount());
        loanDetails.put("average_txn", 100);
        loanDetails.put("unlock_date", unlockDate);
        ineligibleResponseDTO.setTransactionCountDetails(transactionCountDetails);
        ineligibleResponseDTO.setTransactionAmtDetails(transactionAmountDetails);
        ineligibleResponseDTO.setLoanDetails(loanDetails);
        ineligibleResponseDTO.setPanCard(merchantLoanRequest.getPancardNumber());
    }

    private MerchantLoanRequest calculateTarget(MerchantSummary merchantSummary, Long merchantId, String panCard, ScoreCategoryMaster scoreCategoryMaster) {
        int currentTxnCount = (merchantSummary != null && merchantSummary.getDailyTxnCount() != null) ? merchantSummary.getDailyTxnCount() : 0;
        double currentTxnValue = (merchantSummary != null && merchantSummary.getDailyTxnAmount() != null) ? merchantSummary.getDailyTxnAmount() : 0;
        logger.info("Calculating target for ineligible loan---");
        double totalTxnRequired = 2 * scoreCategoryMaster.getTxnCount();
        double totalAmountRequired = 2 * scoreCategoryMaster.getAvgDailyTpv();
        logger.info("Current transaction count : {}, Current transaction amount: {}, Transaction amount required: {}, Transaction Count required: {}", currentTxnCount, currentTxnValue, totalAmountRequired, totalTxnRequired);
        return merchantLoanRequestDoa.save(new MerchantLoanRequest(merchantId, 0,  currentTxnCount, currentTxnValue, (int)totalTxnRequired, totalAmountRequired, panCard));
    }
}
