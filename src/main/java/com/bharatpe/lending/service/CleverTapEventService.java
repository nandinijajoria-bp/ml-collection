package com.bharatpe.lending.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CleverTapEventService {
    @Autowired
    RestTemplate restTemplate;

    @Value("${clevertap.account.id}")
    String accountId;

    @Value("${clevertap.account.passcode}")
    String passCode;

    @Value("${clevertap.api.url}")
    String clevertapURL;

    public void sendClevertapEvent(String evtName, Object evtData, String mid) {
        HttpHeaders headers = new HttpHeaders() {{
            setContentType(MediaType.APPLICATION_JSON);
            set("X-CleverTap-Account-Id", accountId);
            set("X-CleverTap-Passcode", passCode);
        }};

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event");
        payload.put("evtName", evtName.toLowerCase());
        payload.put("evtData", evtData);
        payload.put("ts", System.currentTimeMillis() / 1000);
        payload.put("identity", mid);
        Map<String, Object> map = new HashMap<>();
        map.put("d", Collections.singletonList(payload));
        HttpEntity request = new HttpEntity(map, headers);

        try {
            log.info("Sending request to clevertap: {} ", request);

            ResponseEntity<HashMap> response = restTemplate.exchange(clevertapURL,
                    HttpMethod.POST, request,
                    HashMap.class);

            log.info("Response from clevertap : " + response);

        } catch (Exception e) {
            log.error("Error occurred while sending event to Clevertap, {}, {}", e.getMessage(), e);
        }
    }
}
