package com.bharatpe.lending.lendingplatform.lms.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LmsPaymentDetailsDao;
import com.bharatpe.lending.common.dao.LoanForeClosureChargesDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LmsPaymentDetails;
import com.bharatpe.lending.common.entity.LoanForeClosureCharges;
import com.bharatpe.lending.common.enums.LMSPaymentStatus;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.lendingplatform.lms.client.LendingPlatformHttpClient;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.lendingplatform.lms.dto.request.PaymentAsynchronousRequest;
import com.bharatpe.lending.lendingplatform.lms.dto.response.ApiResponse;
import com.bharatpe.lending.lendingplatform.lms.dto.response.PaymentAsynchronousResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentAsynchronousService {

    private final LmsLoanDetailsService loanDetailsService;
    private final LendingPlatformHttpClient lendingPlatformHttpClient;
    private final LmsPaymentDetailsDao lmsPaymentDetailsDao;
    private final LendingApplicationDao lendingApplicationDao;
    private final LoanForeClosureChargesDao loanForeClosureChargesDao;

    public void postPaymentDetails(
            LendingPaymentSchedule activeLoan, Double amount, String source, String terminalOrderId, Long orderId, boolean foreClosure) {
        try {
            LendingApplication lendingApplication = loanDetailsService.getLendingApplicationDetails(activeLoan.getMerchantId());
            BankDetailsDto merchantBankDetail = loanDetailsService.getMerchantBankDetails(activeLoan.getMerchantId()); //Fetching bank details form Merchant service
            LendingApplicationLenderDetails lald = loanDetailsService.getLenderDetails(lendingApplication.getId(), lendingApplication.getLender());

            LmsPaymentDetails lmsPaymentDetails =
                    insertTransactionRecord(lendingApplication, amount, source, terminalOrderId);

            PaymentAsynchronousRequest paymentAsynchronousRequest = getPaymentAsynchronousRequest(
                    amount, source, terminalOrderId, lendingApplication, merchantBankDetail, lald, new Date(), orderId, foreClosure);

            if (postPaymentToLMS(lmsPaymentDetails, paymentAsynchronousRequest)) {
                if(foreClosure) {
                    markLoanEligibleForForeclosure(lmsPaymentDetails);
                }
                return;
            }

            log.error("Payment posting failed: Empty or invalid response received for BP Loan ID: {}", lendingApplication.getExternalLoanId());
            throw new RuntimeException("Payment posting failed: Invalid response from lending platform."); // TODO : Add custom exception
        } catch (Exception e) {
            log.error("Exception occurred while initiating Payment posting request: {}", e.getMessage(), e);
            throw new RuntimeException("Error during Payment posting : " + e.getMessage(), e); // TODO : Add custom exception
        }
    }

    private void markLoanEligibleForForeclosure(LmsPaymentDetails lmsPaymentDetails) {
        lmsPaymentDetails.setIsEligibleForForeclosure("YES");
        lmsPaymentDetails.setUpdatedAt(new Date());
        lmsPaymentDetailsDao.save(lmsPaymentDetails);
    }

    public void postSettlementPaymentLMS(String terminalOrderId) {
        try {
            LmsPaymentDetails lmsPaymentDetails = lmsPaymentDetailsDao.findByTerminalOrderId(terminalOrderId);
            if (ObjectUtils.isEmpty(lmsPaymentDetails)) {
                log.error("Payment details not found for terminalOrderId: {}", terminalOrderId);
                return;
            }

            LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(lmsPaymentDetails.getBpLoanId());
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.error("Lending application not found for BP Loan ID: {}", lmsPaymentDetails.getBpLoanId());
                return;
            }

            LendingApplicationLenderDetails lald = loanDetailsService.getLenderDetails(lendingApplication.getId(), lendingApplication.getLender());
            BankDetailsDto merchantBankDetail = loanDetailsService.getMerchantBankDetails(lendingApplication.getMerchantId());
            Long orderId = 0L;
            PaymentAsynchronousRequest paymentAsynchronousRequest = getPaymentAsynchronousRequest(
                    lmsPaymentDetails.getAmount().doubleValue(), lmsPaymentDetails.getAdjustmentMode(), terminalOrderId, lendingApplication, merchantBankDetail, lald, lmsPaymentDetails.getTransferDate(), orderId , false);

            if (!postPaymentToLMS(lmsPaymentDetails, paymentAsynchronousRequest)) {
                log.error("Payment posting failed: Invalid response from lending platform for BP Loan ID: {}", lendingApplication.getExternalLoanId());
                throw new RuntimeException("Payment posting failed: Invalid response from lending platform."); // TODO : Add custom exception
            }
        } catch (Exception exception) {
            log.error("Exception occurred while Payment posting request: {}", exception.getMessage(), exception);
        }
    }

    private LmsPaymentDetails insertTransactionRecord(
            LendingApplication lendingApplication, Double amount, String source, String terminalOrderId) {
        log.info("Populating request for payment service for bpLoanId:{}", lendingApplication.getExternalLoanId());
        LmsPaymentDetails lmsPaymentDetails = new LmsPaymentDetails();
        lmsPaymentDetails.setBpLoanId(lendingApplication.getExternalLoanId());
        lmsPaymentDetails.setLender(lendingApplication.getLender());
        lmsPaymentDetails.setTerminalOrderId(terminalOrderId);
        lmsPaymentDetails.setTransferDate(new Date());
        lmsPaymentDetails.setAmount(BigDecimal.valueOf(amount));
        lmsPaymentDetails.setAdjustmentMode(StringUtils.hasLength(source) ? source : "UPI");
        lmsPaymentDetails.setSentToLender(LMSPaymentStatus.INIT);
        lmsPaymentDetails.setSentToLms(LMSPaymentStatus.INIT);
        lmsPaymentDetails.setCreatedAt(new Date());
        return lmsPaymentDetailsDao.save(lmsPaymentDetails);
    }

    private PaymentAsynchronousRequest getPaymentAsynchronousRequest(
            Double amount, String source, String terminalOrderId, LendingApplication lendingApplication, BankDetailsDto merchantBankDetail, LendingApplicationLenderDetails lald, Date transferDate, Long orderId, boolean foreclosureEligibility) {
        double foreclosureCharges = 0;
        LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(orderId);
        if(!ObjectUtils.isEmpty(loanForeClosureCharges)){
            log.info("Foreclosure charges table found for orderId; {}", orderId);
            foreclosureCharges = loanForeClosureCharges.getAmount() + loanForeClosureCharges.getTax();
        }

        return PaymentAsynchronousRequest.builder()
                .bpLoanId(lendingApplication.getExternalLoanId())
                .applicationId(String.valueOf(lendingApplication.getId()))
                .lender(lendingApplication.getLender())
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .amount(BigDecimal.valueOf(amount))
                .paymentMode(StringUtils.hasLength(source) ? source : "UPI")
                .depositBankAccount("10150146205")
                .depositBankIfsc("IDFB0020145") //Sharing Dummy details
                .issuingBankAccount(merchantBankDetail.getAccountNumber())
                .issuingBankIfsc(merchantBankDetail.getIfsc())
                .date(transferDate)
                .fundInRemarks("EDI Payment")
                .paymentStatus(Constants.PaymentTransferConstant.PAYMENT_SUCCESS)
                .transactionReferenceNo(terminalOrderId) //Using terminalOrderId instead of bankRefNo as this can be used in db for receipt posting
                .leadId(lald.getLeadId())
                .clientId(lald.getCccId())
                .lenderLoanAccountNumber(lald.getLan())
                .isEligibleForForeclosure(foreclosureEligibility)
                .foreclosureCharges(BigDecimal.valueOf(foreclosureCharges))
                .build();
    }

    private boolean postPaymentToLMS(
            LmsPaymentDetails lmsPaymentDetails, PaymentAsynchronousRequest paymentAsynchronousRequest) {
        log.info("Posting payment request to 1LMS for terminalOrderId:{}", paymentAsynchronousRequest.getTransactionReferenceNo());
        ApiResponse<PaymentAsynchronousResponse> paymentAsynchronousResponse =
                lendingPlatformHttpClient.sendPostRequest(Constants.ApiEndPointConstants.POST_PAYMENT, paymentAsynchronousRequest, PaymentAsynchronousResponse.class);

        if (!ObjectUtils.isEmpty(paymentAsynchronousResponse)
                && paymentAsynchronousResponse.isSuccess()
                && !ObjectUtils.isEmpty(paymentAsynchronousResponse.getData().getMessage())) {

            log.info("Payment posted successful. BP Loan ID: {}", paymentAsynchronousRequest.getBpLoanId());
            updatePostedAtLms(lmsPaymentDetails);
            return true;
        }
        return false;
    }

    private void updatePostedAtLms(LmsPaymentDetails lmsPaymentDetails) {
        lmsPaymentDetails.setSentToLms(LMSPaymentStatus.PENDING);
        lmsPaymentDetails.setSentToLender(LMSPaymentStatus.PENDING);
        lmsPaymentDetails.setUpdatedAt(new Date());
        lmsPaymentDetailsDao.save(lmsPaymentDetails);
    }
}
