package com.bharatpe.lending.service;


import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.service.LendingGlobalAPICacheService;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.util.HashMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource(properties =  {"lendingGlobalAPICachingRolloutPercent=100"})
@TestConfiguration
public class APIGatewayServiceTest {

    @InjectMocks
    APIGatewayService apiGatewayService;
    @Mock
    EasyLoanUtil easyLoanUtil;
    @Mock
    LendingGlobalAPICacheService globalAPICacheService;

    @Mock
    LendingHmacCalculator lendingHmacCalculator;

    @Mock
    RestTemplate restTemplate;

    @Mock
    InternalClientDaoSlave internalClientDaoSlave;

    @Mock
    AesEncryptionUtil aesEncryptionUtil;

    @Mock
    Environment env;


    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }


    @Value("${lendingGlobalAPICachingRolloutPercent:100}")
    private int lendingGlobalAPICachingRolloutPercent1;

    @Before
    public void setUp() {
        lendingGlobalAPICachingRolloutPercent1 = 10;
    }


    @Test
    public void testgetGlobalLimit() throws IOException, BureauCallMaskedApiException {
        // Arrange
        Long merchantId = 20000757L;
        String source = "testSource";
        Integer appVersion = 2;
        Boolean clubV2 = true;
        GlobalLimitResponse globalLimitResponse = new GlobalLimitResponse();
        globalLimitResponse.setSuccess(true);
        globalLimitResponse.setMessage("success");
        GlobalLimitResponse.Data data = new GlobalLimitResponse.Data();
        data.setMerchantId(20000757L);
        data.setGlobalLimit(100000.0);
        globalLimitResponse.setData(data);
        when(easyLoanUtil.percentScaleUp(anyLong(),any())).thenReturn(true);
        when(easyLoanUtil.isDummyMerchant(anyLong())).thenReturn(false);
        String responseBody = "{\"success\": true, \"message\": \"success\", \"data\": {\"merchantId\": 20000757, \"globalLimit\": 100000.0}}";
        //      Mockito.when(globalAPICacheService.getGlobalAPIResponseCache(anyLong(),any()).thenReturn(responseBody);
        doReturn(responseBody).when(globalAPICacheService).getGlobalAPIResponseCache(anyLong(),any());
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> responseEntity = new ResponseEntity<>(responseBody, responseHeaders, HttpStatus.OK);
        when(restTemplate.exchange(
                anyString(),eq(HttpMethod.POST), (HttpEntity<?>) any(HttpEntity.class),eq(String.class)
        )).thenReturn(responseEntity);

//      // Act
        GlobalLimitResponse response = apiGatewayService.getGlobalLimit(merchantId,source,appVersion,clubV2,"8695710807","1234","5678",false,false,"1234567","TOPUP",true,null,null,false,EligibilityRequestSource.EASY_LOANS);
        // Assert
        assertEquals("success",response.getMessage());
    }

    @Test
    public void testgetGlobalLimitWithScenapticRolloutPercent() throws IOException, BureauCallMaskedApiException {
        // Arrange
        Long merchantId = 20000757L;
        String source = "testSource";
        Integer appVersion = 2;
        Boolean clubV2 = true;
        GlobalLimitResponse globalLimitResponse = new GlobalLimitResponse();
        globalLimitResponse.setSuccess(true);
        globalLimitResponse.setMessage("success");
        GlobalLimitResponse.Data data = new GlobalLimitResponse.Data();
        data.setMerchantId(20000757L);
        data.setGlobalLimit(100000.0);
        globalLimitResponse.setData(data);
        when(easyLoanUtil.percentScaleUp(anyLong(), any())).thenReturn(true);
        when(easyLoanUtil.isDummyMerchant(anyLong())).thenReturn(false);
        InternalClientSlave internalClient = new InternalClientSlave();
        internalClient.setClientName("LENDING");
        internalClient.setSecret("LENDING");
        when(lendingHmacCalculator.getObjectPayload(any(HashMap.class))).thenReturn("true");
        when(internalClientDaoSlave.findByClientName(anyString())).thenReturn(internalClient);
        when(aesEncryptionUtil.decrypt(internalClient.getSecret())).thenReturn("LENDING");
        when(apiGatewayService.getInternalSecret()).thenReturn("LENDING");
        when(lendingHmacCalculator.calculateHmac(anyString(),any())).thenReturn("hash");
        when(env.getProperty("lending.global.endpoint")).thenReturn("mockedValue");
        String responseBody = "{\"success\": true, \"message\": \"success\", \"data\": {\"merchantId\": 20000757, \"globalLimit\": 100000.0}}";
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> responseEntity = new ResponseEntity<>(responseBody, responseHeaders, HttpStatus.OK);
        when(restTemplate.exchange(
                anyString(),eq(HttpMethod.POST), (HttpEntity<?>) any(HttpEntity.class),eq(String.class)
        )).thenReturn(responseEntity);

//      // Act
        GlobalLimitResponse response = apiGatewayService.getGlobalLimit(merchantId,source,appVersion,clubV2,"8695710807","1234","5678",false,false,"1234567","TOPUP",false,null,null,false,EligibilityRequestSource.EASY_LOANS);
        // Assert
        assertEquals("success",response.getMessage());
    }


    @Test
    public void testgetGlobalLimitWithPercentScaleUpFalseScenapticRollOutPercentAtZero() throws BureauCallMaskedApiException
    {
        //Arrange
        Long merchantId = 20000757L;
        String source = "testSource";
        Integer appVersion = 2;
        Boolean clubV2 = true;
        Boolean skipCache = true;
        Boolean isPincodeChanged = false;
        String sessionId = "1234";
        when(easyLoanUtil.percentScaleUp(anyLong(), anyInt())).thenReturn(false);
        when(easyLoanUtil.isDummyMerchant(anyLong())).thenReturn(false);
        when(env.getProperty("lending.global.endpoint")).thenReturn("mockedValue");
        InternalClientSlave internalClient = new InternalClientSlave();
        internalClient.setClientName("LENDING");
        internalClient.setSecret("LENDING");
        when(lendingHmacCalculator.getObjectPayload(any(HashMap.class))).thenReturn("true");
        when(internalClientDaoSlave.findByClientName(anyString())).thenReturn(internalClient);
        when(aesEncryptionUtil.decrypt(internalClient.getSecret())).thenReturn("LENDING");
        when(lendingHmacCalculator.calculateHmac(anyString(),any())).thenReturn("hash");
        GlobalLimitResponse globalLimitResponse = new GlobalLimitResponse();
        globalLimitResponse.setSuccess(true);
        globalLimitResponse.setMessage("success");
        GlobalLimitResponse.Data data = new GlobalLimitResponse.Data();
        data.setMerchantId(20000757L);
        data.setGlobalLimit(100000.0);
        globalLimitResponse.setData(data);

        ResponseEntity<GlobalLimitResponse> responseEntity = new ResponseEntity<>(globalLimitResponse,HttpStatus.OK);
        when(restTemplate.exchange(
                anyString(),eq(HttpMethod.GET), (HttpEntity<?>) any(HttpEntity.class),eq(GlobalLimitResponse.class)
        )).thenReturn(responseEntity);


        //Act
        GlobalLimitResponse response = apiGatewayService.getGlobalLimit(merchantId,source,appVersion,clubV2,"8695710807","1234","5678",false,false,"1234567","TOPUP",true,null,null,false,EligibilityRequestSource.EASY_LOANS);

        //Assert
        assertEquals("success",response.getMessage());


    }

    @Test
    public void testgetScenapticGlobalLimit() throws IOException , BureauCallMaskedApiException {
        //Arrange
        Long merchantId = 20000757L;
        String source = "testSource";
        Integer appVersion = 2;
        Boolean clubV2 = true;
        Boolean skipCache = true;
        Boolean isPincodeChanged = false;
        String sessionId = "1234";

        InternalClientSlave internalClient = new InternalClientSlave();
        internalClient.setClientName("LENDING");
        internalClient.setSecret("LENDING");

        when(lendingHmacCalculator.getObjectPayload(any(HashMap.class))).thenReturn("true");
        when(internalClientDaoSlave.findByClientName(anyString())).thenReturn(internalClient);
        when(aesEncryptionUtil.decrypt(internalClient.getSecret())).thenReturn("LENDING");
        when(lendingHmacCalculator.calculateHmac(anyString(),any())).thenReturn("hash");
        String responseBody = "{\"success\": true, \"message\": \"success\", \"data\": {\"merchantId\": 123, \"globalLimit\": 100000.0}}";
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> responseEntity = new ResponseEntity<>(responseBody, responseHeaders, HttpStatus.OK);
        when(restTemplate.exchange(
                anyString(),eq(HttpMethod.POST), (HttpEntity<?>) any(HttpEntity.class),eq(String.class)
        )).thenReturn(responseEntity);

        //Act
        GlobalLimitResponse globalLimitResponse = apiGatewayService.getScenapticGlobalLimit(merchantId,source,appVersion,clubV2,skipCache,isPincodeChanged,sessionId,false,EligibilityRequestSource.EASY_LOANS);

        //Assert
        assertEquals("success",globalLimitResponse.getMessage());

    }
}