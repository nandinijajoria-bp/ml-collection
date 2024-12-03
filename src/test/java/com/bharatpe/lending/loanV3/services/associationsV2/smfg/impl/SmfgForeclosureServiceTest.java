package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.smfg.SmfgForeclosureDetailsResponse;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.ContextConfiguration;

import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SmfgForeclosureServiceTest.TestConfig.class)
public class SmfgForeclosureServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public SmfgForeclosureService smfgForeclosureService() {
            return new SmfgForeclosureService();
        }

        @Bean
        public ILenderAPIGateway lenderAPIGateway() {
            return mock(ILenderAPIGateway.class);
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public SmfgConfig smfgConfig() {
            return mock(SmfgConfig.class);
        }

        @Bean
        public LendingApplicationDao lendingApplicationDao() {
            return mock(LendingApplicationDao.class);
        }
    }

    @InjectMocks
    private SmfgForeclosureService smfgForeclosureService;

    @Mock
    private ILenderAPIGateway lenderAPIGateway;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SmfgConfig smfgConfig;

    @Mock
    private LendingApplicationDao lendingApplicationDao;

    @Test
    public void testGetForeclosureDetails_ReturnsZero() {
        Long applicationId = 1L;
        when(lendingApplicationDao.findById(applicationId)).thenReturn(Optional.empty());

        Double result = smfgForeclosureService.getForeclosureDetails(applicationId);

        assertEquals(0D, result, 0.01);
        verify(lenderAPIGateway, never()).invokeStage(any(), any());
    }

    @Test
    public void testGetForeclosureDetails_NullResponse() throws Exception {
        Long applicationId = 1L;
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setNbfcId("test-nbfc-id");
        when(lendingApplicationDao.findById(applicationId)).thenReturn(Optional.of(lendingApplication));

        NBFCResponseDTO nbfcResponseDto = new NBFCResponseDTO();
        nbfcResponseDto.setSuccess(true);
        SmfgForeclosureDetailsResponse smfgForeclosureDetailsResponse = new SmfgForeclosureDetailsResponse();
        SmfgForeclosureDetailsResponse.DataResponse dataResponse = new SmfgForeclosureDetailsResponse.DataResponse();
        dataResponse.setForeclosureAmt(1000D);
        smfgForeclosureDetailsResponse.setData(dataResponse);
        nbfcResponseDto.setData(smfgForeclosureDetailsResponse);

        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"foreclosureAmt\":1000}");
        when(objectMapper.readValue("{\"foreclosureAmt\":1000}", SmfgForeclosureDetailsResponse.class))
                .thenReturn(new SmfgForeclosureDetailsResponse());

        Double result = smfgForeclosureService.getForeclosureDetails(applicationId);

        assertEquals(0D, result, 0.01);
        verify(lenderAPIGateway, times(1)).invokeStage(any(), any());
    }

    @Test
    public void testGetForeclosureDetails_ReturnsForeclosureAmount() throws Exception {
        Long applicationId = 1L;
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setNbfcId("test-nbfc-id");
        when(lendingApplicationDao.findById(applicationId)).thenReturn(Optional.of(lendingApplication));

        NBFCResponseDTO nbfcResponseDto = new NBFCResponseDTO();
        nbfcResponseDto.setSuccess(true);
        SmfgForeclosureDetailsResponse smfgForeclosureDetailsResponse = new SmfgForeclosureDetailsResponse();
        SmfgForeclosureDetailsResponse.DataResponse dataResponse = new SmfgForeclosureDetailsResponse.DataResponse();
        dataResponse.setForeclosureAmt(1000D);
        smfgForeclosureDetailsResponse.setData(dataResponse);
        nbfcResponseDto.setData(smfgForeclosureDetailsResponse);

        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"foreclosureAmt\":1000}");
        when(objectMapper.readValue("{\"foreclosureAmt\":1000}", SmfgForeclosureDetailsResponse.class))
                .thenReturn(smfgForeclosureDetailsResponse);

        Double result = smfgForeclosureService.getForeclosureDetails(applicationId);

        assertEquals(1000D, result, 0.01);
        verify(lenderAPIGateway, times(1)).invokeStage(any(), any());
    }

    @Test
    public void testGetForeclosureDetails_ReturnsException() throws Exception {
        Long applicationId = 1L;
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setNbfcId("test-nbfc-id");
        when(lendingApplicationDao.findById(applicationId)).thenReturn(Optional.of(lendingApplication));

        NBFCResponseDTO nbfcResponseDto = new NBFCResponseDTO();
        nbfcResponseDto.setSuccess(true);
        SmfgForeclosureDetailsResponse smfgForeclosureDetailsResponse = new SmfgForeclosureDetailsResponse();
        SmfgForeclosureDetailsResponse.DataResponse dataResponse = new SmfgForeclosureDetailsResponse.DataResponse();
        dataResponse.setForeclosureAmt(1000D);
        smfgForeclosureDetailsResponse.setData(dataResponse);
        nbfcResponseDto.setData(smfgForeclosureDetailsResponse);

        when(lenderAPIGateway.invokeStage(any(), any())).thenReturn(nbfcResponseDto);

        Double result = smfgForeclosureService.getForeclosureDetails(applicationId);

        assertEquals(0D, result, 0.01);
        verify(lenderAPIGateway, times(1)).invokeStage(any(), any());
    }

    @Test
    public void testGetForeclosureReceiptRequest_Success_NACHCase() {
        Long applicationId = 1L;
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setNbfcId("test-nbfc-id");

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setAmount(5000D);
        lendingLedger.setDate(new Date());
        lendingLedger.setAdjustmentMode("NACH");
        lendingLedger.setTerminalOrderId(null);

        when(lendingApplicationDao.findById(applicationId)).thenReturn(Optional.of(lendingApplication));

        when(smfgConfig.getLmsAppName()).thenReturn("appName");
        when(smfgConfig.getLmsAppPassword()).thenReturn("appPassword");
        when(smfgConfig.getLmsStaticIpAddress()).thenReturn("192.168.0.1");
        when(smfgConfig.getForeclosureTowards()).thenReturn("someTowards");

        NBFCRequestDTO requestDto = smfgForeclosureService.getForeclosureReceiptRequest(applicationId, lendingLedger);

        assertNotNull(requestDto);
        assertEquals("NACH",lendingLedger.getAdjustmentMode());
    }

    @Test
    public void testGetForeclosureReceiptRequest_Success_NACHFP() {
        Long applicationId = 1L;
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setNbfcId("test-nbfc-id");

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setAmount(5000D);
        lendingLedger.setDate(new Date());
        lendingLedger.setAdjustmentMode("FP");
        lendingLedger.setTerminalOrderId(null);

        when(lendingApplicationDao.findById(applicationId)).thenReturn(Optional.of(lendingApplication));

        when(smfgConfig.getLmsAppName()).thenReturn("appName");
        when(smfgConfig.getLmsAppPassword()).thenReturn("appPassword");
        when(smfgConfig.getLmsStaticIpAddress()).thenReturn("192.168.0.1");
        when(smfgConfig.getForeclosureTowards()).thenReturn("someTowards");

        NBFCRequestDTO requestDto = smfgForeclosureService.getForeclosureReceiptRequest(applicationId, lendingLedger);

        assertNotNull(requestDto);
        assertEquals("FP",lendingLedger.getAdjustmentMode());
    }

    @Test
    public void testGetForeclosureReceiptRequest_Success_DefaultCase() {
        Long applicationId = 1L;
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setNbfcId("test-nbfc-id");

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setAmount(5000D);
        lendingLedger.setDate(new Date());
        lendingLedger.setAdjustmentMode("EXCESS");
        lendingLedger.setTerminalOrderId(null);

        when(lendingApplicationDao.findById(applicationId)).thenReturn(Optional.of(lendingApplication));

        when(smfgConfig.getLmsAppName()).thenReturn("appName");
        when(smfgConfig.getLmsAppPassword()).thenReturn("appPassword");
        when(smfgConfig.getLmsStaticIpAddress()).thenReturn("192.168.0.1");
        when(smfgConfig.getForeclosureTowards()).thenReturn("someTowards");

        NBFCRequestDTO requestDto = smfgForeclosureService.getForeclosureReceiptRequest(applicationId, lendingLedger);

        assertNotNull(requestDto);
        assertEquals("EXCESS",lendingLedger.getAdjustmentMode());
    }

    @Test
    public void testGetForeclosureReceiptRequest_ThrowsException() {
        Long applicationId = 1L;
        LendingApplication lendingApplication = new LendingApplication();
        lendingApplication.setNbfcId("test-nbfc-id");

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setAmount(5000D);
        lendingLedger.setDate(new Date());
        lendingLedger.setTerminalOrderId(null);

        when(smfgConfig.getLmsAppPassword()).thenReturn("appPassword");
        when(smfgConfig.getLmsStaticIpAddress()).thenReturn("192.168.0.1");
        when(smfgConfig.getForeclosureTowards()).thenReturn("someTowards");

        NBFCRequestDTO requestDto = smfgForeclosureService.getForeclosureReceiptRequest(applicationId, lendingLedger);

        assertNull(requestDto);
    }
}
