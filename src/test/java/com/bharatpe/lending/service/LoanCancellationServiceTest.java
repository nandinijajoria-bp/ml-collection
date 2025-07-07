package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingRefundAuditDao;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingLedgerDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

class LoanCancellationServiceTest {

    @InjectMocks
    LoanCancellationService loanCancellationService;

    @Mock
    LendingLedgerDao lendingLedgerDao;
    @Mock
    LendingRefundAuditDao lendingRefundAuditDao;
    @Mock
    DateTimeUtil dateTimeUtil;

    @Mock
    LendingPaymentSchedule lendingPaymentSchedule;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(dateTimeUtil.getCurrentDate()).thenReturn(new Date());
        when(lendingPaymentSchedule.getId()).thenReturn(1L);
        when(lendingPaymentSchedule.getTotalPayableAmount()).thenReturn(1000.0);
        when(lendingPaymentSchedule.getLoanAmount()).thenReturn(800.0);
        when(lendingPaymentSchedule.getInterest()).thenReturn(200.0);
        when(lendingPaymentSchedule.getMerchantId()).thenReturn(10L);
    }

    @Test
    void testUpdateLendingLedgerAndRefundAudit_PositiveAndNegativeEdiExist() {
        Map<String, Object> negativeEdi = new HashMap<>();
        negativeEdi.put("amount", BigDecimal.valueOf(-100.0));
        negativeEdi.put("principle", BigDecimal.valueOf(-80.0));
        negativeEdi.put("interest", BigDecimal.valueOf(-20.0));

        Map<String, Object> positiveEdi = new HashMap<>();
        positiveEdi.put("amount", BigDecimal.valueOf(200.0));
        positiveEdi.put("principle", BigDecimal.valueOf(160.0));
        positiveEdi.put("interest", BigDecimal.valueOf(40.0));

        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(negativeEdi);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(positiveEdi);

        LendingLedger ledgerEntry = new LendingLedger();
        ledgerEntry.setAmount(200.0);
        ledgerEntry.setTerminalOrderId("TID123");
        ledgerEntry.setAdjustmentMode("MODE1");
        ledgerEntry.setId(99L);
        when(lendingLedgerDao.positiveEdiEntries(1L)).thenReturn(Collections.singletonList(ledgerEntry));

        loanCancellationService.updateLendingLedgerAndRefundAudit(123L, lendingPaymentSchedule);

        verify(lendingRefundAuditDao, times(1)).save(any());
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testUpdateLendingLedgerAndRefundAudit_PositiveEdiZero() {
        Map<String, Object> negativeEdi = new HashMap<>();
        negativeEdi.put("amount", BigDecimal.valueOf(-100.0));
        negativeEdi.put("principle", BigDecimal.valueOf(-80.0));
        negativeEdi.put("interest", BigDecimal.valueOf(-20.0));

        Map<String, Object> positiveEdi = new HashMap<>();
        positiveEdi.put("amount", BigDecimal.valueOf(0.0));
        positiveEdi.put("principle", BigDecimal.valueOf(0.0));
        positiveEdi.put("interest", BigDecimal.valueOf(0.0));

        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(negativeEdi);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(positiveEdi);

        loanCancellationService.updateLendingLedgerAndRefundAudit(123L, lendingPaymentSchedule);

        verify(lendingRefundAuditDao, never()).save(any());
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testUpdateLendingLedgerAndRefundAudit_NullEdi() {
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(null);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(null);

        loanCancellationService.updateLendingLedgerAndRefundAudit(123L, lendingPaymentSchedule);

        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testUpdateLendingLedgerAndRefundAudit_PositiveEdiEntriesWithNullTerminalOrderId() {
        Map<String, Object> negativeEdi = new HashMap<>();
        negativeEdi.put("amount", BigDecimal.valueOf(-100.0));
        negativeEdi.put("principle", BigDecimal.valueOf(-80.0));
        negativeEdi.put("interest", BigDecimal.valueOf(-20.0));

        Map<String, Object> positiveEdi = new HashMap<>();
        positiveEdi.put("amount", BigDecimal.valueOf(200.0));
        positiveEdi.put("principle", BigDecimal.valueOf(160.0));
        positiveEdi.put("interest", BigDecimal.valueOf(40.0));

        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(negativeEdi);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(positiveEdi);

        LendingLedger ledgerEntry = new LendingLedger();
        ledgerEntry.setAmount(200.0);
        ledgerEntry.setTerminalOrderId(null);
        ledgerEntry.setAdjustmentMode("MODE1");
        ledgerEntry.setId(99L);
        when(lendingLedgerDao.positiveEdiEntries(1L)).thenReturn(Collections.singletonList(ledgerEntry));

        loanCancellationService.updateLendingLedgerAndRefundAudit(123L, lendingPaymentSchedule);

        verify(lendingRefundAuditDao, times(1)).save(any());
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testBothEdiNull() {
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(null);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(null);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testNegativeEdiEmptyMap() {
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(new HashMap<>());
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(new HashMap<>());
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testNegativeEdiMissingKeys() {
        Map<String, Object> neg = new HashMap<>();
        neg.put("amount", null);
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(neg);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(neg);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testPositiveEdiAmountNegative() {
        Map<String, Object> pos = new HashMap<>();
        pos.put("amount", BigDecimal.valueOf(-10));
        pos.put("principle", BigDecimal.ZERO);
        pos.put("interest", BigDecimal.ZERO);
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(pos);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(pos);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingRefundAuditDao, never()).save(any());
    }

    @Test
    void testPositiveEdiAmountZero() {
        Map<String, Object> pos = new HashMap<>();
        pos.put("amount", BigDecimal.ZERO);
        pos.put("principle", BigDecimal.ZERO);
        pos.put("interest", BigDecimal.ZERO);
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(pos);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(pos);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingRefundAuditDao, never()).save(any());
    }

    @Test
    void testPositiveEdiAmountPositive() {
        Map<String, Object> pos = new HashMap<>();
        pos.put("amount", BigDecimal.valueOf(100));
        pos.put("principle", BigDecimal.valueOf(80));
        pos.put("interest", BigDecimal.valueOf(20));
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(pos);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(pos);
        LendingLedger entry = new LendingLedger();
        entry.setAmount(100.0);
        entry.setTerminalOrderId("TID");
        entry.setAdjustmentMode("MODE");
        entry.setId(1L);
        when(lendingLedgerDao.positiveEdiEntries(1L)).thenReturn(Collections.singletonList(entry));
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingRefundAuditDao, times(1)).save(any());
    }

    @Test
    void testPositiveEdiEntriesEmpty() {
        Map<String, Object> pos = new HashMap<>();
        pos.put("amount", BigDecimal.valueOf(100));
        pos.put("principle", BigDecimal.valueOf(80));
        pos.put("interest", BigDecimal.valueOf(20));
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(pos);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(pos);
        when(lendingLedgerDao.positiveEdiEntries(1L)).thenReturn(Collections.emptyList());
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingRefundAuditDao, never()).save(any());
    }

    @Test
    void testPositiveEdiEntryWithNullTerminalOrderId() {
        Map<String, Object> pos = new HashMap<>();
        pos.put("amount", BigDecimal.valueOf(100));
        pos.put("principle", BigDecimal.valueOf(80));
        pos.put("interest", BigDecimal.valueOf(20));
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(pos);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(pos);
        LendingLedger entry = new LendingLedger();
        entry.setAmount(100.0);
        entry.setTerminalOrderId(null);
        entry.setAdjustmentMode("MODE");
        entry.setId(42L);
        when(lendingLedgerDao.positiveEdiEntries(1L)).thenReturn(Collections.singletonList(entry));
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingRefundAuditDao, times(1)).save(any());
    }

    @Test
    void testNegativeEdiAmountPositive() {
        Map<String, Object> neg = new HashMap<>();
        neg.put("amount", BigDecimal.valueOf(100));
        neg.put("principle", BigDecimal.valueOf(80));
        neg.put("interest", BigDecimal.valueOf(20));
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(neg);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(neg);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testNegativeEdiAmountZero() {
        Map<String, Object> neg = new HashMap<>();
        neg.put("amount", BigDecimal.ZERO);
        neg.put("principle", BigDecimal.ZERO);
        neg.put("interest", BigDecimal.ZERO);
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(neg);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(neg);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testNegativeEdiAmountNegative() {
        Map<String, Object> neg = new HashMap<>();
        neg.put("amount", BigDecimal.valueOf(-100));
        neg.put("principle", BigDecimal.valueOf(-80));
        neg.put("interest", BigDecimal.valueOf(-20));
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(neg);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(neg);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testPositiveEdiPrincipleNull() {
        Map<String, Object> pos = new HashMap<>();
        pos.put("amount", BigDecimal.valueOf(100));
        pos.put("principle", null);
        pos.put("interest", BigDecimal.valueOf(20));
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(pos);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(pos);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testPositiveEdiInterestNull() {
        Map<String, Object> pos = new HashMap<>();
        pos.put("amount", BigDecimal.valueOf(100));
        pos.put("principle", BigDecimal.valueOf(80));
        pos.put("interest", null);
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(pos);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(pos);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testNegativeEdiPrincipleNull() {
        Map<String, Object> neg = new HashMap<>();
        neg.put("amount", BigDecimal.valueOf(-100));
        neg.put("principle", null);
        neg.put("interest", BigDecimal.valueOf(-20));
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(neg);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(neg);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testNegativeEdiInterestNull() {
        Map<String, Object> neg = new HashMap<>();
        neg.put("amount", BigDecimal.valueOf(-100));
        neg.put("principle", BigDecimal.valueOf(-80));
        neg.put("interest", null);
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(neg);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(neg);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        verify(lendingLedgerDao, times(2)).save(any());
    }

    @Test
    void testCreateLendingLedgerNotCalledForZeroAmount() {
        // Simulate newNegativeEdiAmount and newPositiveEdiAmount both zero
        when(lendingPaymentSchedule.getTotalPayableAmount()).thenReturn(0.0);
        Map<String, Object> edi = new HashMap<>();
        edi.put("amount", BigDecimal.ZERO);
        edi.put("principle", BigDecimal.ZERO);
        edi.put("interest", BigDecimal.ZERO);
        when(lendingLedgerDao.totalNegativeEdiAmount(1L)).thenReturn(edi);
        when(lendingLedgerDao.totalPositiveEdiAmount(1L)).thenReturn(edi);
        loanCancellationService.updateLendingLedgerAndRefundAudit(1L, lendingPaymentSchedule);
        // Should not call save for zero amount
        verify(lendingLedgerDao, never()).save(any());
    }

    @Test
    void testCheckIfEDIExists_NullInput() {
        Map<String, Object> result = loanCancellationService.checkIfEDIExists(null);
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.get("amount"));
        assertEquals(BigDecimal.ZERO, result.get("principle"));
        assertEquals(BigDecimal.ZERO, result.get("interest"));
    }

    @Test
    void testCheckIfEDIExists_EmptyMap() {
        Map<String, Object> edi = new HashMap<>();
        Map<String, Object> result = loanCancellationService.checkIfEDIExists(edi);
        assertFalse(CollectionUtils.isEmpty(result));
        assertEquals(BigDecimal.ZERO, result.get("amount"));
        assertEquals(BigDecimal.ZERO, result.get("principle"));
        assertEquals(BigDecimal.ZERO, result.get("interest"));
    }

    @Test
    void testCheckIfEDIExists_MissingKeys() {
        Map<String, Object> edi = new HashMap<>();
        edi.put("amount", BigDecimal.valueOf(100));
        Map<String, Object> result = loanCancellationService.checkIfEDIExists(edi);
        assertEquals(BigDecimal.valueOf(100), result.get("amount"));
        assertEquals(BigDecimal.ZERO, result.get("principle"));
        assertEquals(BigDecimal.ZERO, result.get("interest"));
    }

    @Test
    void testCheckIfEDIExists_NullValues() {
        Map<String, Object> edi = new HashMap<>();
        edi.put("amount", null);
        edi.put("principle", null);
        edi.put("interest", null);
        Map<String, Object> result = loanCancellationService.checkIfEDIExists(edi);
        assertEquals(BigDecimal.ZERO, result.get("amount"));
        assertEquals(BigDecimal.ZERO, result.get("principle"));
        assertEquals(BigDecimal.ZERO, result.get("interest"));
    }

    @Test
    void testCheckIfEDIExists_ValidValues() {
        Map<String, Object> edi = new HashMap<>();
        edi.put("amount", BigDecimal.valueOf(200));
        edi.put("principle", BigDecimal.valueOf(150));
        edi.put("interest", BigDecimal.valueOf(50));
        Map<String, Object> result = loanCancellationService.checkIfEDIExists(edi);
        assertEquals(BigDecimal.valueOf(200), result.get("amount"));
        assertEquals(BigDecimal.valueOf(150), result.get("principle"));
        assertEquals(BigDecimal.valueOf(50), result.get("interest"));
    }
}