package com.bharatpe.lending.loanV2.handlers;

import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.BankStatementUploadRequestDto;
import com.bharatpe.lending.loanV2.dto.BankStatementUploadResponseDto;
import com.bharatpe.lending.loanV2.dto.Gst3bSessionResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class FinanceUtilsHandler {

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    InternalClientDaoSlave internalClientDaoSlave;

    @Autowired
    AesEncryptionUtil aesEncryptionUtil;

    @Value("${finance-utils.base.url}")
    public String FINANCE_UTILS_BASE_URL;

    public String UPLOAD_FILE_API_URL = "/api/bank-statement/upload";

    public String GET_BANK_LIST_API_URL = "/api/bank/list";

    public final String CLIENT = "LENDING";

    private String clientSecret;

    public String GST3b_SEND_OTP_API_URL = "/api/gst3b/send-otp";

    public String GST3b_VERIFY_OTP_API_URL = "/api/gst3b/verify-otp";

    public String GST3b_UPLOAD_API_URL = "/api/gst3b/upload";

    @Value("${verifyOtp.api.readTimeout.value:30000}")
    Integer readTimeoutValue;

    public Gst3bSessionResponseDTO sendGst3bOtp(String gstIn, String userName, String orderId, Long merchantId) {
        try {
            log.info("In financeUtils handler");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Client-Name", CLIENT);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("gstin", gstIn);
            requestBody.put("username", userName);
            requestBody.put("sessionId", orderId);
            requestBody.put("merchantId", merchantId);
            headers.set("hash", lendingHmacCalculator.calculateHmac(lendingHmacCalculator.getObjectPayload(requestBody), getInternalSecret()));
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String url = FINANCE_UTILS_BASE_URL + GST3b_SEND_OTP_API_URL;
            log.info("gst3b send otp api url: {} request: {}", url, request);
            ResponseEntity<Gst3bSessionResponseDTO> response = restTemplate.exchange(url, HttpMethod.POST, request, Gst3bSessionResponseDTO.class);
            if (ObjectUtils.isEmpty(response.getBody())) {
                return null;
            }
            log.info("gst3b send otp api url: {} response: {}", url, response.getBody());
            return response.getBody();
        } catch (HttpServerErrorException
                 | HttpClientErrorException exception) {
            log.error("exception in sending otp for gst3b :{} {}", exception.getMessage(), exception);
            try {
                JsonNode jsonNode = new ObjectMapper().readValue(exception.getResponseBodyAsString(), JsonNode.class);
                return new ObjectMapper().convertValue(jsonNode, Gst3bSessionResponseDTO.class);
            } catch (IOException | IllegalArgumentException e) {
                log.error("Exception in parsing responseBody string : {} {} ", exception.getMessage(), exception);
            }
        } catch (Exception exception) {
            log.error("exception in sending otp for gst3b :{} {}", exception.getMessage(), exception);
        }
        return null;
    }

    public Gst3bSessionResponseDTO verifyGst3bOtp(String requestId, String otp) {
        try {
            log.info("In financeUtils handler");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Client-Name", CLIENT);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("requestId", requestId);
            requestBody.put("otp", otp);
            headers.set("hash", lendingHmacCalculator.calculateHmac(lendingHmacCalculator.getObjectPayload(requestBody), getInternalSecret()));
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String url = FINANCE_UTILS_BASE_URL + GST3b_VERIFY_OTP_API_URL;
            log.info("gst3b verify otp api url: {} request: {}", url, request);
            HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
            clientHttpRequestFactory.setReadTimeout(readTimeoutValue);
            restTemplate = new RestTemplate(clientHttpRequestFactory);
            ResponseEntity<Gst3bSessionResponseDTO> response = restTemplate.exchange(url, HttpMethod.POST, request, Gst3bSessionResponseDTO.class);
            if (ObjectUtils.isEmpty(response.getBody())) {
                return null;
            }
            log.info("gst3b verify otp api url: {} response: {}", url, response.getBody());
            return response.getBody();
        } catch (HttpServerErrorException
                 | HttpClientErrorException exception) {
            log.error("exception in sending otp for gst3b :{} {}", exception.getMessage(), exception);
            try {
                JsonNode jsonNode = new ObjectMapper().readValue(exception.getResponseBodyAsString(), JsonNode.class);
                return new ObjectMapper().convertValue(jsonNode, Gst3bSessionResponseDTO.class);
            } catch (IOException | IllegalArgumentException e) {
                log.error("Exception in parsing responseBody string : {} {} ", exception.getMessage(), exception);
            }
        } catch (Exception exception) {
            log.error("exception in sending otp for gst3b :{} {}", exception.getMessage(), exception);
        }
        return null;
    }

    public Gst3bSessionResponseDTO gst3bUploadFile(String base64, String gstin, String fileName, String orderId, Long merchantId) {
        try {
            log.info("In financeUtils handler");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Client-Name", CLIENT);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("gstin", gstin);
            requestBody.put("base64", base64);
            requestBody.put("fileName", fileName);
            requestBody.put("sessionId", orderId);
            requestBody.put("merchantId", merchantId);
            headers.set("hash", lendingHmacCalculator.calculateHmac(lendingHmacCalculator.getObjectPayload(requestBody), getInternalSecret()));
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String url = FINANCE_UTILS_BASE_URL + GST3b_UPLOAD_API_URL;
            log.info("gst3b upload api url: {} request: {}", url, request);
            ResponseEntity<Gst3bSessionResponseDTO> response = restTemplate.exchange(url, HttpMethod.POST, request, Gst3bSessionResponseDTO.class);
            if (ObjectUtils.isEmpty(response.getBody())) {
                return null;
            }
            log.info("gst3b upload api url: {} response: {}", url, response.getBody());
            return response.getBody();
        } catch (HttpServerErrorException
                 | HttpClientErrorException exception) {
            log.error("exception in sending otp for gst3b :{} {}", exception.getMessage(), exception);
            try {
                JsonNode jsonNode = new ObjectMapper().readValue(exception.getResponseBodyAsString(), JsonNode.class);
                return new ObjectMapper().convertValue(jsonNode, Gst3bSessionResponseDTO.class);
            } catch (IOException | IllegalArgumentException e) {
                log.error("Exception in parsing responseBody string : {} {} ", exception.getMessage(), exception);
            }
        } catch (Exception exception) {
            log.error("exception in sending otp for gst3b :{} {}", exception.getMessage(), exception);
        }
        return null;
    }

    public BankStatementUploadResponseDto uploadFile(String fileName, String password, String file, String bankName, String orderId, Long merchantId) {
        try {
            log.info("In financeUtils handler");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Client-Name", CLIENT);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("fileName", fileName);
            requestBody.put("password", password);
            requestBody.put("bank", bankName);
            requestBody.put("base64", file);
            requestBody.put("orderId", orderId);
            requestBody.put("merchantId", merchantId);
            headers.set("hash", lendingHmacCalculator.calculateHmac(lendingHmacCalculator.getObjectPayload(requestBody), getInternalSecret()));
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String url = FINANCE_UTILS_BASE_URL + UPLOAD_FILE_API_URL;
            log.info("uploadBankingStatement api request url: {} request: {}", url, request);
            ResponseEntity<BankStatementUploadResponseDto> response = restTemplate.exchange(url, HttpMethod.POST, request, BankStatementUploadResponseDto.class);
            if (ObjectUtils.isEmpty(response.getBody())) {
                return null;
            }
            log.info("uploadBankingStatement api response url: {} response: {}", url, response.getBody());
            return response.getBody();
        } catch (HttpServerErrorException
                 | HttpClientErrorException exception) {
            log.error("exception in uploading bank statement file :{} {}", exception.getMessage(), exception);
            try {
                JsonNode jsonNode = new ObjectMapper().readValue(exception.getResponseBodyAsString(), JsonNode.class);
                return new ObjectMapper().convertValue(jsonNode, BankStatementUploadResponseDto.class);
            } catch (IOException | IllegalArgumentException e) {
                log.error("Exception in parsing responseBody string : {} {} ", exception.getMessage(), exception);
            }
        } catch (Exception exception) {
            log.error("exception in uploading bank statement file :{} {}", exception.getMessage(), exception);
        }
        return null;
    }

    public ApiResponse<?> getBankList(String bankName) {
        try {
            log.info("In financeUtils handler");
            String url = FINANCE_UTILS_BASE_URL + GET_BANK_LIST_API_URL + "?bank_name=" + bankName;
            log.info("creating request for url : {}", url);
            ResponseEntity<ApiResponse> response = restTemplate.getForEntity(url, ApiResponse.class, bankName);
            if (ObjectUtils.isEmpty(response.getBody())) {
                return new ApiResponse<>(false, "");
            }
            return response.getBody();
        } catch (HttpServerErrorException
                 | HttpClientErrorException
                 | ResourceAccessException exception) {
            log.error("exception in exception fetching bank list :{} {}", exception.getMessage(), exception);
        }
        return null;
    }

    private String getInternalSecret() {
        if(org.springframework.util.StringUtils.isEmpty(clientSecret)) {
            InternalClientSlave client = internalClientDaoSlave.findByClientName(CLIENT);
            if (client != null) {
                clientSecret = aesEncryptionUtil.decrypt(client.getSecret());
            }
        }
        return clientSecret;
    }
}
