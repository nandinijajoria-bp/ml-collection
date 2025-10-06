package com.bharatpe.lending.service;

import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.dto.QrStatusEventDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class QrStatusApiService {

    private final RestTemplate restTemplate;
    private final String qrStatusBaseUrl;

    public QrStatusApiService(RestTemplate restTemplate,
                              @Value("${qrstatus.api.base-url}") String qrStatusBaseUrl) {
        this.restTemplate = restTemplate;
        this.qrStatusBaseUrl = qrStatusBaseUrl;
    }

    public String handleQrStatusEvent(QrStatusEventDTO eventDTO) {
        String url = qrStatusBaseUrl + "/api/qr-status/event";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        eventDTO.setClientIdentifier("Lending");
        HttpEntity<QrStatusEventDTO> request = new HttpEntity<>(eventDTO, headers);
        ResponseEntity<GlobalLimitResponse> response = restTemplate.postForEntity(url, request, GlobalLimitResponse.class);
        if (response != null && response.getBody() != null) {
            return "Your request has been successfully processed";
        } else {
            if("QR_UNBLOCKED".equalsIgnoreCase(eventDTO.getEventType())) {
                return "Failed to process your request either technical issue or merchant was not blocked for QR reason";
            }
            else{
                return "Your request failed due to technical issue";
            }
        }
    }
}
