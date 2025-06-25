package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.enums.PaymentBank;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.util.LoanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentBankService {

    @Value("${minimum.threshold.fresher:50000}")
    Integer minimumThresholdForFreshUser;

    @Value("${minimum.threshold.repeat.topup.loan:150000}")
    Integer minimumThresholdForRepeatAndTopupLoan;

    private final LoanUtil loanUtil;


    public boolean changePaymentAccount(LendingApplication lendingApplication, BankAccountDetails accDetails ) {
        log.info("Entering into the changePaymentAccount method with lendingApplication: {}",
                lendingApplication != null ? lendingApplication.getId() : "null");
        if(accDetails == null){
            log.info("Account details are null for merchant {}", lendingApplication.getMerchantId()));
            return false;
        }
        Long merchantId = lendingApplication.getMerchantId();
        if (!isPaymentBank(merchantId, accDetails)) {
            log.info("MerchantId {} is not using a payment bank", merchantId);
            return false;
        }
        log.info("MerchantId {} is using a valid payment bank", merchantId);
        String loanType = lendingApplication.getLoanType();
        if (loanType != null && LoanType.TOPUP.name().equalsIgnoreCase(loanType)) {
            log.info("Loan type is TOPUP for lending application: {}", lendingApplication.getId());
        }
        boolean repeatLoan = loanUtil.isRepeatLoan(merchantId);
        if (repeatLoan || LoanType.TOPUP.name().equalsIgnoreCase(loanType)) {
            return lendingApplication.getLoanAmount() > minimumThresholdForRepeatAndTopupLoan;
        } else {
            return lendingApplication.getLoanAmount() > minimumThresholdForFreshUser;
        }
    }


    public boolean isPaymentBank(Long merchantId, BankAccountDetails accDetails) {
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
