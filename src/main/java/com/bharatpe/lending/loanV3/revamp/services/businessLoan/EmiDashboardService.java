package com.bharatpe.lending.loanV3.revamp.services.businessLoan;

import com.bharatpe.lending.constant.CommonConstants;
import com.bharatpe.lending.loanV3.revamp.dto.EmiDashboardResponse;
import com.bharatpe.lending.loanV3.revamp.dto.EmiTaskDetailsResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EmiDashboardService {

    private final RestTemplate restTemplate;

    @Value("${business.loan.api.host:https://merchant-lending-emi.bharatpe.co.in}")
    private String host;
    @Value("${business.loan.dashboard.api.read.timeout:5000}")
    private int dashboardReadTimeout;
    @Value("${business.loan.dashboard.api.connection.timeout:2000}")
    private int dashboardConnectionTimeout;
    @Value("${business.loan.dashboard.api:/api/v1/loan/dashboard}")
    private String path;
    @Value("${business.loan.task.path:/api/v1/loan/task/details}")
    private String emiTaskDetailsPath;
    @Value("${emi.api.token:b8c29f60-9c87-43c3-b35f-f04f06a4e16f}")
    private String businessLoanXapiKey;

    public EmiDashboardService() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(dashboardConnectionTimeout);
        factory.setReadTimeout(dashboardReadTimeout);
        factory.setHttpClient(HttpClientBuilder.create().build());
        this.restTemplate = new RestTemplate(factory);
    }

    @Async("emiDashboardTaskExecutor")
    public CompletableFuture<EmiDashboardResponse> getEmiDashboardResponse(@NotNull Long merchantId, String token) {
        EmiDashboardResponse emiDashboardResponse = getDashboardResponse(merchantId, token);
        return CompletableFuture.completedFuture(emiDashboardResponse);
    }

    public EmiDashboardResponse getDashboardResponse(Long merchantId, String token) {
        String url = host + path;
        HttpEntity<?> request = getRequestEntity(token);
        long startTime = System.currentTimeMillis();
        try {
            log.info("Making business loan dashboard api call for merchant_id: {}, url is: {}, http_entity is: {}",
                    merchantId, url, request);
            ResponseEntity<EmiDashboardResponse> response = restTemplate.exchange(url, HttpMethod.GET, request, EmiDashboardResponse.class);
            log.info("Received business loan response for merchant_id: {}, in time : {}, and response is: {}",
                    merchantId, System.currentTimeMillis() - startTime, response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            log.error("Client error while fetching business loan for merchant_id: {}, and exception is: {} and response body is: {}",
                    merchantId, exception.getStatusCode(), exception.getResponseBodyAsString());
        } catch (Exception exception) {
            log.error("Error while fetching business loan for merchant_id: {}, exception message: {}, exception class: {}, stack trace: {}",
                    merchantId,
                    exception.getMessage(),
                    exception.getClass().getName(),
                    Arrays.toString(exception.getStackTrace()));
        }
        return null;
    }

    public EmiTaskDetailsResponse getEmiApplicationTaskDetailsFromMerchantId(Long merchantId) {
        String url = host + emiTaskDetailsPath + "/merchantId/" + merchantId;
        HttpHeaders headers = new HttpHeaders();
        headers.set(CommonConstants.HEADER_X_API_KEY, businessLoanXapiKey);
        HttpEntity<?> request = new HttpEntity<>(headers);
        long startTime = System.currentTimeMillis();
        try {
            log.info("Making emi task details call for merchant_id: {}, url is: {}, http_entity is: {}",
                    merchantId, url, request);
            ResponseEntity<EmiTaskDetailsResponse> response = restTemplate.exchange(url, HttpMethod.GET, request, EmiTaskDetailsResponse.class);
            log.info("Received emi task details response for merchant_id: {}, in time : {}, and response is: {}",
                    merchantId, System.currentTimeMillis() - startTime, response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            log.error("Client error while fetching emi task details for merchant_id: {}, and exception is: {} and response body is: {}",
                    merchantId, exception.getStatusCode(), exception.getResponseBodyAsString());
        } catch (Exception exception) {
            log.error("Error while fetching emi task details for merchant_id: {}, exception message: {}, exception class: {}, stack trace: {}",
                    merchantId,
                    exception.getMessage(),
                    exception.getClass().getName(),
                    Arrays.toString(exception.getStackTrace()));
        }
        return null;
    }

    public EmiTaskDetailsResponse getEmiApplicationTaskDetailsFromApplicationId(Long applicationId) {
        String url = host + emiTaskDetailsPath + "/applicationId/" + applicationId;
        HttpHeaders headers = new HttpHeaders();
        headers.set(CommonConstants.HEADER_X_API_KEY, businessLoanXapiKey);
        HttpEntity<?> request = new HttpEntity<>(headers);
        long startTime = System.currentTimeMillis();
        try {
            log.info("Making emi task details call for applicationId: {}, url is: {}, http_entity is: {}",
                    applicationId, url, request);
            ResponseEntity<EmiTaskDetailsResponse> response = restTemplate.exchange(url, HttpMethod.GET, request, EmiTaskDetailsResponse.class);
            log.info("Received emi task details response for applicationId: {}, in time : {}, and response is: {}",
                    applicationId, System.currentTimeMillis() - startTime, response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            log.error("Client error while fetching emi task details for applicationId: {}, and exception is: {} and response body is: {}",
                    applicationId, exception.getStatusCode(), exception.getResponseBodyAsString());
        } catch (Exception exception) {
            log.error("Error while fetching emi task details for applicationId: {}, exception message: {}, exception class: {}, stack trace: {}",
                    applicationId,
                    exception.getMessage(),
                    exception.getClass().getName(),
                    Arrays.toString(exception.getStackTrace()));
        }
        return null;
    }

    private HttpEntity<?> getRequestEntity(String token) {
        HttpHeaders headers = getHeader(token);
        return new HttpEntity<>(headers);
    }

    private HttpHeaders getHeader(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.set("client", "LENDING");
        return headers;
    }

    public EmiDashboardResponse getData(CompletableFuture<EmiDashboardResponse> dataCompletableFuture) {
        try {
            return dataCompletableFuture.get();
        } catch (Exception exception) {
            log.error("Exception while getting response: {}", Arrays.toString(exception.getStackTrace()));
        }
        return null;
    }

    public void skipData(CompletableFuture<EmiDashboardResponse> dataCompletableFuture) {
        try {
            log.info("skipping response of emi dashboard api call");
            dataCompletableFuture.get(0, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
    }
}
