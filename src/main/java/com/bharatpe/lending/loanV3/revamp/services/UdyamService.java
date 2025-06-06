package com.bharatpe.lending.loanV3.revamp.services;

import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.UdyamFetchTriggerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UdyamService {
    private final RestTemplate restTemplate;
    private final LendingHmacCalculator lendingHmacCalculator;
    private final InternalClientDaoSlave internalClientDaoSlave;
    private final AesEncryptionUtil aesEncryptionUtil;

    private static String clientSecret;

    @Value("${kyc.service.base.url}")
    private String kycServiceHost;

    @Async
    public void triggerFetchUdyamCertificate(Long applicationId, Long merchantId, String lender){
        if(Objects.isNull(applicationId) || StringUtils.isEmpty(lender)){
            log.warn("invalid entry to fetch udyam, for merchant_id, application_id and lender are:{}, {}, {}", merchantId, applicationId, lender);
            return;
        }
        log.info("making udyam fetch api for merchant_id: {}, application_id: {}, and lender: {}", merchantId, applicationId, lender);
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("merchantId", merchantId);
            requestBody.put("source", "LOAN");
            String url = kycServiceHost + LendingConstants.UDYAM_FETCH_API;
            HttpHeaders headers = getApiHeaders(requestBody);
            HttpEntity<Map<String, Object>> request  = new HttpEntity<>(requestBody, headers);
            restTemplate.exchange(url, HttpMethod.POST, request, UdyamFetchTriggerResponse.class);
        }catch (Exception exception){
            log.error("exception occurred in udyam fetch flow, message: {}", exception.getMessage());
        }
        log.info("udyam fetch is triggered for merchant_id: {}, and lender: {}", merchantId, lender);
    }
    private HttpHeaders getApiHeaders(Map<String, Object> requestBody) {
        String payload = lendingHmacCalculator.getObjectPayload(requestBody);
        String hash = lendingHmacCalculator.calculateHmac(payload, getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(LendingConstants.HEADER_CLIENT_NAME, LendingConstants.LENDING_CLIENT);
        headers.set(LendingConstants.HEADER_HASH, hash);
        return headers;
    }
    private String getInternalSecret() {
        if(org.apache.commons.lang.StringUtils.isEmpty(clientSecret)) {
            InternalClientSlave client = internalClientDaoSlave.findByClientName(LendingConstants.LENDING_CLIENT);
            if (client != null) {
                clientSecret = aesEncryptionUtil.decrypt(client.getSecret());
            }
        }
        return clientSecret;
    }
}
