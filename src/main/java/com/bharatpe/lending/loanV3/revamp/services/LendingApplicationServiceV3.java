package com.bharatpe.lending.loanV3.revamp.services;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.query.dao.LendingApplicationDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.dao.LendingApplicationDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Objects;

@Service
@Slf4j
public class LendingApplicationServiceV3 {


    @Autowired
    private LendingApplicationDaoSlave lendingApplicationDaoSlave;

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;


    public LendingApplicationSlave getLendingApplicationSlave(Long applicationId,Long merchantId){
        LendingApplicationSlave lendingApplication;
        if(Objects.nonNull(applicationId)){
            lendingApplication = lendingApplicationDaoSlave.findByIdAndMerchantId(applicationId, merchantId);
        }
        else{
            lendingApplication=lendingApplicationDaoSlave.findInProgressLoanApplication(merchantId);
        }
        return lendingApplication;
    }

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

    public LendingApplicationDetails getLendingApplicationDetailsByApplicationId(Long applicationId){
        if (ObjectUtils.isEmpty(applicationId)) {
            return null;
        }
        return lendingApplicationDetailsDao.findByApplicationId(applicationId);
    }
}
