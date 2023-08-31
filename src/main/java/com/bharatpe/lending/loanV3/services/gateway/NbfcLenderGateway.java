package com.bharatpe.lending.loanV3.services.gateway;

import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.service.APIGatewayService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Service
public class NbfcLenderGateway extends APIGatewayService {

    @Autowired
    ConfigResolver configResolver;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    RestTemplate restTemplate;


    public <V> V invoke(String requestObject, Class<V> responseType, String requestUrl) {
        try {
            Map<String, Object> requestBody = configResolver.getConfig(requestObject, new TypeReference<Map<String, Object>>() {
            });
            String hash = lendingHmacCalculator
                    .calculateHmac(lendingHmacCalculator.getObjectPayload(requestBody), super.getInternalSecret());
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.set("Hash", hash);
            headers.set("Client-Name", super.CLIENT);
            log.info("request body {}", requestObject);
            HttpEntity<Object> request = new HttpEntity<>(requestBody, headers);
            log.info("request body for nbfc {} request hash {} :  {}", requestUrl,hash, request);
            ResponseEntity<String> responseEntity = restTemplate.postForEntity( requestUrl, request, String.class);
            log.info("response for {} invokation {}", requestUrl, responseEntity);
            if (!ObjectUtils.isEmpty(responseEntity) && responseEntity.hasBody()) {
                V response = objectMapper.readValue(responseEntity.getBody(),responseType);
                return response;
            }
        } catch (HttpStatusCodeException httpStatusCodeException) {
            log.error("status code {} | headers {} | body {}", httpStatusCodeException.getRawStatusCode(), httpStatusCodeException.getResponseHeaders(),
                    httpStatusCodeException.getResponseBodyAsString());
        }
        catch (Exception e) {
            log.error("exception occurred while processing {} api call to nbfc svc {}",e, requestUrl, e.getMessage());
        }
        return null;
    }
    public <V> V invokeWithParams(String requestObject, Class<V> responseType, String requestUrl, HttpMethod httpMethod) {
        try {
            Map<String, Object> requestBody = configResolver.getConfig(requestObject, new TypeReference<Map<String, Object>>() {
            });
            String hash = lendingHmacCalculator
                    .calculateHmac(lendingHmacCalculator.getObjectPayload(requestBody), super.getInternalSecret());
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.set("Hash", hash);
            headers.set("Client-Name", super.CLIENT);
            log.info("request body {}", requestObject);
            HttpEntity<Object> request = new HttpEntity<>(requestBody, headers);
            log.info("request body for nbfc {} request hash {} :  {}", requestUrl,hash, request);
            ResponseEntity<String> responseEntity = restTemplate.exchange( requestUrl , httpMethod, request, String.class);
            log.info("response for {} invokation {}", requestUrl, responseEntity);
            if (!ObjectUtils.isEmpty(responseEntity) && responseEntity.hasBody()) {
                V response = objectMapper.readValue(responseEntity.getBody(),responseType);
                return response;
            }
        } catch (HttpStatusCodeException httpStatusCodeException) {
            log.error("status code {} | headers {} | body {}", httpStatusCodeException.getRawStatusCode(), httpStatusCodeException.getResponseHeaders(),
                    httpStatusCodeException.getResponseBodyAsString());
        }
        catch (Exception e) {
            log.error("exception occurred while processing {} api call to nbfc svc {}",e, requestUrl, e.getMessage());
        }
        return null;
    }
}
