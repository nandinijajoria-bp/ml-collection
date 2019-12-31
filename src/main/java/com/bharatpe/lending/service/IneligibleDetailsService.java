package com.bharatpe.lending.service;

import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.dao.MerchantLoanRequestDoa;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantLoanRequest;
import com.bharatpe.common.entities.MerchantSummary;
import com.bharatpe.common.objects.CommonAPIRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class IneligibleDetailsService {

    private Logger logger = LoggerFactory.getLogger(IneligibleDetailsService.class);

    @Autowired
    private MerchantSummaryDao merchantSummaryDao;

    @Autowired
    private MerchantLoanRequestDoa merchantLoanRequestDoa;

    @Autowired
    private MerchantDao merchantDao;

    public Map<String, Object> fetchIneligibleLoanDetails(Merchant merchant, CommonAPIRequest commonAPIRequest) {
        Map<String, Object> finalResponse = new HashMap<>();
        Integer requestedLoanAmount = null;
        MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
        MerchantLoanRequest merchantLoanRequest = merchantLoanRequestDoa.getMerchantLoanRequest(merchant.getId());
        if (commonAPIRequest.getPayload().get("requested_loan_amt") != null) {
            requestedLoanAmount = (Integer) commonAPIRequest.getPayload().get("requested_loan_amt");
            logger.info("New Ineligible Loan request for amount : {} and merchantId : {}", requestedLoanAmount, merchant.getId());
            merchantLoanRequestDoa.deleteByMerchantId(merchant.getId());
            merchantLoanRequest = calculateTarget(merchantSummary, requestedLoanAmount, merchant.getId());
        }
        if (merchantLoanRequest != null) {
            requestedLoanAmount = merchantLoanRequest.getRequestedLoanAmount();
            calculateIneligibleLoanDetails(merchantSummary, merchantLoanRequest, finalResponse);
        }
        int previousLoanCount = (merchantSummary != null && merchantSummary.getTotalLoansCount() != null) ? merchantSummary.getTotalLoansCount() : 0;
        createResponse(finalResponse, requestedLoanAmount, previousLoanCount);
        return finalResponse;
    }

    private void calculateIneligibleLoanDetails(MerchantSummary merchantSummary, MerchantLoanRequest merchantLoanRequest, Map<String, Object> finalResponse){
        Map<String, Object> transactionCountDetails = new HashMap<>();
        Map<String, Object> transactionAmountDetails = new HashMap<>();
        Map<String, Object> loanDetails = new HashMap<>();
        int currentTxnCount = (merchantSummary != null && merchantSummary.getTotalTxns1Month() != null) ? merchantSummary.getTotalTxns1Month() : 0;
        double currentTxnValue = (merchantSummary != null && merchantSummary.getTpv1Mon() != null) ? merchantSummary.getTpv1Mon() : 0;
        int onGoingTransactions = currentTxnCount - merchantLoanRequest.getInitialTransactionCount();
        double onGoingAmount = currentTxnValue - merchantLoanRequest.getInitialTransactionAmount();
        double avgTxnValue = Math.ceil((merchantLoanRequest.getTargetTransactionAmount()/merchantLoanRequest.getTargetTransactionCount())/10.0)*10;
        int transactionCountLeft = Math.max(merchantLoanRequest.getTargetTransactionCount() - onGoingTransactions, 0);
        double transactionAmountLeft = Math.max(merchantLoanRequest.getTargetTransactionAmount() - onGoingAmount, 0);
        Calendar c = Calendar.getInstance();
        c.setTime(merchantLoanRequest.getCreatedAt());
        c.add(Calendar.DATE, 30);
        Date unlockDate = c.getTime();
        transactionCountDetails.put("txn_left", transactionCountLeft);
        transactionCountDetails.put("txn_ongoing", onGoingTransactions);
        transactionCountDetails.put("txn_total", merchantLoanRequest.getTargetTransactionCount());
        transactionAmountDetails.put("txn_left", transactionAmountLeft);
        transactionAmountDetails.put("txn_ongoing", onGoingAmount);
        transactionAmountDetails.put("txn_total", merchantLoanRequest.getTargetTransactionAmount());
        loanDetails.put("average_txn", avgTxnValue);
        loanDetails.put("unlock_date", unlockDate);
        finalResponse.put("transaction_count_details", transactionCountDetails);
        finalResponse.put("transaction_amt_details", transactionAmountDetails);
        finalResponse.put("loan_details", loanDetails);
        if (transactionCountLeft == 0 && transactionAmountLeft == 0) {
            finalResponse.put("eligible", true);
        } else {
            finalResponse.put("eligible", false);
        }
    }

    private MerchantLoanRequest calculateTarget(MerchantSummary merchantSummary, Integer requestedLoanAmount, Long merchantId){
        //long vintage = TimeUnit.DAYS.convert(new Date().getTime() - merchantSummary.getCreatedAt().getTime(), TimeUnit.MILLISECONDS);
        int tenure = 6;
        float multiplier = 0.5f;
        double totalTxnValue = (merchantSummary != null && merchantSummary.getTpv1Mon() != null) ? merchantSummary.getTpv1Mon() : 0;
        int totalTxnCount = (merchantSummary != null && merchantSummary.getTotalTxns1Month() != null) ? merchantSummary.getTotalTxns1Month() : 0;
        double avgTxnValue = (totalTxnValue != 0 && totalTxnCount != 0) ? Math.floor((totalTxnValue/totalTxnCount)/10.0)*10 : 0;
        double totalAmountRequired = (((requestedLoanAmount * 3)/(tenure * multiplier))-totalTxnValue)/2;
        int totalTxnRequired = (avgTxnValue != 0) ? (int) Math.ceil(totalAmountRequired/avgTxnValue) : 50;//taking minimum transaction count as 50
        logger.info("Calculating target for ineligible loan---");
        logger.info("Current transaction count : {}, Current transaction amount: {}, Transaction amount required: {}, Transaction Count required: {}", totalTxnCount, totalTxnValue, totalAmountRequired, totalTxnRequired);
        return merchantLoanRequestDoa.save(new MerchantLoanRequest(merchantId, requestedLoanAmount, totalTxnCount, totalTxnValue, totalTxnRequired, totalAmountRequired));
    }

    private void createResponse(Map<String, Object> finalResponse, Integer requestedLoanAmount, Integer totalLoansCount){
        finalResponse.put("min_loan_amt", 10000);
        if (totalLoansCount == null || totalLoansCount.equals(0)) {
            finalResponse.put("isLoanRequested", false);
            finalResponse.put("max_loan_amt", 250000);
        } else {
            finalResponse.put("isLoanRequested", true);
            finalResponse.put("max_loan_amt", 500000);
        }
        if (requestedLoanAmount != null) {
            finalResponse.put("requested_loan_amt", requestedLoanAmount);
        }
    }
}
