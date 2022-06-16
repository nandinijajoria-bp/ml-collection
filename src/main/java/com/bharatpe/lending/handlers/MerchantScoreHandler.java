package com.bharatpe.lending.handlers;

import com.bharatpe.lending.common.dto.ResponseDTO;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.dto.MerchantScoreResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MerchantScoreHandler {
    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${de.merchant.score.url}")
    String url;

    public MerchantScoreResponseDto getMerchantScore(Long merchantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("client", "LENDING");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("merchant_id", merchantId);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        log.info("Merchant score URL:{} and request:{}", url, request);

        ResponseEntity<ResponseDTO> responseEntity;
        try {
            responseEntity = restTemplate.exchange(
              url,
              HttpMethod.POST,
              request,
              ResponseDTO.class
            );

            log.info("MerchantScoreResponse entity: {}, MerchantScoreResponse body: {}", responseEntity, responseEntity.getBody());

            if(!responseEntity.getBody().isSuccess()){
                return null;
            }
            List<MerchantScoreResponseDto> merchantScoreResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(responseEntity.getBody().getData()), objectMapper.getTypeFactory().constructCollectionType(List.class, MerchantScoreResponseDto.class));

            log.info("merchantScoreResponseDto : {}", merchantScoreResponseDto);

            return merchantScoreResponseDto.get(0);
        } catch (HttpServerErrorException
          | HttpClientErrorException
          | ResourceAccessException exception) {
            log.info("exception while fetch merchant score for merchantId: {} ,{}, {}", merchantId,exception.getMessage(), exception);
        } catch (Exception exception) {
            log.error("Error occurred while parsing json: {} {}", exception.getMessage(), exception);
        }
        return null;
    }

}
