package com.bharatpe.lending.handlers;

import com.bharatpe.lending.dto.MerchantReference;
import com.bharatpe.lending.dto.ValidateMerchantReferencesRequestDto;
import com.bharatpe.lending.loanV2.dto.DeGetReferencesResponse;
import com.bharatpe.lending.loanV2.dto.DsValidateReferencesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class DsHandler {
    @Autowired
    RestTemplate restTemplate;

    @Value("${ds.reference.base.url}")
    String dsBaseUrl;

    @Value("${de.reference.base.url}")
    String deBaseUrl;

    public List<MerchantReference> validateMerchantReferences(Long merchantId, List<ValidateMerchantReferencesRequestDto> referenceList) {
        log.info("Start validating merchant references: {} of merchantId: {} from DS", referenceList, merchantId);
        try {
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("contacts", referenceList);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Basic ZHNfdXNlcjpkc0BiaGFyYXRwZTEyMw==");
            headers.add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36");

            String url =
                    dsBaseUrl + "/contact_details";
            HttpEntity<Object> request = new HttpEntity<>(requestMap, headers);
            log.info("DS validate merchant references request :{} url: {}", request, url);
            ResponseEntity<DsValidateReferencesResponse> responseEntity = restTemplate
                    .exchange(url, HttpMethod.POST, request, DsValidateReferencesResponse.class);

            log.info("DS Validate Merchant References responseEntity : {} for merchantId: {}", responseEntity, merchantId);
            log.info("DS Validate Merchant References response : {} for merchantId: {}", responseEntity.getBody(), merchantId);
            if (Objects.nonNull(responseEntity.getBody()) && responseEntity.getBody().getStatus().equals("success")) {
                return responseEntity.getBody().getContacts();
            }

        } catch (Exception e) {
            log.error("Exception occurred while validating merchant references from DS api of merchantId: {}, {}", merchantId, e);
        }
        return null;
    }

    public DeGetReferencesResponse getMerchantReferences(Long merchantId, Integer minScore, Integer limit) {
        log.info("Start getting merchant references from DE of merchantId: {}", merchantId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("accept", MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Object> request = new HttpEntity<>(headers);
            String url = deBaseUrl + "/GetMerchantConfidenceScore" + "?merchant_id=" + merchantId + "&min_score=" + minScore + "&limit=" + limit;
            log.info("DE get merchant references for merchantId: {}, request :{} url: {}", merchantId, request, url);
            ResponseEntity<DeGetReferencesResponse> responseEntity = null;
            int retryCount = 0;
            while (retryCount < 3) {
                try {
                    responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, DeGetReferencesResponse.class);
                    break;
                } catch (Exception e) {
                    //When fetching contacts for any merchant_id for first time then this api is giving read time our error.
                    retryCount++;
                    log.error("Retrying: {} time, Exception occurred while getting merchant references from DE api of merchantId: {}, {}", retryCount, merchantId, e);
                }
            }
            log.info("DE Get Merchant References for merchantId: {}, responseEntity : {} ", responseEntity, merchantId);
            if (Objects.nonNull(responseEntity) && Objects.nonNull(responseEntity.getBody()) && responseEntity.getBody().getStatus().equals("success") && responseEntity.getBody().getData() != null) {
                return responseEntity.getBody();
            }
        } catch (Exception e) {
            log.error("Exception occurred while getting merchant references from DE api of merchantId: {}, {}", merchantId, e);
        }
        return null;
    }

}