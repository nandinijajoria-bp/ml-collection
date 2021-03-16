package com.bharatpe.lending.service;


import com.bharatpe.common.dao.InternalClientDao;
import com.bharatpe.common.entities.InternalClient;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

public class PostRequestService {

    @Autowired
    HmacCalculator hmacCalculator;

    @Autowired
    InternalClientDao internalClientDao;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    AesEncryption aesEncryption;

    public String sendRequest() {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("task_id", 1);
            requestBody.put("merchant_id", 10017);
            requestBody.put("status", "CLOSED");
            requestBody.put("referral_code", "9992212337");

            String payload = hmacCalculator.getObjectPayload(requestBody);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
            String hash = hmacCalculator.calculateHmac(payload, getSecret());
            headers.set("hash", hash);
            headers.set("clientName", "SALES_DASHBOARD");

            HttpEntity<Object> entity = new HttpEntity<>(requestBody,headers);
            ResponseEntity<Object> response=null;
            String requestUrl = "endpoint";
            response= restTemplate.exchange(requestUrl, HttpMethod.POST, entity, Object.class);
            return null;
        }

    }

    private String getSecret() {
        InternalClient client = internalClientDao.findByClientName("LENDING");
        if (client != null) {
            return aesEncryption.decrypt(client.getSecret());
        }
        return null;
    }
}
