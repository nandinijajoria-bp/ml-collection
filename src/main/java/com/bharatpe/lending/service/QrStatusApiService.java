package com.bharatpe.lending.service;

import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.dto.QrStatusEventDTO;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class QrStatusApiService {

    private final RestTemplate restTemplate;
    private final String underwritingServiceBaseUrl;
    private final String underwritingApiKey;

    public QrStatusApiService(RestTemplate restTemplate,
                              @Value("${underwriting.service.base.url}") String underwritingServiceBaseUrl,
                              @Value("${x.api.key.underwriting.service}") String underwritingApiKey) {
        this.restTemplate = restTemplate;
        this.underwritingServiceBaseUrl = underwritingServiceBaseUrl;
        this.underwritingApiKey = underwritingApiKey;
    }

    //status,data,errorMessage
    public ApiResponse<?> handleQrStatusEvent(QrStatusEventDTO eventDTO) {
        log.info("Starting QR status event processing for merchant: {}, eventType: {}",
                eventDTO.getMerchantId(), eventDTO.getEventType());

        String url = underwritingServiceBaseUrl + "/api/v1/qr/webhook";
        log.debug("Making API call to underwriting service URL: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", underwritingApiKey);
        eventDTO.setClientIdentifier("Lending");
        HttpEntity<QrStatusEventDTO> request = new HttpEntity<>(eventDTO, headers);

        log.debug("Request payload prepared for merchant: {}, clientIdentifier: {}, API Key added to headers",
                eventDTO.getMerchantId(), eventDTO.getClientIdentifier());

        try {
            log.info("Calling QR status API for merchant: {} with event: {}",
                    eventDTO.getMerchantId(), eventDTO.getEventType());

            ResponseEntity<GlobalLimitResponse> response = restTemplate.postForEntity(url, request, GlobalLimitResponse.class);

            log.info("Received response from QR status API - Status: {}, HasBody: {}",
                    response.getStatusCode(), response.getBody() != null);

            if(response.getBody() != null) {
                log.debug("Response body details - ErrorCode: {}, HasData: {}",
                        response.getBody().getErrorCode(), response.getBody().getData() != null);
            }

            ApiResponse apiResponse = new ApiResponse();

            if("QR_UNBLOCKED".equalsIgnoreCase(eventDTO.getEventType())){
                log.info("Processing QR_UNBLOCKED event for merchant: {}", eventDTO.getMerchantId());

                if(!response.getStatusCode().is2xxSuccessful() || (response.getBody() != null && response.getBody().getErrorCode() != null && !response.getBody().getErrorCode().isEmpty())){
                    log.warn("QR unblock failed for merchant: {} - HTTP Status: {}, Error: {}",
                            eventDTO.getMerchantId(), response.getStatusCode(),
                            response.getBody() != null ? response.getBody().getErrorCode() : "No error code");

                    apiResponse.setSuccess(false);
                    apiResponse.setMessage(response.getBody() != null ? response.getBody().getErrorCode() : "Failed to process unblock request");
                    apiResponse.setData(response.getBody() != null ? response.getBody().getData() : null);
                } else {
                    log.info("QR unblock successful for merchant: {}", eventDTO.getMerchantId());

                    apiResponse.setSuccess(true);
                    apiResponse.setData(response.getBody() != null ? response.getBody().getData() : null);
                    apiResponse.setMessage("Merchant unblocked successfully");
                }

                log.info("QR_UNBLOCKED processing completed for merchant: {} with success: {}",
                        eventDTO.getMerchantId(), apiResponse.isSuccess());
                return apiResponse;

            } else if ("QR_BLOCKED".equalsIgnoreCase(eventDTO.getEventType())) {
                log.info("Processing QR_BLOCKED event for merchant: {}", eventDTO.getMerchantId());

                // For BLOCKED events, global response will be empty always
                if(response.getStatusCode().is2xxSuccessful()){
                    log.info("QR block successful for merchant: {} - HTTP Status: {}",
                            eventDTO.getMerchantId(), response.getStatusCode());

                    apiResponse.setSuccess(true);
                    apiResponse.setMessage("Merchant blocked successfully");
                    apiResponse.setData(null); // Always null for blocked events
                } else {
                    log.warn("QR block failed for merchant: {} - HTTP Status: {}",
                            eventDTO.getMerchantId(), response.getStatusCode());

                    apiResponse.setSuccess(false);
                    apiResponse.setMessage("Failed to block merchant");
                    apiResponse.setData(null);
                }

                log.info("QR_BLOCKED processing completed for merchant: {} with success: {}",
                        eventDTO.getMerchantId(), apiResponse.isSuccess());
                return apiResponse;

            }
            else {
                log.info("Unknown Event type provided");
                return apiResponse;
            }

        } catch (Exception e) {
            log.error("Exception occurred while processing QR status event for merchant: {} with eventType: {} - Error: {}",
                    eventDTO.getMerchantId(), eventDTO.getEventType(), e.getMessage(), e);

            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setSuccess(false);
            apiResponse.setMessage("Error processing QR status event: " + e.getMessage());
            apiResponse.setData(null);

            log.info("Exception handling completed for merchant: {} - returning error response", eventDTO.getMerchantId());
            return apiResponse;
        }
    }
}
