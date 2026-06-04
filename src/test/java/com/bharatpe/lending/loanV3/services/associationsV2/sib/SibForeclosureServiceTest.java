package com.bharatpe.lending.loanV3.services.associationsV2.sib;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.common.entity.LendingCollectionAudit;
import com.bharatpe.lending.common.dao.LendingCollectionAuditDao;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.sib.SibForeclosureRequest;
import com.bharatpe.lending.loanV3.dto.request.sib.SibRepaymentRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.sib.SibForeclosureResponse;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SibForeclosureServiceTest {

    @Mock
    private ILenderAPIGateway lenderAPIGateway;

    @Mock
    private LendingApplicationDao lendingApplicationDao;

    @Mock
    private LendingCollectionAuditDao lendingCollectionAuditDao;

    @InjectMocks
    private SibForeclosureService sibForeclosureService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void injectConfig() {
        ReflectionTestUtils.setField(sibForeclosureService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(sibForeclosureService, "sibForeclosureDetailsTimeoutThreshold", 20_000);
        ReflectionTestUtils.setField(sibForeclosureService, "programReferenceNumber", "PREF-9");
        ReflectionTestUtils.setField(sibForeclosureService, "nposConfigId", 7);
    }

    @Test
    public void getForeclosureDetails_missingApplication_returnsZero() {
        when(lendingApplicationDao.findById(1L)).thenReturn(Optional.empty());

        Double out = sibForeclosureService.getForeclosureDetails(1L);

        Assert.assertEquals(0D, out, 0.0001);
        verify(lenderAPIGateway, never()).invokeStage(any(), any(), any());
    }

    @Test
    public void getForeclosureDetails_nullNbfcId_returnsZero() {
        LendingApplication app = new LendingApplication();
        app.setId(2L);
        when(lendingApplicationDao.findById(2L)).thenReturn(Optional.of(app));

        Assert.assertEquals(0D, sibForeclosureService.getForeclosureDetails(2L), 0.0001);
        verify(lenderAPIGateway, never()).invokeStage(any(), any(), any());
    }

    @Test
    public void getForeclosureDetails_success_parsesForeclosureAmount() {
        Long appId = 3L;
        LendingApplication app = new LendingApplication();
        app.setId(appId);
        app.setNbfcId("SIB-LOAN-1");
        when(lendingApplicationDao.findById(appId)).thenReturn(Optional.of(app));

        SibForeclosureResponse.SibForeclosureData data = SibForeclosureResponse.SibForeclosureData.builder()
                .foreclosureAmount(15_250.5d)
                .build();
        NBFCResponseDTO<SibForeclosureResponse> res = new NBFCResponseDTO<>();
        res.setSuccess(true);
        res.setData(SibForeclosureResponse.builder()
                .data(data)
                .build());

        when(lenderAPIGateway.invokeStage(any(NBFCRequestDTO.class), eq(LenderAssociationStages.FORECLOSURE_FETCH), eq(20_000)))
                .thenReturn(res);

        Assert.assertEquals(15_250.5d, sibForeclosureService.getForeclosureDetails(appId), 0.0001);

        ArgumentCaptor<NBFCRequestDTO> cap = ArgumentCaptor.forClass(NBFCRequestDTO.class);
        verify(lenderAPIGateway).invokeStage(cap.capture(), eq(LenderAssociationStages.FORECLOSURE_FETCH), eq(Integer.valueOf(20_000)));
        Assert.assertEquals(Lender.SIB.name(), cap.getValue().getLender());
        Assert.assertEquals(appId, cap.getValue().getApplicationId());
        SibForeclosureRequest payload = (SibForeclosureRequest) cap.getValue().getPayload();
        Assert.assertEquals(7, (int) payload.getNposConfigId());
        Assert.assertEquals("SIB-LOAN-1", payload.getInvestorLoanId());
    }

    @Test
    public void getForeclosureDetails_gatewayNotSuccessful_returnsZero() {
        Long appId = 4L;
        LendingApplication app = new LendingApplication();
        app.setId(appId);
        app.setNbfcId("X");
        when(lendingApplicationDao.findById(appId)).thenReturn(Optional.of(app));

        NBFCResponseDTO<Object> res = new NBFCResponseDTO<>();
        res.setSuccess(false);
        res.setData(Collections.emptyMap());
        when(lenderAPIGateway.invokeStage(any(), any(), any()))
                .thenReturn(res);

        Assert.assertEquals(0D, sibForeclosureService.getForeclosureDetails(appId), 0.0001);
    }

    @Test
    public void getForeclosureReceiptRequest_missingApplication_returnsNull() {
        when(lendingApplicationDao.findById(10L)).thenReturn(Optional.empty());

        LendingLedger ledger = new LendingLedger();
        ledger.setId(1L);
        Assert.assertNull(sibForeclosureService.getForeclosureReceiptRequest(10L, ledger));
    }

    @Test
    public void getForeclosureReceiptRequest_nullNbfcId_returnsNull() {
        when(lendingApplicationDao.findById(10L)).thenReturn(Optional.of(new LendingApplication()));
        when(lendingCollectionAuditDao.findByLedgerID(1L, 1)).thenReturn(null);
        LendingLedger ledger = new LendingLedger();
        ledger.setId(1L);

        Assert.assertNull(sibForeclosureService.getForeclosureReceiptRequest(10L, ledger));
    }

    @Test
    public void getForeclosureReceiptRequest_withAudit_buildsSibRepaymentRequest() {
        long appId = 11L;
        LendingApplication app = new LendingApplication();
        app.setId(appId);
        app.setNbfcId("INV-55");
        app.setExternalLoanId("BP-99");
        when(lendingApplicationDao.findById(appId)).thenReturn(Optional.of(app));

        LendingCollectionAudit audit = new LendingCollectionAudit();
        audit.setId(200L);
        when(lendingCollectionAuditDao.findByLedgerID(50L, 1)).thenReturn(audit);

        Date txn = new Date(120, 0, 10);
        LendingLedger ledger = new LendingLedger();
        ledger.setId(50L);
        ledger.setTerminalOrderId("UPI-1");
        ledger.setDate(txn);
        ledger.setAdjustmentMode("NACH");
        ledger.setAmount(1_000.5);
        ledger.setPrinciple(400d);
        ledger.setInterest(600d);

        NBFCRequestDTO<?> dto = sibForeclosureService.getForeclosureReceiptRequest(appId, ledger);
        Assert.assertNotNull(dto);
        Assert.assertEquals(Lender.SIB.name(), dto.getLender());
        Assert.assertEquals("LENDING", dto.getProductName());
        Assert.assertEquals(200L, dto.getIdentifier().get("bpReferenceId"));
        Assert.assertEquals("UPI-1", dto.getIdentifier().get("lenderReferenceId"));
        SibRepaymentRequestDTO p = (SibRepaymentRequestDTO) dto.getPayload();
        Assert.assertEquals(7, p.getNposConfigId().intValue());
        Assert.assertEquals("50_UPI-1", p.getClientRequestId());
        Assert.assertNotNull(p.getRequestData().getCollection().getRecords().get(0).getBharatpeLoanId());
    }

    @Test
    public void getForeclosureReceiptRequest_noAudit_usesZeroBpReference() {
        long appId = 12L;
        LendingApplication app = new LendingApplication();
        app.setId(appId);
        app.setNbfcId("I");
        app.setExternalLoanId("E");
        when(lendingApplicationDao.findById(appId)).thenReturn(Optional.of(app));
        when(lendingCollectionAuditDao.findByLedgerID(88L, 1)).thenReturn(null);

        LendingLedger ledger = new LendingLedger();
        ledger.setId(88L);
        ledger.setTerminalOrderId("T");
        ledger.setDate(new Date(120, 0, 1));
        ledger.setAdjustmentMode("X");
        ledger.setAmount(1d);
        ledger.setPrinciple(0.5d);
        ledger.setInterest(0.5d);

        NBFCRequestDTO<?> dto = sibForeclosureService.getForeclosureReceiptRequest(appId, ledger);
        Assert.assertNotNull(dto);
        Assert.assertEquals(Integer.valueOf(0), dto.getIdentifier().get("bpReferenceId"));
    }

    @Test
    public void fmtAmount_scalesToTwoDecimalPlaces() {
        Assert.assertEquals("100.01", sibForeclosureService.fmtAmount(100.005d));
    }

    @Test
    public void fmtAmount_nullIsEmpty() {
        Assert.assertEquals("", sibForeclosureService.fmtAmount(null));
    }
}
