package com.bharatpe.lending.service;

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

    private boolean isValidPaymentAccount(String bankName, double amount, String status){
        log.info("Validating payment account");
        if(bankName == null || bankName.isEmpty()){
            log.error("Bank name is null or empty");
            return false;
        }

    }
}
