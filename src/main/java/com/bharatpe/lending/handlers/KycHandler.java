package com.bharatpe.lending.handlers;

import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.common.util.RestUtils;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.KycDocStatus;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.ShopPhotoProofType;
import com.bharatpe.lending.loanV2.dto.InitiateKycDTO;
import com.bharatpe.lending.loanV2.dto.KycDocResponse;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.FileNotFoundException;
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
    @Autowired
    private LendingShopDocumentsDao lendingShopDocumentsDao;
    @Autowired
    private S3BucketHandler s3BucketHandler;

    @Value("${kyc.service.base.url}")
    private String kycServiceHost;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${enable.p2pm.flag:false}")
    boolean p2pmEnabled;

    @Value("${skip.screen.rollout:0}")
    private Integer skipScreenRolloutForMerchants;

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

    public List<KycDoc> getKycDoc(Long merchantId, Boolean acceptRejected, Boolean acceptDraft, String docs) {
        log.info("Getting Kyc docs for merchant:{}", merchantId);
        try {
            Map<String, Object> requestParams = new HashMap<String, Object>(){{
                put("merchantId", merchantId);
                put("docs", docs);
                put("imgRequire", true);
                put("acceptRejected", acceptRejected);
                put("acceptDraft", acceptDraft);
                put("returnMultipleSubDocTypes", "BUSINESSDOCS".equalsIgnoreCase(docs));
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, String>> request  = new HttpEntity<>(headers);
            final String url = env.getProperty("kyc.service.base.url") + LendingConstants.KYC_DOC_URL + "?merchantId=" + merchantId + "&docs=" + docs + "&imgRequire=true&acceptRejected=" + acceptRejected + "&acceptDraft=" + acceptDraft + "&returnMultipleSubDocTypes=" + requestParams.get("returnMultipleSubDocTypes");
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

    @Async("commonAsyncTaskExecutor")
    public void syncShopPhoto(Long merchantId, Long applicationId){
        List<LendingShopDocuments> lendingShopDocumentList = lendingShopDocumentsDao.findByMerchantIdAndApplicationIdOrderByUpdatedAtDesc(merchantId, applicationId);
        for(LendingShopDocuments lendingShopDocument: lendingShopDocumentList){
            try {
                String imageUrl = s3BucketHandler.getTemporaryPublicURL(lendingShopDocument.getProofFrontSide(), bucket);
                String imageDocType = getPhotoDocType(lendingShopDocument.getProofType());
                if(imageDocType!=null){
                    syncImage(lendingShopDocument, imageUrl, imageDocType);
                }else {
                    log.warn("skipping image sync because of wrong proof type, proof type is: {}", lendingShopDocument.getProofType());
                }
            }catch (FileNotFoundException exception){
                log.error("File not found exception while generating s3 link for merchant: {} and application: {} of image_id: {} ",
                        lendingShopDocument.getMerchantId(), lendingShopDocument.getApplicationId(), lendingShopDocument.getId());
            }
        }
    }

    private void syncImage(LendingShopDocuments lendingShopDocument, String imageUrl, String imageDocType) {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("merchantId", lendingShopDocument.getMerchantId());
        requestData.put("docType", imageDocType);
        requestData.put("status", "PENDING");
        requestData.put("source", "LOAN");
        requestData.put("latitude", lendingShopDocument.getLatitude());
        requestData.put("longitude", lendingShopDocument.getLongitude());
        requestData.put("docUrl", imageUrl);

        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("type", "add");
        requestParams.put("data", requestData);

        String url = kycServiceHost + LendingConstants.UPLOAD_SHOP_IMAGE;
        HttpHeaders headers = getApiHeaders(requestParams);
        HttpEntity<Map<String, Object>> request  = new HttpEntity<>(requestParams, headers);
        int retry = 2;
        while (retry>0){
            try {
                log.info("Request for image sync to central service for merchant: {}, and application: {} is {}",
                        lendingShopDocument.getMerchantId(), lendingShopDocument.getApplicationId(), request);
                ResponseEntity<UploadShopImageResponse> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, UploadShopImageResponse.class);
                log.info("Response received from central service for image sync for merchant: {}, and application: {} is {}",
                        lendingShopDocument.getMerchantId(), lendingShopDocument.getApplicationId(), responseEntity);
                UploadShopImageResponse response = responseEntity.getBody();
                if(response!=null && response.getData() !=null && response.getData().isSuccess()){
                    break;
                }
            }catch (RestClientException exception){
                log.error("Rest client exception while syncing image to central services for merchant: {} and application: {}. shop document id is: {}. exception is:{}",
                        lendingShopDocument.getMerchantId(), lendingShopDocument.getApplicationId(), lendingShopDocument.getId(), exception.getMessage());
            } catch (Exception exception){
                log.error("Exception while syncing image to central services for merchant: {} and application: {}. shop document id is: {}. exception is:{}",
                        lendingShopDocument.getMerchantId(), lendingShopDocument.getApplicationId(), lendingShopDocument.getId(), exception.getStackTrace());
            }
            retry--;
        }
    }

    private String getPhotoDocType(String proofType) {
        if(ShopPhotoProofType.FRONT.getValue().equalsIgnoreCase(proofType)){
            return "SHOP_PICTURE_1";
        }
        if(ShopPhotoProofType.STOCK.getValue().equalsIgnoreCase(proofType)){
            return "SHOP_PICTURE_2";
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

    public KycDocResponseDTO getKycDocs(Long merchantId, Date validAfterDate, String provider, String docs, Boolean acceptRejected, Boolean acceptDraft, String convertedKycRanking) {
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
            Map<String, Object> requestParams = createPayLoad(merchantId, finaValidAfter, provider, docs, acceptRejected, acceptDraft, convertedKycRanking);
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, String>> request  = new HttpEntity<>(headers);
            final String url = getKycUrl(merchantId, docs, acceptRejected, validAfter, provider, acceptDraft, convertedKycRanking);
            log.info("Get Kyc docs API url : {} and request : {} for merchant:{}", url, request, merchantId);
            ResponseEntity<KycDocResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, KycDocResponse.class);
            log.info("Get KYC docs response : {} for merchant:{}", responseEntity.getBody(), merchantId);
            if (Objects.nonNull(responseEntity.getBody()) && responseEntity.getBody().isStatus() && responseEntity.getBody().getData() != null) {
                kycDocResponseDTO.setKycDocs(responseEntity.getBody().getData().getDocs());
                if(Objects.nonNull(responseEntity.getBody().getData().getEntity())){
                    kycDocResponseDTO.setEntityStatus(responseEntity.getBody().getData().getEntity().getStatus());
                    kycDocResponseDTO.setKycRanking(responseEntity.getBody().getData().getEntity().getKycRanking());
                    kycDocResponseDTO.setStatusOfRequestedKycRanking(responseEntity.getBody().getData().getEntity().getStatusOfRequestedKycRanking());
                    kycDocResponseDTO.setActivatedViaNewObV3(responseEntity.getBody().getData().getEntity().isActivatedViaNewObV3());
                }
                return kycDocResponseDTO;
            }
        } catch (Exception ex) {
            log.error("Exception in getKycDoc for merchant:{}, {}, {}", merchantId, ex, Arrays.asList(ex.getStackTrace()));
        }
        return null;
    }

    private String getKycUrl(Long merchantId, String docs, Boolean acceptRejected, String validAfter, String provider, Boolean acceptDraft, String convertedKycRanking) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(env.getProperty("kyc.service.base.url"))
                .append(LendingConstants.KYC_DOC_URL)
                .append("?merchantId=").append(merchantId)
                .append("&docs=").append(docs)
                .append("&imgRequire=true")
                .append("&acceptRejected=").append(acceptRejected)
                .append("&validAfter=").append(validAfter)
                .append("&provider=").append(provider)
                .append("&acceptDraft=").append(acceptDraft);
        if (p2pmEnabled && !ObjectUtils.isEmpty(convertedKycRanking)) {
            urlBuilder.append("&kycRankingRequired=").append(convertedKycRanking);
        }
        return urlBuilder.toString();
    }

    private Map<String, Object> createPayLoad(Long merchantId, String finaValidAfter, String provider, String docs, Boolean acceptRejected, Boolean acceptDraft, String convertedKycRanking) {
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("merchantId", merchantId);
        requestParams.put("validAfter", finaValidAfter);
        requestParams.put("provider", provider);
        requestParams.put("docs", docs);
        requestParams.put("imgRequire", true);
        requestParams.put("acceptRejected", acceptRejected);
        requestParams.put("acceptDraft", acceptDraft);
        if(p2pmEnabled && !ObjectUtils.isEmpty(convertedKycRanking)) {
            log.info("fetching status of requestedKycRanking: {} for merchant: {}", convertedKycRanking, merchantId);
            requestParams.put("kycRankingRequired", convertedKycRanking);
        }
        return requestParams;
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
            List<KycDoc> kycDocs = getKycDoc(merchantId, false, true, "PAN_NO,SELFIE,POA");
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

    public Map<String,String> initiateKyc(Long merchantId, InitiateKycDTO initiateKycDTO, List<KycDocType> docTypes, Date validAfterDate, Boolean onlySelfieLivelinessRequired, String convertedkycRanking) {
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
            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("callBackUrl", initiateKycDTO.getCallBackUrl());
            requestParams.put("product", "LOAN");
            requestParams.put("referenceId", initiateKycDTO.getReferenceId());
            requestParams.put("panNumber", initiateKycDTO.getPanNumber());
            requestParams.put("merchantId", initiateKycDTO.getMerchantId());
            requestParams.put("documents", documents);
            requestParams.put("validAfter", finaValidAfter);
            requestParams.put("onlySelfieLivelinessRequired", onlySelfieLivelinessRequired);

            if(easyLoanUtil.percentScaleUp(merchantId, skipScreenRolloutForMerchants)) {
                log.info("convertedkycRanking for merchant_id : {} is : {}", merchantId, convertedkycRanking);
                if(convertedkycRanking == null ||
                        (convertedkycRanking.equalsIgnoreCase("P2MM") ||
                                convertedkycRanking.equalsIgnoreCase("P2PM") ||
                                convertedkycRanking.equalsIgnoreCase("P2MS")))
                {
                    List<String> skipScreens = Collections.singletonList("aadhaar");
                    requestParams.put("skipScreens", skipScreens);
                }
            }

            // Conditionally add "kycRankingRequired"
            if (p2pmEnabled && !ObjectUtils.isEmpty(convertedkycRanking)) {
                log.info("passing kycRankingRequired as {} for merchant: {}", convertedkycRanking, merchantId);
                requestParams.put("kycRankingRequired", convertedkycRanking);
            }
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
//            log.info("PanNumber: {}, name: {} & dob: {} of merchantId : {}",panNumber, name, dob, merchantId);
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

    public PanVerifyKYCResponseDto verifyPanDetailsInternal(String panNumber, String name, String dob, Long merchantId) {
        if (ObjectUtils.isEmpty(panNumber) || ObjectUtils.isEmpty(name) || ObjectUtils.isEmpty(dob)) {
            return null;
        }
        try {
            String url = env.getProperty("kyc.service.base.url") + LendingConstants.PAN_VERIFY_V3_INTERNAL;

            Map<String, Object> payload = new HashMap<>();
            payload.put("panNumber", panNumber);
            payload.put("dob", dob);
            payload.put("name", name);
            payload.put("userType", "MERCHANT");
            payload.put("merchantId", merchantId);
            payload.put("source", "LOAN");

            HttpHeaders headers = getApiHeaders(payload);
            headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            log.info("Pan Verify Internal request for merchantId: {}, request: {} url: {}", merchantId, mapper.writeValueAsString(request), url);
            ResponseEntity<PanVerifyKYCResponseDto> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, PanVerifyKYCResponseDto.class);
            if(Objects.isNull(responseEntity.getBody())){
                return null;
            }
            log.info("Pan Verify Internal Response {} for merchantId: {}", mapper.writeValueAsString(responseEntity.getBody()),merchantId);
            if(responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.hasBody()) {
                return responseEntity.getBody();
            }
        }catch (HttpClientErrorException.TooManyRequests exception) {
            log.error("Too Many Requests error while verifying pan details for merchantId:{} {}",merchantId, Arrays.asList(exception.getStackTrace()));
        }catch (Exception e) {
            log.error("Error occurred while verifying pan details for merchantId {} {}", merchantId,Arrays.asList(e.getStackTrace()));
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

    public List<KycDoc> getBusinessDocs(Long merchantId) {
        log.info("Fetching docs for merchantId: {}", merchantId);
        try {
            Map<String, Object> requestParams = new HashMap<String, Object>(){{
                put("merchantId", merchantId);
                put("docs", "BUSINESSDOCS");
                put("imgRequire", true);
                put("acceptRejected", false);
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, String>> request  = new HttpEntity<>(headers);
            final String url = env.getProperty("kyc.service.base.url") + LendingConstants.KYC_DOC_URL + "?merchantId=" + merchantId + "&docs=BUSINESSDOCS&acceptRejected=false&imgRequire=true&returnMultipleSubDocTypes=true";
            log.info("Get Doc request for merchantId: {}, request: {} url: {}", merchantId, mapper.writeValueAsString(request), url);
            ResponseEntity<KycDocResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, KycDocResponse.class);
            log.info("Get Doc Response for merchantId: {}, {}", mapper.writeValueAsString(responseEntity.getBody()) ,merchantId);
            if (Objects.nonNull(responseEntity.getBody()) && responseEntity.getBody().isStatus() && responseEntity.getBody().getData() != null) {
                return responseEntity.getBody().getData().getDocs();
            }
        }catch (Exception e) {
            log.error("Error occurred while getting docs for merchantId:{}", merchantId, e);
        }
        return null;
    }


    public Map<String,String> initiateKycForBusinessDoc(Long merchantId, InitiateKycBLDocUploadDTO initiateKycDTO, List<String> docTypes) {
        log.info("Initiate kyc for merchant:{}", merchantId);
        Map<String, String> responseObj = new HashMap<>();
        try {
            List<Map<String, String>> documents = new ArrayList<>();
            for (String docType : docTypes) {
                documents.add(new HashMap<String, String>(){{
                    put("docType", "BUSINESSDOCS");
                    put("subDocType", docType);
                }});
            }

            Map<String, Object> businessDocuments = new HashMap<>();
            businessDocuments.put("docs", documents);
            businessDocuments.put("businessDocsSkip", true);

            Map<String, Object> requestBody = new HashMap<String, Object>(){{
                put("product", "LOAN");
                put("merchantId", merchantId);
                put("callBackUrl", initiateKycDTO.getCallBackUrl());
                put("action", "BL_TAGGING");
                put("documents", new ArrayList<>());
                put("docUploadCountRequiredByProduct", initiateKycDTO.getDocUploadCountRequiredByProduct());
                put("businessDocuments", businessDocuments);
                put("referenceId", initiateKycDTO.getReferenceId());
            }};
            HttpHeaders headers = getApiHeaders(requestBody);
            HttpEntity<Map<String, Object>> request  = new HttpEntity<>(requestBody, headers);
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
                    if (jsonNode.has("businessDocsUploadUrl")) {
                        responseObj.put("businessDocsUploadUrl", jsonNode.get("businessDocsUploadUrl").asText());
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

}
