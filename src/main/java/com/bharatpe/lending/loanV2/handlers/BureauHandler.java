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

    public BureauResponseDTO getBureauData(String pancard, Long merchantId, String mobile, Long days) {
        try {
            log.info("in bureau handler");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("mobile",mobile);
            log.info("mobile number fetched");
            Map<String, String> merchantName = apiGatewayService.getFirstLastName(merchantId, pancard);
            requestBody.put("first_name",merchantName.get("firstName"));
            requestBody.put("last_name",merchantName.get("lastName"));
            log.info("name fetched");
            if (!StringUtils.isEmpty(pancard)) {
                requestBody.put("pancard", pancard);
            }
            requestBody.put("source", "EASY_LOANS");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            final String url = BUREAU_BASE_URL+ "/bureau/fetchBureau?days" + "=" + days;
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


    public BureauResponseDTO getBureauData(String pancard, Long merchantId, String mobile, Long days,String source) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("mobile",mobile);
            Map<String, String> merchantName = apiGatewayService.getFirstLastName(merchantId, pancard);
            requestBody.put("first_name",merchantName.get("firstName"));
            requestBody.put("last_name",merchantName.get("lastName"));
            log.info("name fetched");
            if (!StringUtils.isEmpty(pancard)) {
                requestBody.put("pancard", pancard);
            }
            requestBody.put("source", source);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            log.info("request for bureau data {} for merchant id {}", request, merchantId);
            final String url = BUREAU_BASE_URL+ "/bureau/fetchBureau?days" + "=" + days;
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

    public BureauResponseDTO getMaskedMobileNos(String pancard, Long merchantId, String mobile, String stageOneHitId, String stageTwoHitId) {
        try {
            log.info("Fetching Masked Mobile nos for merchant:{} with mobile: {}",merchantId, mobile);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("mobile",mobile);
            log.info("mobile number fetched");
            Map<String, String> merchantName = apiGatewayService.getFirstLastName(merchantId, pancard);
            requestBody.put("first_name",merchantName.get("firstName"));
            requestBody.put("last_name",merchantName.get("lastName"));
            requestBody.put("stage_one_hit_id",stageOneHitId);
            requestBody.put("stage_two_hit_id",stageTwoHitId);
            if (!StringUtils.isEmpty(pancard)) {
                requestBody.put("pancard", pancard);
            }
            log.info("Request body created");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            final String url = BUREAU_BASE_URL+ "/bureau/fetchMaskedMobileNos";
            log.info("Request: {} to fetch Masked mobile nos for phone: {} and pancard: {} using fetch bureau response", request, mobile, pancard);

            ResponseEntity<ApiResponse> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, ApiResponse.class);
            if(Objects.isNull(responseEntity.getBody())){
                return null;
            }
            BureauResponseDTO bureauResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(responseEntity.getBody().getData()), BureauResponseDTO.class);
            log.info("bureauResponse:{}",bureauResponseDTO);
            return bureauResponseDTO;
        } catch (HttpServerErrorException
                  | HttpClientErrorException
                  | ResourceAccessException exception) {
            log.info("exception while fetch bureau :{} {}", exception.getMessage(), exception);
        } catch (IOException exception) {
            log.error("Error occurred while parsing json: {} {}", exception.getMessage(), exception);
        }
        return null;
    }
}
