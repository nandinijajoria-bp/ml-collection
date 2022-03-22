package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingFLDGDao;
import com.bharatpe.lending.common.entity.LendingFLDG;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.handlers.S3BucketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Objects;

@Service
public class FLDGReportService {

    Logger logger = LoggerFactory.getLogger(FLDGReportService.class);

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingFLDGDao lendingFLDGDao;

    public void uploadFLDGReportEntries(String fileName) {
        try {
            LendingFLDG lendingFLDG = new LendingFLDG();
            logger.info("Getting file : {} from s3", fileName);
            InputStream fldgFile = s3BucketHandler.getObject(fileName, "loan-document");
            BufferedReader fldgFileReader = new BufferedReader(new InputStreamReader(fldgFile));
            String readLine = fldgFileReader.readLine();
            readLine = fldgFileReader.readLine();
            Date startDate = DateTimeUtil.getCurrentDayStartTime();
            while(Objects.nonNull(readLine)) {
                logger.info("readline: {}", readLine);
                String[] arr = readLine.split(",");
                String externalLoanId = arr[1];
                LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(externalLoanId);

                Double fldgAmount = Double.valueOf(arr[2]);
                Double fldgPrinciple = Double.valueOf(arr[3]);
                Double fldgInterest = Double.valueOf(arr[4]);
                if(Objects.nonNull(lendingApplication)) {
                    LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndApplicationId(lendingApplication.getMerchant().getId(), lendingApplication.getId());
                    if (Objects.nonNull(lendingPaymentSchedule)) {
                        lendingFLDG.setLoanId(lendingPaymentSchedule.getId());
                        lendingFLDG.setFldgAmount(fldgAmount);
                        lendingFLDG.setFldgInterest(fldgInterest);
                        lendingFLDG.setFldgPrinciple(fldgPrinciple);
                        lendingFLDG.setPreFldgPaidAmount(lendingPaymentSchedule.getPaidAmount());
                        lendingFLDG.setPreFldgPaidInterest(lendingPaymentSchedule.getPaidInterest());
                        lendingFLDG.setPreFldgPaidPrinciple(lendingPaymentSchedule.getPaidPrinciple());
                        lendingFLDG.setFldgDate(startDate);
                        lendingFLDGDao.save(lendingFLDG);
                    }
                }
                fldgFileReader.readLine();
            }
        } catch (Exception e) {
            logger.error("Error occurred while saving fldg report: {}",e);
        }
    }

}
