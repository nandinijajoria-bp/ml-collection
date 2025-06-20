package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.enums.PaymentBank;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PaymentBankService {


    @Autowired
    private LoanUtil loanUtil;


    public boolean changePaymentAccount(LendingApplication lendingApplication) {
        log.info("Entering into the changePaymentAccount method with lendingApplication: {}",
                lendingApplication != null ? lendingApplication.getId() : "null");

        if (lendingApplication == null) {
            log.error("Lending application is null");
            return false;
        }
        Long merchantId = lendingApplication.getMerchantId();
        if (!isPaymentBank(merchantId)) {
            log.info("MerchantId {} is not using a payment bank", merchantId);
            return false;
        }
        log.info("MerchantId {} is using a valid payment bank", merchantId);
        String loanType = lendingApplication.getLoanType();
        if (loanType != null && loanType.equalsIgnoreCase("TOPUP")) {
            log.info("Loan type is TOPUP for lending application: {}", lendingApplication.getId());
        }
        boolean repeatLoan = loanUtil.isRepeatLoan(merchantId);
        log.info("Repeat loan status for merchantId {} is: {}", merchantId, repeatLoan);
        if (repeatLoan || "TOPUP".equalsIgnoreCase(loanType)) {
            return lendingApplication.getLoanAmount() >= 150000;
        } else {
            return lendingApplication.getLoanAmount() >= 50000;
        }
    }


    public boolean isPaymentBank(Long merchantId) {
        BankAccountDetails accDetails = loanUtil.getAccountDetails(merchantId);

        if (accDetails == null || accDetails.getBankName() == null || accDetails.getBankName().isEmpty()) {
            log.error("Bank account details missing or bank name is empty for merchantId: {}", merchantId);
            return false;
        }

        String bankName = accDetails.getBankName();
        for (PaymentBank paymentBank : PaymentBank.values()) {
            if (bankName.equalsIgnoreCase(paymentBank.getVal())) {
                log.info("Bank {} is a recognized payment bank for merchantId: {}", bankName, merchantId);
                return true;
            }
        }

        log.info("Bank {} is NOT a recognized payment bank for merchantId: {}", bankName, merchantId);
        return false;
    }



}
