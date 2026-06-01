package com.bharatpe.lending.service;

import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingRefundAuditDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.query.entity.LendingRefundAuditSlave;
import com.bharatpe.lending.dto.RefundStatusResponseDTO;
import com.bharatpe.lending.util.LoanUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.CollectionUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundServiceTest {

    @InjectMocks
    private RefundService refundService;

    @Mock
    private LendingPaymentScheduleDaoSlave lendingPaymentScheduleSlaveDao;

    @Mock
    LoanUtil loanUtil;

    @Mock
    private LendingRefundAuditDaoSlave lendingRefundAuditSlaveDao;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testGetRefundList_NoLoansFound() {
        Long merchantId = 123L;

        when(lendingPaymentScheduleSlaveDao.findAllByMerchantId(merchantId)).thenReturn(Collections.emptyList());

        RefundStatusResponseDTO response = refundService.getRefundList(merchantId);

        assertNotNull(response);
        assertTrue(CollectionUtils.isEmpty(response.getData().getRefundDataMap()));
        verify(lendingPaymentScheduleSlaveDao, times(1)).findAllByMerchantId(merchantId);
        verify(lendingRefundAuditSlaveDao, never()).findAllByMerchantId(anyLong());
    }

    @Test
    void testGetRefundList_NoRefundsFound() {
        Long merchantId = 123L;

        List<LendingPaymentScheduleSlave> loanList = new ArrayList<>();

        loanList.add(buildLoan(1L, merchantId, "ACTIVE", "NBFC1"));

        when(lendingPaymentScheduleSlaveDao.findAllByMerchantId(merchantId)).thenReturn(loanList);
        when(lendingRefundAuditSlaveDao.findAllByMerchantId(merchantId)).thenReturn(Collections.emptyList());

        RefundStatusResponseDTO response = refundService.getRefundList(merchantId);

        assertNotNull(response);
        assertEquals(0, response.getData().getRefundDataMap().size());
        verify(lendingPaymentScheduleSlaveDao, times(1)).findAllByMerchantId(merchantId);
        verify(lendingRefundAuditSlaveDao, times(1)).findAllByMerchantId(merchantId);
    }

    private LendingPaymentScheduleSlave buildLoan(long id, Long merchantId, String status, String nbfc) {
        LendingPaymentScheduleSlave loan = new LendingPaymentScheduleSlave();
        loan.setId(id);
        loan.setMerchantId(merchantId);
        loan.setStatus(status);
        loan.setNbfc(nbfc);
        return loan;
    }

    @Test
    void testGetRefundList_WithRefunds() {
        long merchantId = 123L;

        List<LendingPaymentScheduleSlave> loanList = new ArrayList<>();
        loanList.add(buildLoan(1L, merchantId, "ACTIVE", "NBFC1"));

        List<LendingRefundAuditSlave> refundList = new ArrayList<>();
        LendingRefundAuditSlave refund = new LendingRefundAuditSlave();
        refund.setLoanId(1L);
        refund.setRefundAmount(1000.0);
        refund.setOrderAmount(1000.0);
        refund.setMode("ONLINE");
        refund.setBankRefNo("BANK123");
        refund.setSource("SOURCE1");
        refund.setRefundUtrNo("UTR123");
        refund.setStatus("SUCCESS");
        refund.setOrderDate(new Date());
        refund.setRemarks("Test Refund");
        refund.setLenderRemarks("Lender Test");
        refund.setTransferAmount(1000.0);
        refund.setTransferDate(new Date());
        refund.setCreatedAt(new Date());
        refund.setTerminalOrderId("TERM123");
        refundList.add(refund);

        when(lendingPaymentScheduleSlaveDao.findAllByMerchantId(merchantId)).thenReturn(loanList);
        when(lendingRefundAuditSlaveDao.findAllByMerchantId(merchantId)).thenReturn(refundList);

        RefundStatusResponseDTO response = refundService.getRefundList(merchantId);

        assertNotNull(response);
        assertEquals(1, response.getData().getRefundDataMap().size());
        assertEquals(1, response.getData().getRefundDataMap().get(1L).size());
        RefundStatusResponseDTO.RefundData refundData = response.getData().getRefundDataMap().get(1L).get(0);
        assertEquals(1L, refundData.getLoanId());
        assertEquals(merchantId, refundData.getMerchantId());
        assertEquals("ACTIVE", refundData.getLoanStatus());
        assertEquals("NBFC1", refundData.getLender());
        assertTrue(refundData.isRefundInitiated());
        assertEquals(Double.valueOf(1000.0), refundData.getRefundAmount());
        assertEquals(Double.valueOf(1000.0), refundData.getOrderAmount());
        assertEquals("ONLINE", refundData.getMode());
        assertEquals("BANK123", refundData.getBankRefNo());
        assertEquals("SOURCE1", refundData.getSource());
        assertEquals("UTR123", refundData.getRefundUtrNo());
        assertEquals("SUCCESS", refundData.getStatus());
        assertEquals("Test Refund", refundData.getRemarks());
        assertEquals("Lender Test", refundData.getLenderRemarks());
        assertEquals(Double.valueOf(1000.0), refundData.getTransferAmount());
        assertNotNull(refundData.getOrderDate());
        assertNotNull(refundData.getTransferDate());
        assertEquals("TERM123", refundData.getTerminalOrderId());

        verify(lendingPaymentScheduleSlaveDao, times(1)).findAllByMerchantId(merchantId);
        verify(lendingRefundAuditSlaveDao, times(1)).findAllByMerchantId(merchantId);
    }

    @Test
    void testGetRefundList_MultipleLoansAndRefunds() {
        Long merchantId = 123L;

        List<LendingPaymentScheduleSlave> loanList = new ArrayList<>();
        loanList.add(buildLoan(1L, merchantId, "ACTIVE", "NBFC1"));
        loanList.add(buildLoan(2L, merchantId, "CLOSED", "NBFC2"));

        List<LendingRefundAuditSlave> refundList = new ArrayList<>();
        LendingRefundAuditSlave refund1 = new LendingRefundAuditSlave();
        refund1.setLoanId(1L);
        refund1.setRefundAmount(1000.0);
        refund1.setStatus("SUCCESS");

        LendingRefundAuditSlave refund2 = new LendingRefundAuditSlave();
        refund2.setLoanId(2L);
        refund2.setRefundAmount(500.0);
        refund2.setStatus("FAILED");

        refundList.add(refund1);
        refundList.add(refund2);

        when(lendingPaymentScheduleSlaveDao.findAllByMerchantId(merchantId)).thenReturn(loanList);
        when(lendingRefundAuditSlaveDao.findAllByMerchantId(merchantId)).thenReturn(refundList);

        RefundStatusResponseDTO response = refundService.getRefundList(merchantId);

        assertNotNull(response);
        assertEquals(2, response.getData().getRefundDataMap().size());
        assertEquals(1, response.getData().getRefundDataMap().get(1L).size());
        assertEquals(1, response.getData().getRefundDataMap().get(2L).size());
        assertEquals(Double.valueOf(1000.0), response.getData().getRefundDataMap().get(1L).get(0).getRefundAmount());
        assertEquals(Double.valueOf(500.0), response.getData().getRefundDataMap().get(2L).get(0).getRefundAmount());

        verify(lendingPaymentScheduleSlaveDao, times(1)).findAllByMerchantId(merchantId);
        verify(lendingRefundAuditSlaveDao, times(1)).findAllByMerchantId(merchantId);
    }


}