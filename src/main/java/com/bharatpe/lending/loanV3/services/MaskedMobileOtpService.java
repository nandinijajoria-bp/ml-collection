package com.bharatpe.lending.loanV3.services;

import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.common.util.RestUtils;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.dto.MaskedMobileOtpDTO;
import com.bharatpe.lending.loanV3.dto.MaskedMobileOtpRequestDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class MaskedMobileOtpService {
    @Value("${bureau.otp.baseurl:https://bureau-response.bharatpe.co.in}")
    String bureauOtpBaseurl;

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    InternalClientDaoSlave internalClientDaoSlave;

    @Autowired
    AesEncryptionUtil aesEncryptionUtil;

    @Autowired
    RestUtils restUtils;

    private static final String CLIENT = "LENDING";

    private static final String SOURCE = "EASY_LOANS";

    private static String clientSecret;

    public ApiResponse<?> sendOTP(BasicDetailsDto merchant, Map<String, String> map) {
        final String url = bureauOtpBaseurl + "/bureau/send/otp";

        MaskedMobileOtpRequestDTO maskedMobileOtpRequestDTO = new MaskedMobileOtpRequestDTO();
        maskedMobileOtpRequestDTO.setMerchantId(merchant.getId());
        maskedMobileOtpRequestDTO.setBureauMobile(map.get("bureauMobile"));
        String mobileNumber = merchant.getMobile();
        if (mobileNumber.startsWith("91")) {
            mobileNumber = mobileNumber.substring(2);
        }
        maskedMobileOtpRequestDTO.setMobile(mobileNumber);
        maskedMobileOtpRequestDTO.setSource(SOURCE);

        try {
            // Convert the request DTO to a Map
            HttpHeaders headers = getApiHeaders(maskedMobileOtpRequestDTO);
            Map<String, Object> requestBodyMap = new ObjectMapper().convertValue(maskedMobileOtpRequestDTO,
                    new TypeReference<Map<String, Object>>() {});
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBodyMap, headers);

            // Send the POST request
            ResponseEntity<MaskedMobileOtpDTO> responseEntity = restTemplate.exchange(url, HttpMethod.POST,
                    request, MaskedMobileOtpDTO.class);

            log.info("sendOTP api response :{}",responseEntity);
            // Check if response is successful
            if (!ObjectUtils.isEmpty(responseEntity)
                    && responseEntity.getStatusCode().is2xxSuccessful()
                    && !ObjectUtils.isEmpty(responseEntity.getBody()) ) {
                log.info("masked sendOtp maskedMobileOtpRequestDTO : {} and responseEntity.getBody(): {} and responseBody: {}", maskedMobileOtpRequestDTO, responseEntity.getBody(), responseEntity.getBody().getData());
                return new ApiResponse<>(responseEntity.getBody().getData());
            } else {
                log.error("Failed to send OTP, response status: {}", responseEntity.getStatusCode());
                return new ApiResponse<>(false, "400", "Failed to send OTP");
            }
        } catch (Exception e) {
            log.error("Exception in sendOTP for request: {}, Error: {}", maskedMobileOtpRequestDTO, e.getMessage());
            return new ApiResponse<>(false, "500" + e.getMessage(), null);
        }
    }

    public ApiResponse<?> verifyOTP(BasicDetailsDto merchant, Map<String, String> map) {
        final String url = bureauOtpBaseurl + "/bureau/verify/otp";

        MaskedMobileOtpRequestDTO maskedMobileOtpRequestDTO = new MaskedMobileOtpRequestDTO();
        maskedMobileOtpRequestDTO.setMerchantId(merchant.getId());
        maskedMobileOtpRequestDTO.setBureauMobile(map.get("bureauMobile"));

        String mobileNumber = merchant.getMobile();
        if (mobileNumber.startsWith("91")) {
            mobileNumber = mobileNumber.substring(2);
        }
        maskedMobileOtpRequestDTO.setMobile(mobileNumber);
        maskedMobileOtpRequestDTO.setUuid(map.get("uuid"));
        maskedMobileOtpRequestDTO.setOtp(map.get("otp"));
        maskedMobileOtpRequestDTO.setSource(SOURCE);

        try {
            // Convert the request DTO to a Map
            HttpHeaders headers = getApiHeaders(maskedMobileOtpRequestDTO);
            Map<String, Object> requestBodyMap = new ObjectMapper().convertValue(maskedMobileOtpRequestDTO,
                    new TypeReference<Map<String, Object>>() {});
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBodyMap, headers);

            // Send the POST request
            ResponseEntity<MaskedMobileOtpDTO> responseEntity = restTemplate.exchange(url, HttpMethod.POST,
                    request, MaskedMobileOtpDTO.class);
            log.info("verifyOtp api response :{}",responseEntity);

            // Check if response is successful
            if (!ObjectUtils.isEmpty(responseEntity)
                    && responseEntity.getStatusCode().is2xxSuccessful()
                    && !ObjectUtils.isEmpty(responseEntity.getBody()) ) {
                log.info("masked verifyOtp maskedMobileOtpRequestDTO : {} and responseEntity.getBody(): {} and responseBody: {}", maskedMobileOtpRequestDTO, responseEntity.getBody(), responseEntity.getBody().getData());
                return new ApiResponse<>(responseEntity.getBody().getData());
            } else {
                log.error("Failed to verify OTP, response status: {}", responseEntity.getStatusCode());
                return new ApiResponse<>(false, "400", "Failed to send OTP");
            }
        } catch (Exception e) {
            log.error("Exception in verify OTP for request: {}, Error: {}", maskedMobileOtpRequestDTO, e.getMessage());
            return new ApiResponse<>(false, "500" + e.getMessage(), null);
        }
    }

    private HttpHeaders getApiHeaders(Object requestPayload) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(LendingConstants.HEADER_CLIENT_NAME, CLIENT);
        Map<String, Object> payload = new ObjectMapper().convertValue(requestPayload, new TypeReference<Map<String, Object>>() {});
        headers.add(LendingConstants.HEADER_HASH, lendingHmacCalculator.calculateHmac(lendingHmacCalculator.getObjectPayload(payload), getInternalSecret()));
        return headers;
    }

    private String getInternalSecret() {
        if(StringUtils.isEmpty(clientSecret)) {
            InternalClientSlave client = internalClientDaoSlave.findByClientName(CLIENT);
            if (client != null) {
                clientSecret = aesEncryptionUtil.decrypt(client.getSecret());
            }
        }
        return clientSecret;
    }
}
