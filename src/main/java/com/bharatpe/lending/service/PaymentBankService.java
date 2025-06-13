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
        log.info("Entering into the changePaymentAccount method with lendingApplication: {}", lendingApplication.getId());
        if(lendingApplication == null) {
            log.error("Lending application is null");
            return false;
        }

        String bankName = paymentBank(lendingApplication.getMerchantId());
        if (bankName == null) {
            log.error("Bank name is null for merchantId: {}", lendingApplication.getMerchantId());
            return false;
        }
        log.info("Bank name for merchantId {} is: {}", lendingApplication.getMerchantId(), bankName);
        String loanType = lendingApplication.getLoanType();
        boolean repeatLoan = loanUtil.isRepeatLoan(lendingApplication.getMerchantId());
        if(repeatLoan || loanType.equalsIgnoreCase("TOPUP")) {
            return lendingApplication.getLoanAmount() >= 150000;
        }else{
            return lendingApplication.getLoanAmount() >= 50000;
        }
    }

    private String paymentBank(Long merchantId) {
        BankAccountDetails accDetails = loanUtil.getAccountDetails(merchantId);

        if (accDetails == null) {
            log.error("No payment account found for merchantId: {}", merchantId);
            return null;
        }
        String bankName = accDetails.getBankName();
        if (bankName == null || bankName.isEmpty()) {
            log.error("Bank name is null or empty for merchantId: {}", merchantId);
            return null;
        }
        for (PaymentBank paymentBank : PaymentBank.values()) {
            if (bankName.equalsIgnoreCase(paymentBank.getVal())) {
                return paymentBank.name();
            }
        }
        return null;
    }



}
