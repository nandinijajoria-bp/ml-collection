package com.bharatpe.lending.handlers;

import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.common.util.RestUtils;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.dto.KycDocResponseDTO;
import com.bharatpe.lending.dto.PanFetchKYCResponseDto;
import com.bharatpe.lending.dto.PanVerifyKYCResponseDto;
import com.bharatpe.lending.enums.KycDocStatus;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.loanV2.dto.InitiateKycDTO;
import com.bharatpe.lending.loanV2.dto.KycDocResponse;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.*;

@Component
@Slf4j
public class KycHandler {

    @Autowired
    ObjectMapper mapper;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    InternalClientDaoSlave internalClientDaoSlave;

    @Autowired
    AesEncryptionUtil aesEncryptionUtil;

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    Environment env;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    RestUtils restUtils;

    private static final String CLIENT = "LENDING";

    private static String clientSecret;

    private static final List<KycDocType> kycMandatoryDocs = Arrays.asList(KycDocType.PAN_NO, KycDocType.SELFIE, KycDocType.POA);

    private static final List<KycDocType> lenderKycPipeMandatoryDocs = Arrays.asList(KycDocType.PAN_NO, KycDocType.SELFIE);

    private HttpHeaders getApiHeaders(Map<String, Object> requestBody) {
        String payload = lendingHmacCalculator.getObjectPayload(requestBody);
        String hash = lendingHmacCalculator.calculateHmac(payload, getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(LendingConstants.HEADER_CLIENT_NAME, CLIENT);
        headers.set(LendingConstants.HEADER_HASH, hash);
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

    public List<KycDoc> getKycDoc(Long merchantId) {
        log.info("Getting Kyc docs for merchant:{}", merchantId);
        try {
            String docs = "PAN_NO,SELFIE,POA";
            Map<String, Object> requestParams = new HashMap<String, Object>(){{
                put("merchantId", merchantId);
                put("docs", docs);
                put("imgRequire", true);
                put("acceptRejected", true);
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, String>> request  = new HttpEntity<>(headers);
            final String url = env.getProperty("kyc.service.base.url") + LendingConstants.KYC_DOC_URL + "?merchantId=" + merchantId + "&docs=" + docs + "&imgRequire=true&acceptRejected=true";
            log.info("Get Kyc docs API url : {} and request : {} for merchant:{}", url, request, merchantId);
            ResponseEntity<KycDocResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, KycDocResponse.class);
            log.info("Get KYC docs response : {} for merchant:{}", responseEntity.getBody(), merchantId);
            if (Objects.nonNull(responseEntity.getBody()) && responseEntity.getBody().isStatus() && responseEntity.getBody().getData() != null) {
                return responseEntity.getBody().getData().getDocs();
            }
        } catch (Exception ex) {
            log.error("Exception in getKycDoc for merchant:{}, {}, {}", merchantId, ex, Arrays.asList(ex.getStackTrace()));
        }
        return null;
    }

    public List<KycDoc> getKycDoc(Long merchantId, Boolean acceptRejected, Boolean acceptDraft) {
        log.info("Getting Kyc docs for merchant:{}", merchantId);
        try {
            String docs = "PAN_NO,SELFIE,POA";
            Map<String, Object> requestParams = new HashMap<String, Object>(){{
                put("merchantId", merchantId);
                put("docs", docs);
                put("imgRequire", true);
                put("acceptRejected", acceptRejected);
                put("acceptDraft", acceptDraft);
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, String>> request  = new HttpEntity<>(headers);
            final String url = env.getProperty("kyc.service.base.url") + LendingConstants.KYC_DOC_URL + "?merchantId=" + merchantId + "&docs=" + docs + "&imgRequire=true&acceptRejected=" + acceptRejected + "&acceptDraft=" + acceptDraft;
            log.info("Get Kyc docs API url : {} and request : {} for merchant:{}", url, request, merchantId);
            ResponseEntity<KycDocResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, KycDocResponse.class);
            log.info("Get KYC docs response : {} for merchant:{}", responseEntity.getBody(), merchantId);
            if (Objects.nonNull(responseEntity.getBody()) && responseEntity.getBody().isStatus() && responseEntity.getBody().getData() != null) {
                return responseEntity.getBody().getData().getDocs();
            }
        } catch (Exception ex) {
            log.error("Exception in getKycDoc for merchant:{}, {}, {}", merchantId, ex, Arrays.asList(ex.getStackTrace()));
        }
        return null;
    }


    public List<KycDoc> getKycDoc(Long merchantId, Date validAfterDate, String provider) {
        log.info("Getting Kyc docs for merchant:{}", merchantId);
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String validAfter = sdf.format(validAfterDate);
            String docs = "PAN_NO,SELFIE,POA";
            Map<String, Object> requestParams = new HashMap<String, Object>(){{
                put("merchantId", merchantId);
                put("validAfter", validAfter);
                put("provider", provider);
                put("docs", docs);
                put("imgRequire", true);
                put("acceptRejected", true);
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, String>> request  = new HttpEntity<>(headers);
            final String url = env.getProperty("kyc.service.base.url") + LendingConstants.KYC_DOC_URL + "?merchantId=" + merchantId + "&docs=" + docs + "&imgRequire=true&acceptRejected=true" + "&validAfter=" + validAfter + "&provider=" + provider;
            log.info("Get Kyc docs API url : {} and request : {} for merchant:{}", url, request, merchantId);
            ResponseEntity<KycDocResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, KycDocResponse.class);
            log.info("Get KYC docs response : {} for merchant:{}", responseEntity.getBody(), merchantId);
            if (Objects.nonNull(responseEntity.getBody()) && responseEntity.getBody().isStatus() && responseEntity.getBody().getData() != null) {
                return responseEntity.getBody().getData().getDocs();
            }
        } catch (Exception ex) {
            log.error("Exception in getKycDoc for merchant:{}, {}, {}", merchantId, ex, Arrays.asList(ex.getStackTrace()));
        }
        return null;
    }

    public KycDocResponseDTO getKycDocs(Long merchantId, Date validAfterDate, String provider, String docs, Boolean acceptRejected, Boolean acceptDraft) {
        log.info("Getting Kyc docs for merchant:{}", merchantId);
        KycDocResponseDTO kycDocResponseDTO = new KycDocResponseDTO();
        try {
            String validAfter = "";
            if(!ObjectUtils.isEmpty(validAfterDate)) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                validAfter = sdf.format(validAfterDate);
            }
            String finaValidAfter = validAfter;
            Map<String, Object> requestParams = new HashMap<String, Object>(){{
                put("merchantId", merchantId);
                put("validAfter", finaValidAfter);
                put("provider", provider);
                put("docs", docs);
                put("imgRequire", true);
                put("acceptRejected", acceptRejected);
                put("acceptDraft", acceptDraft);
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, String>> request  = new HttpEntity<>(headers);
            final String url = env.getProperty("kyc.service.base.url") + LendingConstants.KYC_DOC_URL + "?merchantId=" + merchantId + "&docs=" + docs + "&imgRequire=true&acceptRejected=" + acceptRejected + "&validAfter=" + validAfter + "&provider=" + provider + "&acceptDraft=" + acceptDraft;
            log.info("Get Kyc docs API url : {} and request : {} for merchant:{}", url, request, merchantId);
            ResponseEntity<KycDocResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, KycDocResponse.class);
            log.info("Get KYC docs response : {} for merchant:{}", responseEntity.getBody(), merchantId);
            if (Objects.nonNull(responseEntity.getBody()) && responseEntity.getBody().isStatus() && responseEntity.getBody().getData() != null) {
                kycDocResponseDTO.setKycDocs(responseEntity.getBody().getData().getDocs());
                if(Objects.nonNull(responseEntity.getBody().getData().getEntity())){
                    kycDocResponseDTO.setEntityStatus(responseEntity.getBody().getData().getEntity().getStatus());
                }
                return kycDocResponseDTO;
            }
        } catch (Exception ex) {
            log.error("Exception in getKycDoc for merchant:{}, {}, {}", merchantId, ex, Arrays.asList(ex.getStackTrace()));
        }
        return null;
    }

    public KycStatusDTO getKycStatus(Long merchantId) {
        log.info("Checking kyc status for merchant:{}", merchantId);

        if(easyLoanUtil.isDummyMerchant(merchantId) || merchantId == 10407700L) {
            log.info("Merchant is Dummy, return kyc status as approved");
            return KycStatusDTO.builder().kycStatus(KycStatus.APPROVED).build();
        }

        try {
            List<KycDoc> kycDocs = getKycDoc(merchantId);
            if (!CollectionUtils.isEmpty(kycDocs)) {
                if (kycDocs.size() < kycMandatoryDocs.size()) return KycStatusDTO.builder().kycStatus(KycStatus.DRAFT).build();
                for (KycDoc kycDoc : kycDocs) {
                    if (kycDoc.getStatus() != null && kycDoc.getStatus().equals(KycDocStatus.REJECTED)) {
                        return KycStatusDTO.builder().kycDocType(kycDoc.getDocType()).kycStatus(KycStatus.REJECTED).remarks(kycDoc.getRemarks()).build();
                    }
                }
                for (KycDoc kycDoc : kycDocs) {
                    if (kycDoc.getStatus() != null && !kycDoc.getStatus().equals(KycDocStatus.REJECTED) && !kycDoc.getStatus().equals(KycDocStatus.APPROVED)) {
                        return KycStatusDTO.builder().kycDocType(kycDoc.getDocType()).kycStatus(KycStatus.valueOf(kycDoc.getStatus().name())).build();
                    }
                }

                return KycStatusDTO.builder().kycStatus(KycStatus.APPROVED).build();
            }
        } catch (Exception e) {
            log.error("Exception in getKycStatus for merchant:{}, {}, {}", merchantId, e, Arrays.asList(e.getStackTrace()));
        }
        return KycStatusDTO.builder().kycStatus(KycStatus.NEW).build();
    }

    public KycStatusDTO getKycStatusForLenderKycPipe(Long merchantId) {
        log.info("Checking kyc status with kyc on lender pipe for merchant:{} ", merchantId);

        if(easyLoanUtil.isDummyMerchant(merchantId) || merchantId == 10407700L) {
            log.info("Merchant is Dummy, return kyc status as approved");
            return KycStatusDTO.builder().kycStatus(KycStatus.APPROVED).build();
        }

        try {
            List<KycDoc> kycDocs = getKycDoc(merchantId, false, true);
            if (!CollectionUtils.isEmpty(kycDocs)) {
                if (kycDocs.size() < lenderKycPipeMandatoryDocs.size()) return KycStatusDTO.builder().kycStatus(KycStatus.DRAFT).build();
                for (KycDoc kycDoc : kycDocs) {
                    if (kycDoc.getStatus() != null && lenderKycPipeMandatoryDocs.contains(kycDoc.getDocType())) {
                        if (kycDoc.getStatus().equals(KycDocStatus.REJECTED)) {
                            return KycStatusDTO.builder()
                                    .kycDocType(kycDoc.getDocType())
                                    .kycStatus(KycStatus.REJECTED)
                                    .remarks(kycDoc.getRemarks())
                                    .build();
                        }
                        if (!kycDoc.getStatus().equals(KycDocStatus.APPROVED)
                                && !(kycDoc.getDocType().equals(KycDocType.SELFIE)
                                && kycDoc.getStatus().equals(KycDocStatus.DRAFT))) {
                            return KycStatusDTO.builder()
                                    .kycDocType(kycDoc.getDocType())
                                    .kycStatus(KycStatus.valueOf(kycDoc.getStatus().name()))
                                    .build();
                        }
                    }
                }

                return KycStatusDTO.builder().kycStatus(KycStatus.APPROVED).build();
            }
        } catch (Exception e) {
            log.error("Exception in getKycStatus with kyc on lender pipe for merchant:{}, {}, {}", merchantId, e, Arrays.asList(e.getStackTrace()));
        }
        return KycStatusDTO.builder().kycStatus(KycStatus.NEW).build();
    }

    public KycStatusDTO getKycStatus(Long merchantId, Date validAfterDate, String provider) {
        log.info("Checking kyc status for merchant:{}", merchantId);

        if(easyLoanUtil.isDummyMerchant(merchantId) || merchantId == 10407700L) {
            log.info("Merchant is Dummy, return kyc status as approved");
            return KycStatusDTO.builder().kycStatus(KycStatus.APPROVED).build();
        }

        try {
            List<KycDoc> kycDocs = getKycDoc(merchantId, validAfterDate, provider);
            if (!CollectionUtils.isEmpty(kycDocs)) {
                if (kycDocs.size() < kycMandatoryDocs.size()) return KycStatusDTO.builder().kycStatus(KycStatus.DRAFT).build();
                for (KycDoc kycDoc : kycDocs) {
                    if (kycDoc.getStatus() != null && kycDoc.getStatus().equals(KycDocStatus.REJECTED)) {
                        return KycStatusDTO.builder().kycDocType(kycDoc.getDocType()).kycStatus(KycStatus.REJECTED).remarks(kycDoc.getRemarks()).build();
                    }
                }
                for (KycDoc kycDoc : kycDocs) {
                    if (kycDoc.getStatus() != null && !kycDoc.getStatus().equals(KycDocStatus.REJECTED) && !kycDoc.getStatus().equals(KycDocStatus.APPROVED)) {
                        return KycStatusDTO.builder().kycDocType(kycDoc.getDocType()).kycStatus(KycStatus.valueOf(kycDoc.getStatus().name())).build();
                    }
                }

                return KycStatusDTO.builder().kycStatus(KycStatus.APPROVED).build();
            }
        } catch (Exception e) {
            log.error("Exception in getKycStatus for merchant:{}. {}, {}", merchantId, e, Arrays.asList(e.getStackTrace()));
        }
        return KycStatusDTO.builder().kycStatus(KycStatus.NEW).build();
    }

    public KycStatusDTO getKycStatus(List<KycDoc> kycDocs, Long merchantId){

        if(easyLoanUtil.isDummyMerchant(merchantId) || merchantId == 10407700L) {
            log.info("Merchant is Dummy, return kyc status as approved");
            return KycStatusDTO.builder().kycStatus(KycStatus.APPROVED).build();
        }

        try {
            if (!CollectionUtils.isEmpty(kycDocs)) {
                if (kycDocs.size() < kycMandatoryDocs.size()) return KycStatusDTO.builder().kycStatus(KycStatus.DRAFT).build();
                for (KycDoc kycDoc : kycDocs) {
                    if (kycDoc.getStatus() != null && kycDoc.getStatus().equals(KycDocStatus.REJECTED)) {
                        return KycStatusDTO.builder().kycDocType(kycDoc.getDocType()).kycStatus(KycStatus.REJECTED).remarks(kycDoc.getRemarks()).build();
                    }
                }
                for (KycDoc kycDoc : kycDocs) {
                    if (kycDoc.getStatus() != null && !kycDoc.getStatus().equals(KycDocStatus.REJECTED) && !kycDoc.getStatus().equals(KycDocStatus.APPROVED)) {
                        return KycStatusDTO.builder().kycDocType(kycDoc.getDocType()).kycStatus(KycStatus.valueOf(kycDoc.getStatus().name())).build();
                    }
                }

                return KycStatusDTO.builder().kycStatus(KycStatus.APPROVED).build();
            }
        } catch (Exception e) {
            log.error("Exception in getKycStatus for merchant:{}, {}, {}", merchantId, e, Arrays.asList(e.getStackTrace()));
        }
        return KycStatusDTO.builder().kycStatus(KycStatus.NEW).build();
    }

    public Map<String,String> initiateKyc(Long merchantId, InitiateKycDTO initiateKycDTO, List<KycDocType> docTypes) {
        log.info("Initiate kyc for merchant:{}", merchantId);
        Map<String, String> responseObj = new HashMap<>();
        try {
            List<Map<String, String>> documents = new ArrayList<>();
            for (KycDocType docType : docTypes) {
                documents.add(new HashMap<String, String>(){{put("docType", docType.getVal());}});
            }
            Map<String, Object> requestParams = new HashMap<String, Object>(){{
                put("callBackUrl", initiateKycDTO.getCallBackUrl());
                put("product", "LOAN");
                put("referenceId", initiateKycDTO.getReferenceId());
                put("panNumber", initiateKycDTO.getPanNumber());
                put("merchantId", initiateKycDTO.getMerchantId());
                put("documents", documents);
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, Object>> request  = new HttpEntity<>(requestParams, headers);
            final String url = env.getProperty("kyc.service.base.url") + LendingConstants.KYC_INITIATE_URL;
            log.info("Initiate Kyc API url : {} and request : {} for merchant:{}", url, request, merchantId);
            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("Initiate Kyc response : {} for merchant:{}", responseEntity, merchantId);

            if (Objects.nonNull(responseEntity.getBody())) {
                JsonNode jsonNode =  mapper.readTree(responseEntity.getBody());
                if (jsonNode != null) {
                    if (jsonNode.has("requestorId")) {
                        responseObj.put("ckycId", jsonNode.get("requestorId").asText());
                    }
                    if (jsonNode.has("message")) {
                        responseObj.put("message", jsonNode.get("message").asText());
                    }
                    if (jsonNode.has("callBackUrl")){
                        responseObj.put("callBackUrl", jsonNode.get("callBackUrl").asText());
                    }
                }
            }
        } catch (Exception e) {
            responseObj.put("message", "Error initiating KYC");
            log.error("Exception in initiateKyc for merchant:{}, {}, {}", merchantId, e, Arrays.asList(e.getStackTrace()));
        }
        return responseObj;
    }

    public Map<String,String> initiateKyc(Long merchantId, InitiateKycDTO initiateKycDTO, List<KycDocType> docTypes, Date validAfterDate, Boolean onlySelfieLivelinessRequired) {
        log.info("Initiate kyc for merchant:{}", merchantId);
        Map<String, String> responseObj = new HashMap<>();
        try {
            String validAfter = null;
            if(!ObjectUtils.isEmpty(validAfterDate)) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
               validAfter = sdf.format(validAfterDate);
            }
            String finaValidAfter = validAfter;
            List<Map<String, String>> documents = new ArrayList<>();
            for (KycDocType docType : docTypes) {
                documents.add(new HashMap<String, String>(){{put("docType", docType.getVal());}});
            }
            Map<String, Object> requestParams = new HashMap<String, Object>(){{
                put("callBackUrl", initiateKycDTO.getCallBackUrl());
                put("product", "LOAN");
                put("referenceId", initiateKycDTO.getReferenceId());
                put("panNumber", initiateKycDTO.getPanNumber());
                put("merchantId", initiateKycDTO.getMerchantId());
                put("documents", documents);
                put("validAfter", finaValidAfter);
                put("onlySelfieLivelinessRequired", onlySelfieLivelinessRequired);
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, Object>> request  = new HttpEntity<>(requestParams, headers);
            final String url = env.getProperty("kyc.service.base.url") + LendingConstants.KYC_INITIATE_URL;
            log.info("Initiate Kyc API url : {} and request : {} for merchant:{}", url, request, merchantId);
            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("Initiate Kyc response : {} for merchant:{}", responseEntity, merchantId);

            if (Objects.nonNull(responseEntity.getBody())) {
                JsonNode jsonNode =  mapper.readTree(responseEntity.getBody());
                if (jsonNode != null) {
                    if (jsonNode.has("requestorId")) {
                        responseObj.put("ckycId", jsonNode.get("requestorId").asText());
                    }
                    if (jsonNode.has("message")){
                        responseObj.put("message", jsonNode.get("message").asText());
                    }
                    if (jsonNode.has("callBackUrl")){
                        responseObj.put("callBackUrl", jsonNode.get("callBackUrl").asText());
                    }
                }
            }
        } catch (Exception e) {
            responseObj.put("message", "Error initiating KYC");
            log.error("Exception in initiateKyc for merchant:{}, {}, {}", merchantId, e, Arrays.asList(e.getStackTrace()));
        }
        return responseObj;
    }

    public String getPanNumber(Long merchantId) {
        log.info("Getting pan details for merchant:{}", merchantId);
        try {
            Map<String, Object> requestParams = new HashMap<String, Object>(){{
                put("identifier", merchantId);
                put("userType", "MERCHANT");
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, Object>> request  = new HttpEntity<>(requestParams, headers);
            final String url = env.getProperty("kyc.service.base.url") + LendingConstants.KYC_PAN_NO_URL;
            log.info("Get pan details API url : {} and request : {} for merchant:{}", url, request, merchantId);
            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("Get pan details response : {} for merchant:{}", responseEntity, merchantId);
            if (Objects.nonNull(responseEntity.getBody())) {
                JsonNode jsonNode =  mapper.readTree(responseEntity.getBody());
                if (jsonNode != null && jsonNode.get("data") != null && jsonNode.get("data").get("panNo") != null)
                    return jsonNode.get("data").get("panNo").asText();
            }
        } catch (Exception e) {
            log.error("Exception in getPanNumber for merchant:{}", merchantId, e);
        }
        return null;
    }

    public String getPanName(String panNumber, Long merchantId) throws Exception {
        if (ObjectUtils.isEmpty(panNumber)) {
            log.info("PanNumber of merchantId : {} is empty", merchantId);
            return null;
        }
        HashMap<String, String> data;
        String url = env.getProperty("kyc.service.base.url") + LendingConstants.PAN_NAME;
        Map<String, Object> payload = new HashMap<>();
        payload.put("panNumber", panNumber);
        payload.put("customerId", merchantId);
        payload.put("identifier", merchantId);
        payload.put("userType", "MERCHANT");
        String hash = lendingHmacCalculator.calculateHmac(lendingHmacCalculator.getObjectPayload(payload), getInternalSecret());;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        headers.put("clientName", CLIENT);
        headers.put("hash", hash);

        Map<String, Object> res = restUtils.postForObject(url, headers, payload, Map.class, RestUtils.ExceptionLevel.INFO);
        log.info("res {}", res.get("data"));
        if ((boolean) res.get("status")) {
            data = (HashMap<String, String>) res.get("data");
            return data.get("name");
        }
        return null;
    }

    public PanFetchKYCResponseDto panFetch(String token, String panNumber, Long merchantId) {
        if (ObjectUtils.isEmpty(panNumber)) {
            log.info("PanNumber of merchantId : {} is empty", merchantId);
            return null;
        }
        try {
            String url = env.getProperty("kyc.service.base.url") + LendingConstants.PAN_FETCH;

            Map<String, Object> payload = new HashMap<>();
            payload.put("panNumber", panNumber);
            payload.put("userType", "MERCHANT");
            payload.put("source", "LOAN");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            headers.set("token", token);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            log.info("Pan Fetch request for merchantId: {}, request: {} url: {}", merchantId, mapper.writeValueAsString(request), url);
            ResponseEntity<PanFetchKYCResponseDto> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, PanFetchKYCResponseDto.class);
            if(Objects.isNull(responseEntity.getBody())){
                return null;
            }
            log.info("Pan Fetch Response {} for merchantId: {}", mapper.writeValueAsString(responseEntity.getBody()),merchantId);
            if(responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.hasBody()) {
                return responseEntity.getBody();
            }
        }catch (HttpClientErrorException.TooManyRequests exception) {
            log.error("Exception in fetching pan details for merchantId:{} {}",merchantId, exception.getMessage());
            throw exception;
        }catch (Exception e) {
            log.error("Error occurred while fetching pan details for merchantId:{}", merchantId, e);
        }
        return null;
    }

    public PanVerifyKYCResponseDto verifyPanDetails(String token, String panNumber, String name, String dob, Long merchantId) throws Exception {
        if (ObjectUtils.isEmpty(panNumber) || ObjectUtils.isEmpty(name) || ObjectUtils.isEmpty(dob)) {
            log.info("PanNumber: {}, name: {} & dob: {} of merchantId : {}",panNumber, name, dob, merchantId);
            return null;
        }
        try {
            String url = env.getProperty("kyc.service.base.url") + LendingConstants.PAN_VERIFY;

            Map<String, Object> payload = new HashMap<>();
            payload.put("panNumber", panNumber);
            payload.put("dob", dob);
            payload.put("name", name);
            payload.put("userType", "MERCHANT");
            payload.put("source", "LOAN");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            headers.set("token", token);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            log.info("Pan Verify request for merchantId: {}, request: {} url: {}", merchantId, mapper.writeValueAsString(request), url);
            ResponseEntity<PanVerifyKYCResponseDto> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, PanVerifyKYCResponseDto.class);
            if(Objects.isNull(responseEntity.getBody())){
                return null;
            }
            log.info("Pan Verify Response {} for merchantId: {}", mapper.writeValueAsString(responseEntity.getBody()),merchantId);
            if(responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.hasBody()) {
                return responseEntity.getBody();
            }
        }catch (HttpClientErrorException.TooManyRequests exception) {
            log.info("Too Many Requests error while verifying pan details for merchantId:{} {}",merchantId, exception.getMessage());
            throw exception;
        }catch (Exception e) {
            log.error("Error occurred while verifying pan details for merchantId {}", merchantId, e);
        }
        return null;
    }

    public List<KycDoc> getPan(Long merchantId) {
        log.info("Getting PAN for merchant:{}", merchantId);
        try {
            String docs = "PAN_NO";
            Map<String, Object> requestParams = new HashMap<String, Object>(){{
                put("merchantId", merchantId);
                put("docs", docs);
                put("acceptRejected", false);
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, String>> request  = new HttpEntity<>(headers);
            final String url = env.getProperty("kyc.service.base.url") + LendingConstants.KYC_DOC_URL + "?merchantId=" + merchantId + "&docs=" + docs + "&acceptRejected=false";
            log.info("Get Kyc docs API url : {} and request : {} for merchant:{}", url, request, merchantId);
            ResponseEntity<KycDocResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, KycDocResponse.class);
            log.info("Get KYC docs response : {} for merchant:{}", responseEntity.getBody(), merchantId);
            if (Objects.nonNull(responseEntity.getBody()) && responseEntity.getBody().isStatus() && responseEntity.getBody().getData() != null) {
                return responseEntity.getBody().getData().getDocs();
            }
        } catch (Exception ex) {
            log.error("Exception in getKycDoc for merchant:{}", merchantId, ex);
        }
        return null;
    }

    public KycStatus getPanStatus(Long merchantId){

        List<KycDoc> kycDocs = getPan(merchantId);
        if(easyLoanUtil.isDummyMerchant(merchantId) || merchantId == 10407700L) {
            log.info("Merchant is Dummy, return kyc status as approved");
            return KycStatus.APPROVED;
        }

        try {
            if (!CollectionUtils.isEmpty(kycDocs)) {
                if (kycDocs.size() < 1) return KycStatus.DRAFT;
                for (KycDoc kycDoc : kycDocs) {
                    if (kycDoc.getStatus() != null && kycDoc.getStatus().equals(KycDocStatus.REJECTED)) {
                        return KycStatus.REJECTED;
                    }
                    if (kycDoc.getStatus() != null && !kycDoc.getStatus().equals(KycDocStatus.REJECTED) && !kycDoc.getStatus().equals(KycDocStatus.APPROVED)) {
                        return KycStatus.valueOf(kycDoc.getStatus().name());
                    }
                }
                return KycStatus.APPROVED;
            }
        } catch (Exception e) {
            log.error("Exception in getKycStatus for merchant:{}", merchantId, e);
        }
        return KycStatus.NEW;
    }

    public PanFetchKYCResponseDto panFetch(String panNumber, Long merchantId) {
        try {
            String url = env.getProperty("kyc.service.base.url") + LendingConstants.PAN_FETCH_INTERNAL;

            Map<String, Object> payload = new HashMap<>();
            payload.put("panNumber", panNumber);
            payload.put("merchantId", merchantId);
            payload.put("userType", "MERCHANT");
            payload.put("source", "LOAN");

            HttpHeaders headers = getApiHeaders(payload);
            HttpEntity<Map<String, Object>> request  = new HttpEntity<>(payload, headers);
            log.info("Pan Fetch request for merchantId: {}, request: {} url: {}", merchantId, mapper.writeValueAsString(request), url);
            ResponseEntity<PanFetchKYCResponseDto> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, PanFetchKYCResponseDto.class);
            log.info("Pan Fetch Response for merchantId: {}, {}", responseEntity.getBody() ,merchantId);
            return responseEntity.getBody();
        } catch (HttpClientErrorException exception) {
            log.error("Exception in fetching pan details for merchantId:{} {}",merchantId, exception.getMessage());
        }catch (Exception e) {
            log.error("Error occurred while fetching pan details for merchantId:{}", merchantId, e);
        }
        return null;
    }
}
