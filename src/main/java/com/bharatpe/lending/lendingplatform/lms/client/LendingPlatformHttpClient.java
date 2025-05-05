package com.bharatpe.lending.lendingplatform.lms.client;

import com.bharatpe.lending.lendingplatform.authentication.service.LendingPlatformTokenHandler;
import com.bharatpe.lending.lendingplatform.config.LendingPlatformConfiguration;
import com.bharatpe.lending.lendingplatform.lms.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ErrorStatusCode.BAD_REQUEST;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ErrorStatusCode.INTERNAL_SERVER_ERROR;

@Service
@Slf4j
public class LendingPlatformHttpClient {

    private static final String NO_RESPONSE_BODY = "No response body";

    @Autowired
    @Qualifier("LendingPlatformRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private LendingPlatformTokenHandler lendingPlatformTokenHandler;

    @Autowired
    private LendingPlatformConfiguration lendingPlatformConfiguration;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Sends a POST request to the specified endpoint with the given request body.
     *
     * @param endpoint     the endpoint to send the request to
     * @param request      the request body
     * @param responseType the expected response type
     * @param <T>          the type of the response body
     * @return the response body wrapped in an ApiResponse
     */
    public <T> ApiResponse<T> sendPostRequest(String endpoint, Object request, Class<T> responseType) {
        return sendRequest(endpoint, request, responseType, HttpMethod.POST, null);
    }


    /**
     * Sends a GET request to the specified endpoint with the given request parameters.
     *
     * @param endpoint      the endpoint to send the request to
     * @param requestParams the request parameters
     * @param responseType  the expected response type
     * @param <T>           the type of the response body
     * @return the response body wrapped in an ApiResponse
     */


    public <T> ApiResponse<T> sendGetRequestWithParams(String endpoint, Map<String, String> requestParams, Class<T> responseType) {
        return sendRequest(endpoint, null, responseType, HttpMethod.GET, requestParams);
    }

    /**
     * Sends an HTTP request to the specified endpoint with the given request body and method.
     *
     * @param endpoint      the endpoint to send the request to
     * @param request       the request body
     * @param responseType  the expected response type
     * @param method        the HTTP method to use
     * @param requestParams the request parameters (for GET requests)
     * @param <T>           the type of the response body
     * @return the response body wrapped in an ApiResponse
     */
    public <T> ApiResponse<T> sendRequest(String endpoint, Object request, Class<T> responseType, HttpMethod method, Map<String, String> requestParams) {
        String url = (method == HttpMethod.GET && requestParams != null) ? buildUrlWithParams(endpoint, requestParams) : buildUrl(endpoint);
        try {
            HttpEntity<?> entity = (method == HttpMethod.GET) ? populateHeaders() : populateHeadersAndPayload(request);
            log.info("Sending {} request to URL: {}, response type: {}, with request body: {}", method, url, responseType.getName(), request);

            ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(url, method, entity, new ParameterizedTypeReference<ApiResponse<Object>>() {
            });

            if (response.getBody() == null) {
                log.error("No response body received");
                return ApiResponse.error(INTERNAL_SERVER_ERROR, null, null);
            }

            return handleResponse(response, responseType);
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ApiResponse.error(INTERNAL_SERVER_ERROR, "Unexpected error: "+ e.getMessage(), null);
        }
    }

    private <T> ApiResponse<T> handleResponse(ResponseEntity<ApiResponse<Object>> response, Class<T> responseType) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            return handleErrorResponse(response);
        }

        log.info("Request successful - response: {}", response.getBody());
        ApiResponse<Object> rawResponse = response.getBody();

        if (rawResponse == null) {
            log.error("No response body received");
            return ApiResponse.error(INTERNAL_SERVER_ERROR, NO_RESPONSE_BODY, null);
        }

        if (!rawResponse.isSuccess()) {
            return handleFailureResponse(rawResponse);
        }

        return convertResponseData(rawResponse, responseType);
    }

    private <T> ApiResponse<T> handleErrorResponse(ResponseEntity<ApiResponse<Object>> response) {
        log.error("Request failed - HTTP Status: {}, response body: {}", response.getStatusCode(), response.getBody() != null ? response.getBody() : NO_RESPONSE_BODY);

        if (response.getStatusCode().is4xxClientError()) {
            String errorMessage = (response.getBody() != null && response.getBody().getData() != null)
                    ? "Client error code : " + response.getStatusCode() + " Error Message : " + response.getBody().getData()
                    : "Client error code : " + response.getStatusCode() + " No error message available";
            return ApiResponse.error(BAD_REQUEST, errorMessage, null);
        } else if (response.getStatusCode().is5xxServerError()) {
            String errorMessage = (response.getBody() != null && response.getBody().getData() != null)
                    ? "Server error code : " + response.getStatusCode() + " Error Message : " + response.getBody().getData()
                    : "Server error code : " + response.getStatusCode() + " No error message available";
            return ApiResponse.error(INTERNAL_SERVER_ERROR, errorMessage, null);
        }

        return ApiResponse.error(INTERNAL_SERVER_ERROR, "Unexpected error", null);
    }

    private <T> ApiResponse<T> handleFailureResponse(ApiResponse<Object> rawResponse) {
        log.error("Request failed - Error: {}", rawResponse.getError());
        return ApiResponse.error(
                rawResponse.getError() != null ? rawResponse.getError().getErrorStatusCode() : "Unknown",
                rawResponse.getError() != null ? rawResponse.getError().getErrorMessage() + " data : " + rawResponse.getData() : "No error message available",
                null
        );
    }

    private <T> ApiResponse<T> convertResponseData(ApiResponse<Object> rawResponse, Class<T> responseType) {
        try {
            T typedData = objectMapper.convertValue(rawResponse.getData(), responseType);
            return new ApiResponse<>(true, null, typedData);
        } catch (Exception e) {
            log.error("Error converting response data: {}", e.getMessage(), e);
            return ApiResponse.error("CONVERSION_ERROR", "Failed to convert response data", null);
        }
    }

    private HttpEntity<?> populateHeaders() {
        HttpHeaders headers = getAuthHeader();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<?> populateHeadersAndPayload(Object payload) {
        HttpHeaders headers = getAuthHeader();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity<>(payload, headers);
    }

    private HttpHeaders getAuthHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(lendingPlatformTokenHandler.getAuthenticationToken());
        return headers;
    }

    private String buildUrlWithParams(String endpoint, Map<String, String> requestParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(lendingPlatformConfiguration.getBaseUrl())
                .path(endpoint);
        requestParams.forEach(builder::queryParam);
        return builder.toUriString();
    }

    private String buildUrl(String endpoint) {
        return UriComponentsBuilder.fromHttpUrl(lendingPlatformConfiguration.getBaseUrl())
                .path(endpoint)
                .toUriString();
    }
}