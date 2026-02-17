package com.bharatpe.lending.lendingplatform.lms.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.lendingplatform.lms.dto.pojo.LmsLoanStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Date;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class LmsLoanStatusUpdateService {

    private final LendingPaymentScheduleDao lendingPaymentScheduleDao;
    private final LendingApplicationDao lendingApplicationDao;

    private static final String CLOSED = "CLOSED";
    private static final String FORECLOSED = "FORECLOSED";
    private static final String CANCELLED = "CANCELLED";
    public static final double ZERO_AMOUNT = 0.0;
    public static final int ZERO_COUNT = 0;

    @Transactional
    public void updateLoanStatus(LmsLoanStatus lmsLoanStatus) {
        LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(lmsLoanStatus.getBpLoanId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Lending application not found for bpLoanId: {}", lmsLoanStatus.getBpLoanId());
            return;
        }

        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(lendingApplication.getId());
        if (ObjectUtils.isEmpty(lendingPaymentSchedule)) {
            log.info("Payment schedule not found for applicationId: {}", lendingApplication.getId());
            return;
        }

        // if it is a BP LMS loan we cannot cancel it in LPS through 1LMS callback.
        if (!"1LMS".equals(lendingPaymentSchedule.getLmsSource())) {
            log.info("LMS Source is not 1LMS for applicationId: {}", lendingApplication.getId());
            return;
        }


        log.info("Updating loan status for applicationId: {} and loanDetails: {}", lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule);

        try {
            if (CLOSED.equals(lmsLoanStatus.getStatus()) || FORECLOSED.equals(lmsLoanStatus.getStatus())) {
                closeLoan(lendingPaymentSchedule);
                log.info("Loan is {} for applicationId: {}", lmsLoanStatus.getStatus(), lendingApplication.getId());
            } else if (CANCELLED.equals(lmsLoanStatus.getStatus())) {
                cancelLoan(lendingPaymentSchedule);
                log.info("Loan is {} for applicationId: {}", lmsLoanStatus.getStatus(), lendingApplication.getId());
            } else {
                log.warn("Unknown loan status: {} for applicationId: {}", lmsLoanStatus.getStatus(), lendingApplication.getId());
            }
        }catch (Exception ex){
            log.error("Error updating loan status for applicationId: {}. Error: {}", lendingApplication.getId(), ex.getMessage(), ex);
        }
    }

    public void closeLoan(LendingPaymentSchedule lendingPaymentSchedule) {
        lendingPaymentSchedule.setStatus(CLOSED);
        lendingPaymentSchedule.setDueAmount(ZERO_AMOUNT);
        lendingPaymentSchedule.setDueInterest(ZERO_AMOUNT);
        lendingPaymentSchedule.setDuePrinciple(ZERO_AMOUNT);
        lendingPaymentSchedule.setDueOtherCharges(ZERO_AMOUNT);
        lendingPaymentSchedule.setDuePenalty(ZERO_AMOUNT);
        lendingPaymentSchedule.setEdiRemainingCount(ZERO_COUNT);
        lendingPaymentSchedule.setClosingDate(new Date());
        lendingPaymentScheduleDao.save(lendingPaymentSchedule);
    }

    public void cancelLoan(LendingPaymentSchedule lendingPaymentSchedule) {
        lendingPaymentSchedule.setStatus(CANCELLED);
        lendingPaymentSchedule.setEdiRemainingCount(ZERO_COUNT);
        lendingPaymentSchedule.setPaidAmount(ZERO_AMOUNT);
        lendingPaymentSchedule.setPaidPrinciple(ZERO_AMOUNT);
        lendingPaymentSchedule.setPaidInterest(ZERO_AMOUNT);
        lendingPaymentSchedule.setDueAmount(ZERO_AMOUNT);
        lendingPaymentSchedule.setDuePrinciple(ZERO_AMOUNT);
        lendingPaymentSchedule.setDueInterest(ZERO_AMOUNT);
        lendingPaymentSchedule.setDuePenalty(ZERO_AMOUNT);
        lendingPaymentSchedule.setPaidPenalty(ZERO_AMOUNT);
        lendingPaymentScheduleDao.save(lendingPaymentSchedule);
    }

}