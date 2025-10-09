package com.bharatpe.lending.handlers;

import com.bharatpe.lending.common.dto.PayoutResponseDTO;
import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.constant.LendingConstants;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class EmiHandler
{

    @Autowired
    RestTemplate restTemplate;

    @Value("${emi.base.url}")
    String emiBaseUrl;

    @Value("${pg.callback.emi.base.url:https://merchant-lending-emi.bharatpe.co.in}")
    String pgCallbackEmiBaseUrl;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${emi.api.token:b8c29f60-9c87-43c3-b35f-f04f06a4e16f}")
    String apiToken;

    @Value("${pg.callback.emi.api.token:b8c29f60-9c87-43c3-b35f-f04f06a4e16f}")
    String pgCallbackApiToken;

    public void updatePennyDropStatus(PayoutResponseDTO payoutResponseDTO)
    {
        try
        {

            Map<String, Object> body = new HashMap<String, Object>();

            body.put("accountNumber", payoutResponseDTO.getAccountNumber());
            body.put("orderId", payoutResponseDTO.getOrderId());
            body.put("status", payoutResponseDTO.getStatus());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(LendingConstants.HEADER_X_API_KEY, apiToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            final String url = emiBaseUrl + "/api/v1/loan/callback/pennydrop";
            log.info("Update penny drop callback API url : {} and request : {}", url, request);

            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            log.info("PennyDrop Callback response: {}", responseEntity.getBody());
            if (responseEntity.getStatusCode().is2xxSuccessful())
            {
                log.info("PennyDrop Callback response: {}", responseEntity.getBody());
            }
            else
            {
                log.error("PennyDrop Callback failed with status code: {}", responseEntity.getStatusCode());
            }
        }
        catch (Exception e)
        {
            log.error("Exception in updatePennyDropStatus: {}", e.getMessage(), e);
        }
    }

    public void handleEmiPgCallback(PgPaymentCallbackDTO callbackDTO) {
        try {
            Map<String, Object> body = objectMapper.convertValue(callbackDTO, Map.class);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(LendingConstants.HEADER_X_API_KEY, pgCallbackApiToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            final String url = pgCallbackEmiBaseUrl + "/api/v1/payment/callback";
            log.info("pg status callback API url : {} and request : {}", url, request);

            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                log.info("Pg status Callback response: {}", responseEntity.getBody());
            } else {
                log.error("Pg status Callback failed with status code: {}", responseEntity.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Exception in calling emi pg status callback : {}", e.getMessage(), e);
        }
    }

    public SupportEmiResponseDTO handleSupportLoanEmiDetails(Long merchantId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(LendingConstants.HEADER_X_API_KEY, apiToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(headers);
            final String url = pgCallbackEmiBaseUrl + "/api/v1/collection/details" + "?merchantId=" + merchantId;
            log.info("crm emi loan details api url : {} and request : {}", url, request);

            ResponseEntity<EmiServiceResponse> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    EmiServiceResponse.class
            );
            log.info("RAW JSON Response: {}", responseEntity);
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                EmiServiceResponse emiResponse = responseEntity.getBody();
                System.out.println("Message: " + emiResponse.getResult().getMessage());
                SupportEmiResponseDTO supportResponseDTO = new SupportEmiResponseDTO(true,"SUCCESS");
                supportResponseDTO.setData(emiResponse);
                return supportResponseDTO;
            } else {
                log.error("crm emi loan details api failed with status code: {}", responseEntity.getStatusCode());
                return new SupportEmiResponseDTO(false, "Failed to fetch emi loan details");
            }
        } catch (Exception e) {
            log.error("Exception in calling crm emi loan details api : {}", Arrays.asList(e.getStackTrace()), e);
            return new SupportEmiResponseDTO(false, "Exception in fetch emi loan details");
        }
    }



}
