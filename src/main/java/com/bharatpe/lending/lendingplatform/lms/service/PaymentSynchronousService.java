package com.bharatpe.lending.lendingplatform.lms.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.lendingplatform.lms.client.LendingPlatformHttpClient;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.lendingplatform.lms.dto.request.PaymentSynchronousRequest;
import com.bharatpe.lending.lendingplatform.lms.dto.response.ApiResponse;
import com.bharatpe.lending.lendingplatform.lms.dto.response.PaymentSynchronousResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Date;

// Note: This service is currently unused. It will be implemented as per future requirements.

@Service
@Slf4j
public class PaymentSynchronousService {
    @Autowired
    private LmsLoanDetailsService lmSloanDetailsService;

    @Autowired
    private LendingPlatformHttpClient lendingPlatformHttpClient;

    public void postPaymentDetails(LendingPaymentSchedule activeLoan, Double amount, String bankRefNo, String source) {
        try {
            LendingApplication lendingApplication = lmSloanDetailsService.getLendingApplicationDetails(activeLoan.getMerchantId());
            BankDetailsDto merchantBankDetail = lmSloanDetailsService.getMerchantBankDetails(activeLoan.getMerchantId()); //Fetching bank details form Merchant service

            // Note : Need to call fetch transactions method
            //TransactionDetailsResponse transactionDetailsResponse = TransactionsDetailsService.method();
            PaymentSynchronousRequest request = PaymentSynchronousRequest.builder()
                    .bpLoanId(lendingApplication.getExternalLoanId())
                    //.paymentId(transactionDetailsResponse)
                    //.transactionReferenceId(transactionDetailsResponse)
                    .date(new Date())
                    .paymentStatus(Constants.PaymentTransferConstant.PAYMENT_SUCCESS)
                    .build();
            ApiResponse<PaymentSynchronousResponse> apiResponse = lendingPlatformHttpClient.sendPostRequest(Constants.ApiEndPointConstants.POST_PAYMENT, request, PaymentSynchronousResponse.class);
            if (apiResponse != null && !ObjectUtils.isEmpty(apiResponse.getData().getMessage())) {
                log.info("Payment updated successfully. BP Loan ID: {}", apiResponse.getData().getExternalLmsId());
                return;
            }
            log.error("Payment update failed: Empty or invalid response received for BP Loan ID: {}", lendingApplication.getExternalLoanId());
            throw new RuntimeException("Payment update failed: Invalid response from lending platform.");
        } catch (Exception e) {
            log.error("Exception occurred while initiating Payment update request: {}", e.getMessage(), e);
            throw new RuntimeException("Error during Payment update : " + e.getMessage(), e);
        }
    }
}
