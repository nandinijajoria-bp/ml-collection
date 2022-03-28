package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingFLDGDao;
import com.bharatpe.lending.common.dao.LendingNbfcRetryDao;
import com.bharatpe.lending.common.entity.LendingFLDG;
import com.bharatpe.lending.common.entity.LendingNbfcRetry;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.enums.Lender;
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

    @Autowired
    LendingNbfcRetryDao lendingNbfcRetryDao;

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

    public ResponseDTO nbfcRetry(String fileName) {
        try {
            logger.info("Getting file : {} from s3", fileName);
            InputStream nbfcRetryFile = s3BucketHandler.getObject(fileName, "loan-document");
            BufferedReader nbfcRetryFileReader = new BufferedReader(new InputStreamReader(nbfcRetryFile));
            String readLine = nbfcRetryFileReader.readLine();
            readLine = nbfcRetryFileReader.readLine();
            String message = "";
            Integer count = 0;
            while(Objects.nonNull(readLine)) {
                try {
                    count++;
//                    if(count>20) {
//                        break;
//                    }
                    logger.info("readline: {}", readLine);
                    String[] arr = readLine.split(",");
                    if(arr.length != 4) {
                        message +="Invalid Row: "+ readLine + "\n";
                        continue;
                    }
                    Long applicationId = Long.valueOf(arr[1]);
                    LendingEnum.LENDER lender = LendingEnum.LENDER.valueOf(arr[2]);
                    Long retryCount = Long.valueOf(arr[3]);
                    LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).get();
                    if(Objects.isNull(lendingApplication.getNbfcId()) &&
                            Objects.isNull(lendingApplication.getDisburseTimestamp()) &&
                            Objects.isNull(lendingApplication.getLoanDisbursalStatus())
                    ) {
                        LendingNbfcRetry lendingNbfcRetry = lendingNbfcRetryDao.findTopByApplicationId(lendingApplication.getId());
                        if(Objects.isNull(lendingNbfcRetry)) {
                            lendingNbfcRetry = new LendingNbfcRetry();
                            lendingNbfcRetry.setMerchantId(lendingApplication.getMerchant().getId());
                            lendingNbfcRetry.setApplicationId(lendingApplication.getId());
                        } else if("PENDING".equalsIgnoreCase(lendingNbfcRetry.getNbfcStatus())) {
                            logger.info("Nbfc status is pending for applicationId: {}", lendingApplication.getId());
                            continue;
                        }
                        lendingNbfcRetry.setNbfcStatus("FAILED");
                        lendingNbfcRetry.setStatus("PENDING");
                        lendingNbfcRetry.setRetryCount(retryCount);
                        lendingNbfcRetry.setLender(lender.name());
                        lendingNbfcRetry.setRetryAfter(new Date());
                        lendingNbfcRetryDao.save(lendingNbfcRetry);
                        lendingApplication.setLender(lender.name());
                        lendingApplicationDao.save(lendingApplication);
                    }
                } catch (Exception exception) {
                    message +="Failed for row: "+ readLine + " " + exception.getMessage() + "\n" ;
                    logger.error("Exception Occurred while adding retry nbfc : {}", exception.getMessage(), exception);
                } finally {
                    readLine = nbfcRetryFileReader.readLine();
                }
            }
            return new ResponseDTO(true, message);
        } catch (Exception e) {
            logger.error("Error occurred while processing nbfc retry file: {}",e);
        }
        return new ResponseDTO(true, "Something went wrong");
    }

}
