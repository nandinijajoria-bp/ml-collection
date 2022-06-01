package com.bharatpe.lending.handlers;

import com.bharatpe.lending.dto.MerchantResponseDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class MerchantHandler {

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${de.merchant.summary.url}")
    String url;


    public MerchantResponseDTO getMerchantSummary(Long merchantID) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("client", "LENDING");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("merchant_id", merchantID);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        log.info("Merchant URL:{} and request:{}", url, request);

        ResponseEntity<ResponseDTO> responseEntity;
        try {
            responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    ResponseDTO.class
            );

            log.info("response entity: {}, response body: {}, data: {}", responseEntity, responseEntity.getBody(), responseEntity.getBody().getData());

            if(!responseEntity.getBody().isSuccess()){
                return null;
            }
            MerchantResponseDTO merchantDTO = objectMapper.readValue(objectMapper.writeValueAsString(responseEntity.getBody().getData()), MerchantResponseDTO.class);
            return merchantDTO;
        } catch (HttpServerErrorException
                | HttpClientErrorException
                | ResourceAccessException exception) {
            log.info("exception while fetching merchant :{} {}", exception.getMessage(), exception);
        } catch (Exception exception) {
            log.error("Error occurred while parsing json: {} {}", exception.getMessage(), exception);
        }
        return null;
    }
}

