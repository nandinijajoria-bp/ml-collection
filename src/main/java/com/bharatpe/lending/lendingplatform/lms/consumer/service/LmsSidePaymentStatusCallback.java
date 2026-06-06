package com.bharatpe.lending.lendingplatform.lms.consumer.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.service.MandateCancellationService;
import com.bharatpe.lending.common.dao.LmsPaymentDetailsDao;
import com.bharatpe.lending.common.entity.LmsPaymentDetails;
import com.bharatpe.lending.common.enums.LMSPaymentStatus;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.transaction.Transactional;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class LmsSidePaymentStatusCallback {

    private final LmsPaymentDetailsDao lmsPaymentDetailsDao;
    private final LendingPaymentScheduleDao lendingPaymentScheduleDao;
    private final LendingApplicationDao lendingApplicationDao;
    private final MandateCancellationService mandateCancellationService;

    private final ExecutorService mandateCancellationExecutor = Executors.newFixedThreadPool(5);

    @PreDestroy
    public void shutdownMandateCancellationExecutor() {
        if (!mandateCancellationExecutor.isShutdown()) {
            mandateCancellationExecutor.shutdown();
            try {
                if (!mandateCancellationExecutor.awaitTermination(10, TimeUnit.MINUTES)) {
                    mandateCancellationExecutor.shutdownNow();
                    mandateCancellationExecutor.awaitTermination(1, TimeUnit.MINUTES);
                }
            } catch (InterruptedException e) {
                mandateCancellationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
                log.error("1LMS mandate cancellation executor shutdown interrupted", e);
            }
        }
    }

    @Transactional
    public void updateLmsPostingStatus(String terminalOrderId) {
        try {
            terminalOrderId = terminalOrderId.replace("\"", "");
            LmsPaymentDetails lmsPaymentDetails = lmsPaymentDetailsDao.findByTerminalOrderId(terminalOrderId);
            lmsPaymentDetails.setSentToLms(LMSPaymentStatus.SUCCESS);
            lmsPaymentDetails.setUpdatedAt(new Date());
            lmsPaymentDetailsDao.save(lmsPaymentDetails);
            if("YES".equalsIgnoreCase(lmsPaymentDetails.getIsEligibleForForeclosure())){
                closeLoanAndUpdateStatus(lmsPaymentDetails);
            }
        } catch (Exception e) {
            log.error("Exception in processing lms payment posting callback: {}", e.getMessage(), e);
        }
    }

    @Transactional
    private void closeLoanAndUpdateStatus(LmsPaymentDetails lmsPaymentDetails) {
        //Fetching LPS entity based on bpLoanId received from callback
        LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(lmsPaymentDetails.getBpLoanId());
        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(lendingApplication.getId());
        log.info("update status closed for applicationId {} and loanDetails {}",lendingPaymentSchedule.getApplicationId(),lendingPaymentSchedule);

        lendingPaymentSchedule.setStatus("CLOSED");
        lendingPaymentSchedule.setDueAmount(0.0);
        lendingPaymentSchedule.setDueInterest(0.0);
        lendingPaymentSchedule.setDuePrinciple(0.0);
        lendingPaymentSchedule.setDueOtherCharges(0.0);
        lendingPaymentSchedule.setDuePenalty(0.0);
        lendingPaymentSchedule.setEdiRemainingCount(0);
        lendingPaymentSchedule.setClosingDate(new Date());
        lendingPaymentScheduleDao.save(lendingPaymentSchedule);

        final LendingPaymentSchedule closedLoan = lendingPaymentSchedule;
        mandateCancellationExecutor.execute(() -> mandateCancellationService.cancelPendingMandateExecutions(closedLoan));
    }
}
