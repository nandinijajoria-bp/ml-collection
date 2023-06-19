package com.bharatpe.lending.loanV2.handlers;

import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.BankStatementUploadRequestDto;
import com.bharatpe.lending.loanV2.dto.BankStatementUploadResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
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
            log.info("uploadBankingStatement api request url: {} response: {}", url, request);
            ResponseEntity<BankStatementUploadResponseDto> response = restTemplate.exchange(url, HttpMethod.POST, request, BankStatementUploadResponseDto.class);
            if (ObjectUtils.isEmpty(response.getBody())) {
                return null;
            }
            log.info("uploadBankingStatement api response url: {} response: {}", url, response.getBody());
            return response.getBody();
        } catch (HttpServerErrorException
                 | HttpClientErrorException
                 | ResourceAccessException exception) {
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
