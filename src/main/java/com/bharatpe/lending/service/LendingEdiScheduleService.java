package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.CommonResponse;
import com.bharatpe.lending.dto.EdiScheduleDTO;
import com.bharatpe.lending.dto.EdiScheduleV2DTO;
import com.bharatpe.lending.util.Finance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

@Service
public class LendingEdiScheduleService {

    private final Logger logger = LoggerFactory.getLogger(LendingEdiScheduleService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LiquiloansService liquiloansService;

    public CommonResponse getEdiSchedule(Long merchantId, Long applicationId) {
        logger.info("Creating EDI Schedule for applicationId:{}", applicationId);
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if (lendingApplication == null) {
                return new CommonResponse(false, "Lending application not found");
            }
            int installmentNo = 1;
            int ediCount = lendingApplication.getPayableDays().intValue();
            Double openingBalance = lendingApplication.getLoanAmount();
            String construct = lendingApplication.getLoanConstruct();
            double totalInterest = 0D;
            Double totalPrincipal = 0D;
            List<EdiScheduleDTO> ediSchedules = new ArrayList<>();
            double procFee = lendingApplication.getProcessingFee();
//            if(procFee > 0D) {
//                ediSchedules.add(createProcFeeSchedule(lendingApplication));
//            }
            if("CONSTRUCT_2".equals(construct) || "CONSTRUCT_3".equals(construct)) {
                int ioInstallmentNo = 1;
                while(ioInstallmentNo <= lendingApplication.getIoPayableDays()) {
                    EdiScheduleDTO currentSchedule = new EdiScheduleDTO();
                    currentSchedule.setSerialNumber(installmentNo);
                    currentSchedule.setInterest(lendingApplication.getIoEdi());
                    currentSchedule.setPrincipal(0D);
                    currentSchedule.setEdiAmount(lendingApplication.getIoEdi().intValue());
                    ediSchedules.add(currentSchedule);
                    totalInterest = totalInterest + lendingApplication.getIoEdi();
                    installmentNo++;
                    ioInstallmentNo++;
                }
            }

            double reducingInterestRateDaily = Finance.rate(ediCount, lendingApplication.getEdi().intValue(), lendingApplication.getLoanAmount());
            int normalEdIinstallmentNo = 1;
            while (normalEdIinstallmentNo <= ediCount) {
                Double principal = round(Finance.ppmt(reducingInterestRateDaily, normalEdIinstallmentNo, ediCount, -1 * lendingApplication.getLoanAmount()));
                double interest = round(lendingApplication.getEdi().intValue() - principal);
                if(normalEdIinstallmentNo == ediCount) {
                    if(!lendingApplication.getLoanAmount().equals(totalPrincipal + principal)) {
                        double diff = lendingApplication.getLoanAmount() - (totalPrincipal + principal);
                        principal = round(lendingApplication.getLoanAmount() - totalPrincipal);
                        interest = round(interest - diff);
                    }
                }
                totalPrincipal = totalPrincipal + principal;
                totalInterest = totalInterest + interest;
                EdiScheduleDTO currentSchedule = new EdiScheduleDTO();
                currentSchedule.setSerialNumber(installmentNo);
                currentSchedule.setInterest(interest);
                currentSchedule.setPrincipal(principal);
                currentSchedule.setEdiAmount(lendingApplication.getEdi().intValue());
                ediSchedules.add(currentSchedule);
                openingBalance = openingBalance - principal;
                installmentNo++;
                normalEdIinstallmentNo++;
            }
            return new CommonResponse(true, "success", ediSchedules);
        } catch(Exception ex) {
            logger.error("Exception while creating schedule for applicationId {}, Exception is {}", applicationId, ex);
        }
        return new CommonResponse(false, "Something went wrong");
    }

    private EdiScheduleDTO createProcFeeSchedule(LendingApplication lendingApplication) {
        Double procFee = lendingApplication.getProcessingFee();
        EdiScheduleDTO schedule = new EdiScheduleDTO();
        schedule.setSerialNumber(0);
        schedule.setInterest(0D);
        schedule.setPrincipal(0D);
        schedule.setEdiAmount(procFee.intValue());
        return schedule;
    }

    private static double round(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public CommonResponse getEdiScheduleV2(Long merchantId, Long applicationId) {
        logger.info("Creating EDI Schedule V2 for applicationId:{}", applicationId);
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if (lendingApplication == null) {
                return new CommonResponse(false, "Lending application not found");
            }
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndApplicationId(merchantId, applicationId);
            int installmentNo = 1;
            int ediCount = lendingApplication.getPayableDays().intValue();
            Double openingBalance = lendingApplication.getLoanAmount();
            String construct = lendingApplication.getLoanConstruct();
            double totalInterest = 0D;
            Double totalPrincipal = 0D;
            List<EdiScheduleV2DTO> ediSchedules = new ArrayList<>();
            double procFee = lendingApplication.getProcessingFee();
//            if(procFee > 0D) {
//                ediSchedules.add(createProcFeeSchedule(lendingApplication));
//            }
            Calendar cal = Calendar.getInstance();
            if("CONSTRUCT_2".equals(construct) || "CONSTRUCT_3".equals(construct)) {
                int ioInstallmentNo = 1;
                if (lendingPaymentSchedule != null) {
                    cal.setTime(lendingPaymentSchedule.getInterestOnlyStartDate());
                }
                while(ioInstallmentNo <= lendingApplication.getIoPayableDays()) {
                    if(cal.get(Calendar.DAY_OF_WEEK) == liquiloansService.getOffDayNumber(LenderOffDays.valueOf(lendingApplication.getLender()).getOffDay())) {
                        cal.add(Calendar.DAY_OF_MONTH, 1);
                        continue;
                    }
                    EdiScheduleV2DTO currentSchedule = new EdiScheduleV2DTO();
                    currentSchedule.setSerialNumber(installmentNo);
                    currentSchedule.setInterest(lendingApplication.getIoEdi());
                    currentSchedule.setPrincipal(0D);
                    currentSchedule.setEdiAmount(lendingApplication.getIoEdi().intValue());
                    currentSchedule.setDate(cal.getTime());
                    currentSchedule.setBalance(openingBalance);
                    ediSchedules.add(currentSchedule);
                    totalInterest = totalInterest + lendingApplication.getIoEdi();
                    installmentNo++;
                    ioInstallmentNo++;
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                }
            }
            if (lendingPaymentSchedule != null) {
                cal.setTime(lendingPaymentSchedule.getStartDate());
            }

            double reducingInterestRateDaily = Finance.rate(ediCount, lendingApplication.getEdi().intValue(), lendingApplication.getLoanAmount());
            int normalEdIinstallmentNo = 1;
            while (normalEdIinstallmentNo <= ediCount) {
                if (cal.get(Calendar.DAY_OF_WEEK) == liquiloansService.getOffDayNumber(LenderOffDays.valueOf(lendingApplication.getLender()).getOffDay())) {
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                    continue;
                }
                Double principal = round(Finance.ppmt(reducingInterestRateDaily, normalEdIinstallmentNo, ediCount, -1 * lendingApplication.getLoanAmount()));
                double interest = round(lendingApplication.getEdi().intValue() - principal);
                if(normalEdIinstallmentNo == ediCount) {
                    if(!lendingApplication.getLoanAmount().equals(totalPrincipal + principal)) {
                        double diff = lendingApplication.getLoanAmount() - (totalPrincipal + principal);
                        principal = round(lendingApplication.getLoanAmount() - totalPrincipal);
                        interest = round(interest - diff);
                    }
                }
                totalPrincipal = totalPrincipal + principal;
                totalInterest = totalInterest + interest;
                EdiScheduleV2DTO currentSchedule = new EdiScheduleV2DTO();
                currentSchedule.setSerialNumber(installmentNo);
                currentSchedule.setInterest(interest);
                currentSchedule.setPrincipal(principal);
                currentSchedule.setEdiAmount(lendingApplication.getEdi().intValue());
                currentSchedule.setDate(cal.getTime());
                currentSchedule.setBalance(round(openingBalance));
                ediSchedules.add(currentSchedule);
                openingBalance = openingBalance - principal;
                installmentNo++;
                normalEdIinstallmentNo++;
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            return new CommonResponse(true, "success", ediSchedules);
        } catch(Exception ex) {
            logger.error("Exception while creating schedule V2 for applicationId {}, Exception is {}, Stacktrace : {}", applicationId, ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return new CommonResponse(false, "Something went wrong");
    }
}
