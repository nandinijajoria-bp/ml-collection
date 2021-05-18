package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.lending.common.dao.LendingLenderMappingDao;
import com.bharatpe.lending.common.entity.LendingLenderMapping;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LenderMappingService {

    private Logger logger = LoggerFactory.getLogger(LenderMappingService.class);

    @Autowired
    LendingLenderMappingDao lendingLenderMappingDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    public void lenderMapping(LendingApplication lendingApplication){
        try{
            String lender = "LDC";
            LendingLenderMapping llm = lendingLenderMappingDao.findByMappingLender();
            if(llm == null){
                lendingLenderMappingDao.updateMultiplier();
                llm = lendingLenderMappingDao.findByMappingLender();
            }
            lendingLenderMappingDao.updateLenderMultiplier(llm.getLender());
            lender=llm.getLender();

            LendingAuditTrial auditLender = new LendingAuditTrial();
            auditLender.setApplicationId(lendingApplication.getId());
            auditLender.setMerchantId(lendingApplication.getMerchant().getId());
            auditLender.setType("LENDER_SET");
            auditLender.setLoanId("BPL"+lendingApplication.getId());
            auditLender.setOldStatus(lendingApplication.getLender());
            auditLender.setNewStatus(lender);
            lendingAuditTrialDao.save(auditLender);

            lendingApplication.setLender(lender);
            lendingApplicationDao.save(lendingApplication);

        }catch (Exception e){
            logger.error("Exception In Lending Lender Mapping of exception :{}", e);
        }
    }
}
