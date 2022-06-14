package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingLenderMappingDao;
import com.bharatpe.lending.common.entity.LendingLenderMapping;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.LoanType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Value("${disbursal.lender.default}")
    String defaultLender;

    @Value("${disbursal.lender.io.topup}")
    String defaultIoTopupLender;

    public void lenderMapping(LendingApplication lendingApplication){
        try{
            if (LoanType.IO_TOPUP.name().equals(lendingApplication.getLoanType())) {
                lendingApplication.setLender(defaultIoTopupLender);
                lendingApplicationDao.save(lendingApplication);
                return;
            }

            Integer repeatLoan = lendingPaymentScheduleDao.getRepeatLoan(lendingApplication.getMerchantId());
            String loanType = repeatLoan > 0 ? "REPEAT" : "NORMAL";
//            if (repeatLoan > 0) {
//                if (true) {
//                    lendingApplication.setLender("LDC");
//                    lendingApplicationDao.save(lendingApplication);
//                    return;
//                }
//                LendingPaymentSchedule oldLoan = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(lendingApplication.getMerchantId());
//                if (!"LDC".equalsIgnoreCase(oldLoan.getNbfc())) {
//                    logger.info("Repeat loan application Lender Change To LDC merchant:{} and applicationId:{}", lendingApplication.getMerchantId(), lendingApplication.getId());
//                    lendingApplication.setLender("LDC");
//                } else {
//                    logger.info("Repeat loan application Lender Change To MAMTA merchant:{} and applicationId:{}", lendingApplication.getMerchantId(), lendingApplication.getId());
//                    lendingApplication.setLender("MAMTA");
//                }
//                lendingApplicationDao.save(lendingApplication);
//                return;
//            }
//            String lender = "LIQUILOANS_NBFC";
            String lender = defaultLender;
            LendingLenderMapping llm = lendingLenderMappingDao.findByMappingLender(loanType);
            if(llm == null){
                lendingLenderMappingDao.updateMultiplier();
                llm = lendingLenderMappingDao.findByMappingLender(loanType);
            }
            lendingLenderMappingDao.updateLenderMultiplier(llm.getLender(), loanType);
            lender=llm.getLender();

            LendingAuditTrial auditLender = new LendingAuditTrial();
            auditLender.setApplicationId(lendingApplication.getId());
            auditLender.setMerchantId(lendingApplication.getMerchantId());
            auditLender.setType("LENDER_SET");
            auditLender.setLoanId("BPL"+lendingApplication.getId());
            auditLender.setOldStatus(lendingApplication.getLender());
            auditLender.setNewStatus(lender);
            lendingAuditTrialDao.save(auditLender);

            lendingApplication.setLender(lender);
            lendingApplicationDao.save(lendingApplication);

        }catch (Exception e){
            logger.error("Exception In Lending Lender Mapping for applicationId:{}", lendingApplication.getId(), e);
            lendingApplication.setLender(defaultLender);
            lendingApplicationDao.save(lendingApplication);
        }
    }
}
