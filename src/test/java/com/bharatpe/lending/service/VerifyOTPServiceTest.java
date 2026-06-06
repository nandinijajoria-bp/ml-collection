package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.service.MandateCancellationService;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifyOTPServiceTest {

    @Mock
    private LendingApplicationDao lendingApplicationDao;
    @Mock
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;
    @Mock
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;
    @Mock
    private LendingLedgerDao lendingLedgerDao;
    @Mock
    private LendingCollectionExcessDao lendingCollectionExcessDao;
    @Mock
    private LendingCollectionAuditService lendingCollectionAuditService;
    @Mock
    private AutoPayUPIDao autoPayUPIDao;
    @Mock
    private MandateCancellationService mandateCancellationService;
    @Mock
    private PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @InjectMocks
    private VerifyOTPService verifyOTPService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @AfterEach
    void tearDown() {
        verifyOTPService.executorService.shutdown();
    }

    @Test
    void closePreviousLoanAfterSuccessfulTopupCreation_whenActiveAutoPayExists_schedulesMandateCancellation() {
        long topupApplicationId = 200L;
        long previousApplicationId = 100L;

        LendingApplication topupApp = new LendingApplication();
        topupApp.setId(topupApplicationId);
        topupApp.setLoanType("TOPUP");
        topupApp.setLoanAmount(100_000D);
        topupApp.setDisbursalAmount(90_000D);
        topupApp.setProcessingFee(5_000D);

        LendingApplicationDetails details = new LendingApplicationDetails();
        details.setPrevAppId(previousApplicationId);

        LendingApplication previousApp = new LendingApplication();
        previousApp.setId(previousApplicationId);
        previousApp.setLender("TRILLIONLOANS");

        LendingPaymentSchedule previousLoan = new LendingPaymentSchedule();
        previousLoan.setId(501L);
        previousLoan.setApplicationId(previousApplicationId);
        previousLoan.setMerchantId(7L);
        previousLoan.setStatus("ACTIVE");
        previousLoan.setLmsSource("LEGACY");
        previousLoan.setNbfc("TRILLIONLOANS");
        previousLoan.setDueAmount(5_000D);
        previousLoan.setDueInterest(0D);
        previousLoan.setDuePenalty(null);
        previousLoan.setPaidAmount(0D);
        previousLoan.setPaidPrinciple(0D);
        previousLoan.setPaidInterest(0D);
        previousLoan.setLoanApplication(previousApp);

        AutoPayUPI autoPayUPI = new AutoPayUPI();
        autoPayUPI.setId(1L);

        when(lendingApplicationDao.findById(topupApplicationId)).thenReturn(Optional.of(topupApp));
        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(topupApplicationId)).thenReturn(details);
        when(lendingApplicationDao.findById(previousApplicationId)).thenReturn(Optional.of(previousApp));
        when(lendingPaymentScheduleDao.findByApplicationId(previousApplicationId)).thenReturn(previousLoan);
        when(lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(anyLong(), anyLong(), eq("ACTIVE")))
                .thenReturn(Collections.emptyList());
        when(autoPayUPIDao.findByApplicationIdAndStatus(previousApplicationId, AutoPayStatusEnum.ACTIVE.name()))
                .thenReturn(autoPayUPI);

        Map<String, Object> response = verifyOTPService.closePreviousLoanAfterSuccessfulTopupCreation(topupApplicationId);

        assertEquals("successfully settled previous loan", response.get("message"));
        verify(autoPayUPIDao).save(autoPayUPI);

        ArgumentCaptor<LendingPaymentSchedule> captor = ArgumentCaptor.forClass(LendingPaymentSchedule.class);
        verify(mandateCancellationService, timeout(5_000)).cancelPendingMandateExecutions(captor.capture());
        assertEquals(501L, (long) captor.getValue().getId());
        assertEquals(previousApplicationId, (long) captor.getValue().getApplicationId());
    }

    @Test
    void closePreviousLoanAfterSuccessfulTopupCreation_whenNoActiveAutoPay_doesNotCancelMandates() {
        long topupApplicationId = 201L;
        long previousApplicationId = 101L;

        LendingApplication topupApp = new LendingApplication();
        topupApp.setId(topupApplicationId);
        topupApp.setLoanType("TOPUP");
        topupApp.setLoanAmount(100_000D);
        topupApp.setDisbursalAmount(90_000D);
        topupApp.setProcessingFee(5_000D);

        LendingApplicationDetails details = new LendingApplicationDetails();
        details.setPrevAppId(previousApplicationId);

        LendingApplication previousApp = new LendingApplication();
        previousApp.setId(previousApplicationId);
        previousApp.setLender("TRILLIONLOANS");

        LendingPaymentSchedule previousLoan = new LendingPaymentSchedule();
        previousLoan.setId(502L);
        previousLoan.setApplicationId(previousApplicationId);
        previousLoan.setMerchantId(8L);
        previousLoan.setStatus("ACTIVE");
        previousLoan.setLmsSource("LEGACY");
        previousLoan.setNbfc("TRILLIONLOANS");
        previousLoan.setDueAmount(5_000D);
        previousLoan.setDueInterest(0D);
        previousLoan.setDuePenalty(null);
        previousLoan.setPaidAmount(0D);
        previousLoan.setPaidPrinciple(0D);
        previousLoan.setPaidInterest(0D);
        previousLoan.setLoanApplication(previousApp);

        when(lendingApplicationDao.findById(topupApplicationId)).thenReturn(Optional.of(topupApp));
        when(lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(topupApplicationId)).thenReturn(details);
        when(lendingApplicationDao.findById(previousApplicationId)).thenReturn(Optional.of(previousApp));
        when(lendingPaymentScheduleDao.findByApplicationId(previousApplicationId)).thenReturn(previousLoan);
        when(lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(anyLong(), anyLong(), eq("ACTIVE")))
                .thenReturn(Collections.emptyList());
        when(autoPayUPIDao.findByApplicationIdAndStatus(previousApplicationId, AutoPayStatusEnum.ACTIVE.name()))
                .thenReturn(null);

        verifyOTPService.closePreviousLoanAfterSuccessfulTopupCreation(topupApplicationId);

        verify(autoPayUPIDao, never()).save(any());
        verify(mandateCancellationService, never()).cancelPendingMandateExecutions(any());
    }
}
