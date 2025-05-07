package com.bharatpe.lending.lendingPlatform.nbfc.client;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.bharatpe.lending.lendingplatform.nbfc.client.LendingPlatformClient;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.request.*;
import com.bharatpe.lending.lendingplatform.nbfc.dto.response.*;
import com.bharatpe.lending.lendingplatform.config.LendingPlatformConfiguration;
import com.bharatpe.lending.lendingplatform.authentication.service.LendingPlatformTokenHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;


@RunWith(SpringRunner.class)
public class LendingPlatformClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private LendingPlatformConfiguration lendingPlatformConfiguration;

    @Mock
    private LendingPlatformTokenHandler lendingPlatformTokenHandler;

    @InjectMocks
    private LendingPlatformClient lendingPlatformClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testInitiateBRE() throws Exception {
        LenderBaseRequest<BRERequest> breRequest = new LenderBaseRequest<>();
        BRERequest breData = new BRERequest();
        ApplicationDetails applicationDetails = new ApplicationDetails();
        applicationDetails.setApplicationId("123L");
        breData.setApplicationDetails(applicationDetails);
        breRequest.setData(breData);

        when(lendingPlatformConfiguration.getBreUrl()).thenReturn("http://test-url.com");
        when(lendingPlatformTokenHandler.getAuthenticationToken()).thenReturn("test-token");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-token");
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<?> request = new HttpEntity<>(breRequest, headers);

        LenderApiResponse<BREResponse> response = new LenderApiResponse<>();
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(LenderApiResponse.class))).thenReturn(response);

        LenderApiResponse<BREResponse> result = lendingPlatformClient.initiateBRE(breRequest);

        assertNotNull(result);
        verify(restTemplate, times(1)).postForObject("http://test-url.com", request, LenderApiResponse.class);
    }

    @Test
    public void testInitiateCreateLead() throws Exception {
        LenderBaseRequest<CreateLeadRequest> createLeadRequest = new LenderBaseRequest<>();
        CreateLeadRequest createLeadData = new CreateLeadRequest();
        ApplicationDetails applicationDetails = new ApplicationDetails();
        applicationDetails.setApplicationId("123L");
        createLeadData.setApplicationDetails(applicationDetails);
        createLeadRequest.setData(createLeadData);

        when(lendingPlatformConfiguration.getBreUrl()).thenReturn("http://test-url.com");
        when(lendingPlatformTokenHandler.getAuthenticationToken()).thenReturn("test-token");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-token");
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<?> request = new HttpEntity<>(createLeadRequest, headers);

        LenderApiResponse<CreateLeadResponse> response = new LenderApiResponse<>();
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(LenderApiResponse.class))).thenReturn(response);

        LenderApiResponse<CreateLeadResponse> result = lendingPlatformClient.initiateCreateLead(createLeadRequest);

        assertNotNull(result);
        verify(restTemplate, times(1)).postForObject("http://test-url.com", request, LenderApiResponse.class);
    }

    // Add similar test cases for other methods like initiateKYCDocumentUpload, initiateKYC, initiateDigiSign, etc.
}
