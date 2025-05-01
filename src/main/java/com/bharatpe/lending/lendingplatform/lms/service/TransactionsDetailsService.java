package com.bharatpe.lending.lendingplatform.lms.service;

import com.bharatpe.lending.dto.LendingMerchantLoansResponseDTO;
import com.bharatpe.lending.lendingplatform.lms.client.LendingPlatformHttpClient;
import com.bharatpe.lending.lendingplatform.lms.dto.response.ApiResponse;
import com.bharatpe.lending.lendingplatform.lms.dto.response.TransactionDetailsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ApiEndPointConstants.GET_ALL_TRANSACTIONS_DETAIL;

// This class is not currently used. It will be implemented as per future requirements.
@Service
@Slf4j
public class TransactionsDetailsService {

    @Autowired
    private LendingPlatformHttpClient lendingPlatformHttpClient;

    public TransactionDetailsResponse getTransactionsFromOneLms(String transactionType, String transactionStatus, String bpLoanId ) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("instrument_type", transactionType);
            params.put("instrument_status", transactionStatus);
            params.put("bpLoanId", bpLoanId);
            ApiResponse<TransactionDetailsResponse> transactionDetailsResponse = lendingPlatformHttpClient.sendGetRequestWithParams(GET_ALL_TRANSACTIONS_DETAIL, params, TransactionDetailsResponse.class);
            if (!ObjectUtils.isEmpty(transactionDetailsResponse) && !ObjectUtils.isEmpty(transactionDetailsResponse.getData()) && !ObjectUtils.isEmpty(transactionDetailsResponse.getData().getBpLoanId())) {
                log.info("Transaction details fetched successfully.");
                return transactionDetailsResponse.getData();
            } else {
                log.error("Invalid response from Transaction Details API: {}", transactionDetailsResponse);
                throw new RuntimeException("Invalid response from Transaction Details API");
            }
        } catch (RuntimeException e) {
            log.error("HTTP error while fetching transactions:");
            throw new RuntimeException("Transaction API request failed: " + e.getMessage(), e);
        }
    }

    public List<LendingMerchantLoansResponseDTO.RepaymentDetails> getRecentTransactionsFromOneLms(TransactionDetailsResponse transactionDetailsResponse){
        List<LendingMerchantLoansResponseDTO.RepaymentDetails> repaymentDetailsList = new ArrayList<>();

        for (TransactionDetailsResponse.TransactionDetails transactionDetails : transactionDetailsResponse.getTransactionDetails()){
            LendingMerchantLoansResponseDTO.RepaymentDetails repaymentDetails = new LendingMerchantLoansResponseDTO.RepaymentDetails();
            repaymentDetails.setAmount(transactionDetails.getTransactionAmount().doubleValue());
            repaymentDetails.setDate(Date.valueOf(transactionDetails.getTransactionDate()));
            repaymentDetails.setMode(transactionDetails.getTransactionMode());
            repaymentDetails.setStatus(transactionDetails.getTransactionStatus());
            repaymentDetails.setOrderId(transactionDetails.getTransactionNo());
            repaymentDetailsList.add(repaymentDetails);
        }

        return repaymentDetailsList;
    }
}

