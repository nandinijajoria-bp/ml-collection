package com.bharatpe.lending.loanV2.handlers;

import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.BureauResponseDTO;
import com.bharatpe.lending.service.APIGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
@Component
@Slf4j
public class BureauHandler {
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    RestTemplate restTemplate;

    @Value("${bureau.base.url}")
    public String BUREAU_BASE_URL;

    public BureauResponseDTO getBureauData(String pancard, Long merchantId, String mobile) {
        try {
            log.info("in bureau handler");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("mobile",mobile);
            log.info("mobile number fetched");
            Map<String, String> merchantName = apiGatewayService.getFirstLastName(merchantId, pancard);
            requestBody.put("first_name",merchantName.get("firstname"));
            requestBody.put("last_name",merchantName.get("lastname"));
            log.info("name fetched");
            if (!StringUtils.isEmpty(pancard)) {
                requestBody.put("pancard", pancard);
            }
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            final String url = BUREAU_BASE_URL+ "/bureau/fetchBureau?days=30";
            log.info("BureauHandler call for phone: {}",mobile);

            ResponseEntity<ApiResponse> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, ApiResponse.class);
            if(Objects.isNull(responseEntity.getBody())){
                return null;
            }
            BureauResponseDTO bureauResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(responseEntity.getBody().getData()), BureauResponseDTO.class);
            log.info("bureauResponse:{}",bureauResponseDTO);
            return bureauResponseDTO;
        }  catch (HttpServerErrorException
                  | HttpClientErrorException
                  | ResourceAccessException exception) {
            log.info("exception while fetch bureau :{} {}", exception.getMessage(), exception);
        }catch (IOException exception) {
            log.error("Error occurred while parsing json: {} {}", exception.getMessage(), exception);
        }
        return null;
    }
}
