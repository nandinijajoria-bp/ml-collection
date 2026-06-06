package com.bharatpe.lending.lendingPlatform.lms.consumer.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.service.MandateCancellationService;
import com.bharatpe.lending.common.dao.LmsPaymentDetailsDao;
import com.bharatpe.lending.common.entity.LmsPaymentDetails;
import com.bharatpe.lending.common.enums.LMSPaymentStatus;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.lendingplatform.lms.consumer.service.LmsSidePaymentStatusCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LmsSidePaymentStatusCallbackTest {

    @Mock
    private LmsPaymentDetailsDao lmsPaymentDetailsDao;
    @Mock
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;
    @Mock
    private LendingApplicationDao lendingApplicationDao;
    @Mock
    private MandateCancellationService mandateCancellationService;

    @InjectMocks
    private LmsSidePaymentStatusCallback callback;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @AfterEach
    void tearDown() {
        callback.shutdownMandateCancellationExecutor();
    }

    @Test
    void updateLmsPostingStatus_whenEligibleForForeclosure_triggersAsyncMandateCancellationForClosedLoan() {
        String terminalOrderId = "order-123";
        LmsPaymentDetails details = new LmsPaymentDetails();
        details.setIsEligibleForForeclosure("YES");
        details.setBpLoanId("bp-loan-1");

        LendingApplication application = new LendingApplication();
        application.setId(99L);

        LendingPaymentSchedule schedule = new LendingPaymentSchedule();
        schedule.setApplicationId(99L);
        schedule.setId(1001L);
        schedule.setStatus("ACTIVE");

        when(lmsPaymentDetailsDao.findByTerminalOrderId(terminalOrderId)).thenReturn(details);
        when(lendingApplicationDao.findByExternalLoanId("bp-loan-1")).thenReturn(application);
        when(lendingPaymentScheduleDao.findByApplicationId(99L)).thenReturn(schedule);

        callback.updateLmsPostingStatus(terminalOrderId);

        assertEquals(LMSPaymentStatus.SUCCESS, details.getSentToLms());
        verify(lendingPaymentScheduleDao).save(schedule);
        assertEquals("CLOSED", schedule.getStatus());

        ArgumentCaptor<LendingPaymentSchedule> captor = ArgumentCaptor.forClass(LendingPaymentSchedule.class);
        verify(mandateCancellationService, timeout(5000)).cancelPendingMandateExecutions(captor.capture());
        assertEquals("CLOSED", captor.getValue().getStatus());
        assertEquals(1001L, Optional.ofNullable(captor.getValue().getId()));
    }

    @Test
    void updateLmsPostingStatus_whenNotEligibleForForeclosure_doesNotCancelMandates() {
        String terminalOrderId = "order-456";
        LmsPaymentDetails details = new LmsPaymentDetails();
        details.setIsEligibleForForeclosure("NO");
        details.setBpLoanId("bp-loan-2");

        when(lmsPaymentDetailsDao.findByTerminalOrderId(terminalOrderId)).thenReturn(details);

        callback.updateLmsPostingStatus(terminalOrderId);

        verify(lendingApplicationDao, never()).findByExternalLoanId(any());
        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        verify(mandateCancellationService, never()).cancelPendingMandateExecutions(any());
    }

}
