package com.bharatpe.lending.loanV3.revamp.services;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.dao.LendingApplicationDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Slf4j
public class LendingApplicationServiceV3 {


    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    public LendingApplication getLendingApplication(Long applicationId,Long merchantId){
        LendingApplication lendingApplication;
        if(Objects.nonNull(applicationId)){
            lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
        }
        else{
            lendingApplication=lendingApplicationDao.findInProgressLoanApplication(merchantId);
        }
        return lendingApplication;
    }
}
