package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingLedgerDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@RunWith(SpringJUnit4ClassRunner.class)
class NachBounceChargesServiceTest {
    @Mock
    private LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;

    @Mock
    private PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Mock
    private LendingLedgerDao lendingLedgerDao;

    @InjectMocks
    private NachBounceChargesService nachBounceChargesService;

    @BeforeEach
    public void setup() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void getNachCharges() {
        // Arrange
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -7); // Subtract 6 days
        Date loanStartDate = calendar.getTime();
        String nbfc = "PAYU";
        Double expectedNachCharge = 0.0; // Example charge, can be adjusted as needed
        LendingPaymentSchedule activeLoan = new LendingPaymentSchedule();
        activeLoan.setMerchantId(123L);
        activeLoan.setId(456L);
        activeLoan.setStartDate(loanStartDate);
        activeLoan.setNbfc(nbfc);
        when(lendingPullPaymentDaoSlave.findFailedNachPullInLastMonth(123L, 456L, "NACH", loanStartDate, DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), -6), "FAILED"))
                .thenReturn(0);

        // Act
        Double result = nachBounceChargesService.getNachCharges(activeLoan);

        // Assert
        assertEquals(expectedNachCharge, result);  // We expect the charge to be equal to payUNachBounceCharge

    }

    @Test
    void getNachChargesWithNachBounce() {
        // Arrange
        ReflectionTestUtils.setField(nachBounceChargesService, "payUNachBounceCharge", 500);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -7); // Subtract 6 days
        Date loanStartDate = calendar.getTime();
        String nbfc = "PAYU";
        Double expectedNachCharge = 500.0; // Example charge, can be adjusted as needed
        LendingPaymentSchedule activeLoan = new LendingPaymentSchedule();
        activeLoan.setMerchantId(123L);
        activeLoan.setId(456L);
        activeLoan.setStartDate(loanStartDate);
        activeLoan.setNbfc(nbfc);
        when(lendingPullPaymentDaoSlave.findFailedNachPullInLastMonth(123L, 456L, "NACH", DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), -6), DateTimeUtil.getCurrentDayStartTime(),"FAILED"))
                .thenReturn(1);

        // Act
        Double result = nachBounceChargesService.getNachCharges(activeLoan);

        // Assert
        assertEquals(expectedNachCharge, result);  // We expect the charge to be equal to payUNachBounceCharge

    }

    @Test
    public void testCheckForNachBounce_noFailedNachPulls() {
        // Arrange
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -7); // Subtract 6 days
        Date loanStartDate = calendar.getTime();
        LendingPaymentSchedule activeLoan = new LendingPaymentSchedule();
        activeLoan.setMerchantId(123L);
        activeLoan.setId(456L);
        activeLoan.setStartDate(loanStartDate);
        when(lendingPullPaymentDaoSlave.findFailedNachPullInLastMonth(123L, 456L, "NACH", loanStartDate, DateTimeUtil.addDays(DateTimeUtil.getCurrentDayStartTime(), -6), "FAILED"))
                .thenReturn(0);

        // Act
        boolean result = nachBounceChargesService.checkForNachBounce(activeLoan);

        // Assert
        assertFalse(result);
        verify(lendingPullPaymentDaoSlave, times(1)).findFailedNachPullInLastMonth(anyLong(), anyLong(), eq("NACH"), any(Date.class), any(Date.class), eq("FAILED"));
    }

    @Test
    public void testCreateCharges() {
        // Arrange
        ReflectionTestUtils.setField(nachBounceChargesService, "payUNachBounceCharge", 500);
        String requestId = "testRequestId";
        LendingPaymentSchedule activeLoan = new LendingPaymentSchedule();
        activeLoan.setMerchantId(123L);
        activeLoan.setId(456L);
        activeLoan.setNbfc("PAYU");

        // Act
        nachBounceChargesService.createCharges(activeLoan, requestId);

        // Assert
        // Verify that the penaltyFeeLedgerDao.save() method was called once with a PenaltyFeeLedger object
        verify(penaltyFeeLedgerDao, times(1)).save(any(PenaltyFeeLedger.class));
        // Verify that the lendingLedgerDao.save() method was called once with a LendingLedger object
        verify(lendingLedgerDao, times(1)).save(any(LendingLedger.class));

    }
}