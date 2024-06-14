package com.bharatpe.lending.handlers;

import com.bharatpe.lending.common.dto.KafkaAudit;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.loanV2.dto.DeGetReferencesResponse;
import com.bharatpe.lending.loanV2.dto.DsValidateReferencesResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.bharatpe.lending.dto.DSfetchMilestoneAchievementsAudit;
import java.util.*;

@Slf4j
@Component
public class DsHandler {
    @Autowired
    RestTemplate restTemplate;

    @Value("${ds.reference.base.url}")
    String dsBaseUrl;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${de.reference.base.url}")
    String deBaseUrl;

    @Value("${de.reference.milestone.base.url}")
    String deMileStoneBaseUrl;


    @Value("${ds.inferred.reference.base.url}")
    String globalDsBaseUrl;

    @Value("${ds.api.url}")
    String dsApiUrl;

    @Autowired
    ObjectMapper mapper;


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

    public DeGetReferencesResponse getMerchantReferences(Long merchantId, Integer minScore, Integer limit,Long applicationId) {
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
            DeGetMerchantReferencesAudit auditData = new DeGetMerchantReferencesAudit(applicationId,responseEntity);
            KafkaAudit<DeGetMerchantReferencesAudit> kafkaAudit = new KafkaAudit<>("easy_loan", "lending", "de_get_references_response_audit", null);
            kafkaAudit.setData(auditData);

            pushKafkaAudit(kafkaAudit);

            log.info("DE Get Merchant References for merchantId: {}, responseEntity : {} ", merchantId, responseEntity);
            if (Objects.nonNull(responseEntity) && Objects.nonNull(responseEntity.getBody()) && responseEntity.getBody().getStatus().equals("success") && responseEntity.getBody().getData() != null) {
                return responseEntity.getBody();
            }
        } catch (Exception e) {
            log.error("Exception occurred while getting merchant references from DE api of merchantId: {}, {}", merchantId, e);
        }
        return null;
    }

    public void pushKafkaAudit(KafkaAudit kafkaAudit) {
        try {
            log.info("pushing kafka event for {}", kafkaAudit);
            kafkaTemplate.send("easyloan_audit_data",kafkaAudit);
        } catch (Exception e) {
            log.error("error while sending audit data {} {}", kafkaAudit, Arrays.asList(e.getStackTrace()));
        }
    }

    public Map<String, Double> fetchDsLocation(Long merchantId) {
        Map<String, Double> responseMap = new HashMap<>();
        try {
            DSMainResponse response = fetchDSMainVariables(merchantId, null);
            if(Objects.nonNull(response) && Objects.nonNull(response.getLocation()) && Objects.nonNull(response.getLocation().getInferredLat()) && Objects.nonNull(response.getLocation().getInferredLon())){
                responseMap.put("latitude", Double.valueOf(response.getLocation().getInferredLat()));
                responseMap.put("longitude", Double.valueOf(response.getLocation().getInferredLon()));
                log.info("DSApiService: fetchDsLocation: responseMap: {}", responseMap);
                return responseMap;
            }
        }
        catch(Exception e){
            log.info("Error in fetching Inferred Location from DS API for merchant:{}, {}, {}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return responseMap;
    }

    public DSMainResponse fetchDSMainVariables(Long merchantId, Long applicationId) {
        try {
            log.info("Request to fetch DS main variables for merchant:{}", merchantId);
            String url = dsApiUrl + "/" + merchantId;
            if (Objects.nonNull(applicationId)) {
                url += "?application_id=" + applicationId;
            }
            long start = System.currentTimeMillis();
            ResponseEntity<DSMainResponse> responseEntity = restTemplate.getForEntity(url, DSMainResponse.class);
            long end = System.currentTimeMillis();

            log.info("fetchDSMainVariables responseEntity : {}", responseEntity);

            log.info("DS main service response time {}ms", end - start);
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                log.info("Found DS main variables:{} for merchant:{}", responseEntity.getBody(), merchantId);
                return responseEntity.getBody();
            }
        } catch (HttpClientErrorException e) {
            log.info("Exception while fetching DS main variables for merchant:{}", merchantId, e);
        } catch (Exception e) {
            log.error("Exception while fetching DS main variables for merchant:{}", merchantId, e);
        }
        return null;
    }

    public DSMileStoneResponse fetchMileStoneData(Long merchantId, Double bureauScore, Double bbsScore, String pincodeColor) {
        try {
            log.info("Request to fetch DS milestones for merchantId:{}", merchantId);
            HttpHeaders headers = new HttpHeaders();
            headers.add("accept", MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Object> request = new HttpEntity<>(headers);
            String url = deMileStoneBaseUrl + "/merchant_milestone/v2" + "?merchant_id=" + merchantId + "&bureauScore=" + bureauScore + "&bbsScore=" + bbsScore + "&pincodeColor=" + pincodeColor;

            log.info("DE get MileStone for merchantId: {}, request: {} url: {}", merchantId, mapper.writeValueAsString(request), url);

            ResponseEntity<DSMileStoneResponse> responseEntity = null;
            try {
                responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, DSMileStoneResponse.class);
                log.info("response {} of target for merchantid {} is ", responseEntity.getBody(),merchantId);
                if (responseEntity.getBody() != null && responseEntity.getStatusCode().is2xxSuccessful()) {
                    return responseEntity.getBody();
                }
            } catch (HttpClientErrorException e) {
                log.error("Exception in Http Client while fetching DS milestones for merchant:{} error is: {}", merchantId, e.getResponseBodyAsString());
            }
        } catch (Exception e) {
            log.error("Exception while fetching milestone data for merchant: {} and error: {}", merchantId, e.getStackTrace());
        }
        return null;
    }

    public DSMileStoneResponse fetchMileStoneDatav3(Long merchantId, Double bureauScore, Double bbsScore, String pincodeColor, String loanAmount) {
        try {
            log.info("Request to fetch DS milestones for merchantId:{}", merchantId);
            HttpHeaders headers = new HttpHeaders();
            headers.add("accept", MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Object> request = new HttpEntity<>(headers);
            String url = deMileStoneBaseUrl + "/merchant_milestone/v3" + "?merchant_id=" + merchantId + "&bureauScore=" + bureauScore + "&bbsScore=" + bbsScore + "&pincodeColor=" + pincodeColor + "&loanAmount=" + loanAmount;

            log.info("DE get MileStone for merchantId: {}, request: {} url: {}", merchantId, mapper.writeValueAsString(request), url);

            ResponseEntity<DSMileStoneResponse> responseEntity = null;
            try {
                responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, DSMileStoneResponse.class);
                log.info("response {} of target for merchantid {}", responseEntity.getBody(),merchantId);
                if (responseEntity.getBody() != null && responseEntity.getStatusCode().is2xxSuccessful()) {
                    return responseEntity.getBody();
                }
            } catch (HttpClientErrorException e) {
                log.error("Exception in Http Client while fetching DS milestones for merchant:{} error is: {}", merchantId, e.getResponseBodyAsString());
            }
        } catch (Exception e) {
            log.error("Exception while fetching milestone data for merchant: {} and error: {}", merchantId, e.getStackTrace());
        }
        return null;
    }


    public DSMileStoneAchievementResponse fetchMilestoneAchievements(Long merchantId,String sessionId)
    {
        try {
            log.info("Request to fetch Achieve milestones for merchantId:{} and sessionId: {}", merchantId, sessionId);
            HttpHeaders headers = new HttpHeaders();
            headers.add("accept", MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Object> request = new HttpEntity<>(headers);
            String url = deMileStoneBaseUrl + "/merchant_achievement" + "?merchant_id=" + merchantId + "&sessionId=" + sessionId;

            log.info("DE Achieve MileStone for merchantId: {}, request: {} url: {}", merchantId, mapper.writeValueAsString(request), url);
            ResponseEntity<DSMileStoneAchievementResponse> responseEntity = null;
            try {
                responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, DSMileStoneAchievementResponse.class);
                log.info("response {} of achievements for merchantid {}",responseEntity.getBody(),merchantId);
                if (responseEntity.getBody() != null && responseEntity.getStatusCode().is2xxSuccessful()) {
                    return responseEntity.getBody();
                }
            } catch (HttpClientErrorException e) {
                log.error("Exception in Http Client while fetching Achieve milestones for merchant:{} error is: {}", merchantId, e.getResponseBodyAsString());
            }
        } catch (Exception e) {
            log.error("Exception while fetching Achieve milestone data for merchant: {} and error: {}", merchantId, e.getStackTrace());

        }
        return null;
    }
    public void pushMilestoneAchievementData(Long merchantId, DSMileStoneAchievementResponse dSMileStoneAchievementResponse)
    {
        try {
            if(!dSMileStoneAchievementResponse.getAchievement().isEmpty()) {
                DSfetchMilestoneAchievementsAudit auditData = new DSfetchMilestoneAchievementsAudit(merchantId, dSMileStoneAchievementResponse);
                KafkaAudit<DSfetchMilestoneAchievementsAudit> kafkaAudit = new KafkaAudit<>("easy_loan", "lending", "de_milestone_achievements_response_audit", null);
                kafkaAudit.setData(auditData);
                pushKafkaAudit(kafkaAudit);
                log.info("DE Get Milestone Achievements for merchantId: {}, response : {} ", merchantId, dSMileStoneAchievementResponse);
            }
        }
        catch(Exception e)
        {
            log.error("Exception while pushing Milestone Achievement data for merchantId {} {}",merchantId , Arrays.asList(e.getStackTrace()));
        }

    }

    public DEPinCode getInferredPinCode(Long merchantId, Double latitude, Double longtitude) {

        try {
            log.info("Request to get Pincode for merchantId:{} ", merchantId);
            HttpHeaders headers = new HttpHeaders();
            headers.add("accept", MediaType.APPLICATION_JSON_VALUE);
            headers.add("content-type", MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("lat", latitude);
            requestMap.put("lon", longtitude);
            HttpEntity<Object> request = new HttpEntity<>(requestMap, headers);

            String url = globalDsBaseUrl + "/geo-info";

            log.info("Request for Inferred Pincode for merchantId: {}, request: {} url: {}", merchantId, mapper.writeValueAsString(request), url);
            ResponseEntity<DEPinCode> responseEntity = null;
            try {
                responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, DEPinCode.class);
                log.info("response {} of inferred pincode for merchantid {}",responseEntity.getBody(),merchantId);
                if (responseEntity.getBody() != null && responseEntity.getStatusCode().is2xxSuccessful()) {
                    return responseEntity.getBody();
                }
            } catch (HttpClientErrorException e) {
                log.error("Exception in Http Client while fetching Pincode for merchant:{} error is: {}", merchantId, e.getResponseBodyAsString());
            }
        } catch (Exception e) {
            log.error("Exception while fetching Pincode for merchant: {} and error: {}", merchantId, e.getStackTrace());

        }
        return null;
    }
    public String fetchDsShopType(Long merchantId) {
        try {
            DSMainResponse response = fetchDSMainVariables(merchantId, null);
            if(Objects.nonNull(response) && Objects.nonNull(response.getLocation()) && Objects.nonNull(response.getLocation().getInferredLat()) && Objects.nonNull(response.getLocation().getInferredLon())){
                String shopType = null;
                if(!ObjectUtils.isEmpty(response.getVisionResponse().getMeta().getShopFrontStructure().getClassifier())) {
                    shopType = response.getVisionResponse().getMeta().getShopFrontStructure().getClassifier();
                }
                shopType = shopType.substring(0,1).toUpperCase() +  shopType.substring(1).toLowerCase();
                log.info("DSApiService: fetchDsShopType: response: {}", shopType);
                return shopType;
            }
        }
        catch(Exception e){
            log.info("Error in fetching Inferred Location from DS API for merchant:{}, {}, {}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

}