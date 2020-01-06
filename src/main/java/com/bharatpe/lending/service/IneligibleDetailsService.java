package com.bharatpe.lending.service;

import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.dao.MerchantLoanRequestDoa;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantLoanRequest;
import com.bharatpe.common.entities.MerchantSummary;
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

@Service
public class IneligibleDetailsService {

    private Logger logger = LoggerFactory.getLogger(IneligibleDetailsService.class);

    @Autowired
    private MerchantSummaryDao merchantSummaryDao;

    @Autowired
    private MerchantLoanRequestDoa merchantLoanRequestDoa;

    @Autowired
    private MerchantDao merchantDao;

    public IneligibleResponseDTO fetchIneligibleLoanDetails(Merchant merchant, IneligibleRequestDTO ineligibleRequestDTO) {
        logger.debug("Fetching Ineligible Loan Details for merchantId : {}", merchant.getId());
        MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
        int previousLoanCount = (merchantSummary != null && merchantSummary.getTotalLoansCount() != null) ? merchantSummary.getTotalLoansCount() : 0;
        IneligibleResponseDTO ineligibleResponseDTO = new IneligibleResponseDTO(previousLoanCount);
        MerchantLoanRequest merchantLoanRequest = merchantLoanRequestDoa.getMerchantLoanRequest(merchant.getId());
        if (ineligibleRequestDTO != null && ineligibleRequestDTO.getRequestedLoanAmount() != null && ineligibleRequestDTO.getPanCard() != null && !ineligibleRequestDTO.getPanCard().trim().equalsIgnoreCase("")) {
            logger.info("New Ineligible Loan request for amount : {} with panCard : {} and merchantId : {}", ineligibleRequestDTO.getRequestedLoanAmount(), ineligibleRequestDTO.getPanCard(), merchant.getId());
            merchantLoanRequestDoa.deleteByMerchantId(merchant.getId());
            merchantLoanRequest = calculateTarget(merchantSummary, ineligibleRequestDTO.getRequestedLoanAmount(), merchant.getId(), ineligibleRequestDTO.getPanCard());
        }
        if (merchantLoanRequest != null) {
            calculateIneligibleLoanDetails(merchantSummary, merchantLoanRequest, ineligibleResponseDTO);
        }
        return ineligibleResponseDTO;
    }

    private void calculateIneligibleLoanDetails(MerchantSummary merchantSummary, MerchantLoanRequest merchantLoanRequest, IneligibleResponseDTO ineligibleResponseDTO){
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
        ineligibleResponseDTO.setTransactionCountDetails(transactionCountDetails);
        ineligibleResponseDTO.setTransactionAmtDetails(transactionAmountDetails);
        ineligibleResponseDTO.setLoanDetails(loanDetails);
        if (transactionCountLeft == 0 && transactionAmountLeft == 0) {
            ineligibleResponseDTO.setEligible(true);
        } else {
            ineligibleResponseDTO.setEligible(false);
        }
        ineligibleResponseDTO.setRequestedLoanAmt(merchantLoanRequest.getRequestedLoanAmount());
    }

    private MerchantLoanRequest calculateTarget(MerchantSummary merchantSummary, Integer requestedLoanAmount, Long merchantId, String panCard){
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
        return merchantLoanRequestDoa.save(new MerchantLoanRequest(merchantId, requestedLoanAmount, totalTxnCount, totalTxnValue, totalTxnRequired, totalAmountRequired, panCard));
    }
}
