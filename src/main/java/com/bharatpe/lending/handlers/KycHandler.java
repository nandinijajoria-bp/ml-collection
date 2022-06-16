package com.bharatpe.lending.handlers;

import com.bharatpe.lending.common.slave.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.slave.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.KycDoc;
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
import org.springframework.web.client.RestTemplate;

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

    private static final String CLIENT = "LENDING";

    private static String clientSecret;

    private static final List<KycDocType> kycMandatoryDocs = Arrays.asList(KycDocType.PAN_NO, KycDocType.PAN_CARD, KycDocType.SELFIE, KycDocType.POA);

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
            String docs = "PAN_NO,PAN_CARD,SELFIE,POA";
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
            log.error("Exception in getKycDoc for merchant:{}", merchantId, ex);
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
            log.error("Exception in getKycStatus for merchant:{}", merchantId, e);
        }
        return KycStatusDTO.builder().kycStatus(KycStatus.NEW).build();
    }

    public String initiateKyc(Long merchantId, InitiateKycDTO initiateKycDTO, List<KycDocType> docTypes) {
        log.info("Initiate kyc for merchant:{}", merchantId);
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
                if (jsonNode != null && jsonNode.has("requestorId"))
                    return jsonNode.get("requestorId").asText();
            }
        } catch (Exception e) {
            log.error("Exception in initiateKyc for merchant:{}", merchantId, e);
        }
        return null;
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
}
