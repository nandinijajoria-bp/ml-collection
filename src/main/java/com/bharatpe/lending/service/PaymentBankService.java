package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.PaymentBankDao;
import com.bharatpe.lending.enums.PaymentBank;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PaymentBankService {


    @Autowired
    private LoanUtil loanUtil;

    @Autowired
    private PaymentBankDao paymentBankDao;


    public boolean changePaymentAccount(LendingApplication lendingApplication) {
        if(lendingApplication == null) {
            log.error("Lending application is null");
            return false;
        }

        String bankName = isPaymentBank(lendingApplication.getMerchantId());
        String loanType = lendingApplication.getLoanType();
        boolean repeatLoan = loanUtil.isRepeatLoan(lendingApplication.getMerchantId());
        if(repeatLoan || loanType.equalsIgnoreCase("TOPUP")) {
            return lendingApplication.getLoanAmount() >= 150000 && bankName != null;
        }else{
            return lendingApplication.getLoanAmount() >= 50000 && bankName != null;
        }
    }

    private String isPaymentBank(Long merchantId) {
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
