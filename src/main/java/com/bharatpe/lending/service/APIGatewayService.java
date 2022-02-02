package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.common.utils.NotificationUtil;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.FpWithdrawStatusCheckDTO;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.constant.*;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LoanAgreementDao;
import com.bharatpe.lending.dao.TokenVerificationDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.LoanAgreement;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.dto.AddressDetails;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class APIGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(APIGatewayService.class);

    @Value("${internal.merchant.id}")
    long merchantId;

    @Value("${monget.api.dump}")
    String MONGET_API;

    @Value("${pg.url}")
    String PG_URL;

    @Autowired
    HmacCalculator hmacCalculator;

    @Autowired
    AesEncryption aesEncryption;

    @Autowired
    MerchantDao merchantDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanAgreementDao loanAgreementDao;

    @Autowired
    LendingPancardDao lendingPancardDao;

    @Autowired
    PincodeCityStateMappingDao pincodeCityStateMappingDao;

    @Autowired
    LendingRedCitiesDao lendingRedCitiesDao;

    @Autowired
    LendingCitiesDao lendingCitiesDao;

    @Autowired
    SignzyCredentialDao signzyCredentialDao;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    DocKycDetailsDao docKycDetailsDao;

    @Value("${signzy.url}")
    public String SIGNZY_URL;

    @Value("${club.url}")
    public String BHARATPE_CLUB_URL;

    private static String SECRET;
    private static String MID;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    LdcVirtualAccountDao ldcVirtualAccountDao;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    SignzyRequestResponseDao signzyRequestResponseDao;

    @Autowired
    Environment env;

    @Autowired
    CrifRequestResponseDao crifRequestResponseDao;

    @Autowired
    InternalClientDao internalClientDao;

    @Autowired
    LendingVirtualAccountDao lendingVirtualAccountDao;

    @Autowired
    ExperianService experianService;

    @Autowired
    BharatPeEnachDao bharatPeEnachDao;

    @Autowired
    TokenVerificationDao tokenVerificationDao;

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    LendingPennydropDao lendingPennydropDao;

    @Autowired
    LendingNotificationService lendingNotificationService;

    @Autowired
    NotificationUtil notificationUtil;

    @Autowired
    LendingPincodesDao lendingPincodesDao;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    LendingEkycDao lendingEkycDao;

    @Autowired
    @Qualifier("customRestTemplate")
    RestTemplate customRestTemplate;

    private final String CLIENT = "LENDING";

    private final String NBFC_URL = "https://api-nbfc.bharatpe.in/api/v1/loan";

    private static String clientSecret;

    @PostConstruct
    public void init() {
        try {
            getSecret();
            getMid();
        } catch (Exception ex) {
            logger.error("Exception while loading Secret in APIGatewayService", ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map createVPA(Merchant merchant, Double amount, String orderId, String vpa) {
        logger.info("In Create VPA of APIGatewayService for merchnat id {}", merchant.getId());
        try {
            Map requestParams = new HashMap<>();
            requestParams.put("amount", amount);
            requestParams.put("orderId", orderId);
            requestParams.put("mid", getMid());
            if (vpa != null) {
                requestParams.put("payerVpa", vpa);
            }
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), getSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("hash", hash);
            headers.set("mid", getMid());
            HttpEntity<Map> request = new HttpEntity<>(requestParams, headers);

            logger.info("createVPA internal request: {}", mapper.writeValueAsString(request));
            int retryCount = 0;
            while (retryCount < 3) {
                try {
                    Map response = restTemplate.postForObject(env.getProperty("collect.vpa.endpoint") + LendingConstants.COLLECT_VPA_CREATE_TXN_URL, request, Map.class);
                    logger.info("Response received from create VPA API {}", mapper.writeValueAsString(response));
                    return response;
                } catch (Exception e) {
                    logger.error("Exception in createVPA", e);
                }
                retryCount++;
            }
        } catch (HttpClientErrorException ex) {
            logger.info("Response from API GAteway : {}", ex.getResponseBodyAsString());
            logger.error("Error in api call to generate dynamic vpa for merchant:{}", merchant.getId(), ex);
        } catch (Exception ex) {
            logger.error("error processing txn for dynamic vpa for merchant id:{}", merchant.getId(), ex);
        }
        return null;
    }

    public PgCreateTransactionResponseDTO createPgTransaction(Long merchantId, PgCreateTransactionRequestDTO pgCreateTransactionRequestDTO) {
        PgCreateTransactionResponseDTO pgCreateTransactionResponseDTO = new PgCreateTransactionResponseDTO();
        logger.info("In Create pg transaction for merchnat id {}", merchantId);
        InternalClient internalClient = internalClientDao.findByClientName(CLIENT);
        try {
            Map requestParams = new HashMap<>();
            requestParams.put("orderId", pgCreateTransactionRequestDTO.getOrderId());
            requestParams.put("orderAmount", pgCreateTransactionRequestDTO.getOrderAmount());
            requestParams.put("redirectURI", pgCreateTransactionRequestDTO.getRedirectURI());
            requestParams.put("redirectURIDeeplink", pgCreateTransactionRequestDTO.getRedirectURIDeeplink());
            requestParams.put("paymentPageHeaderText", pgCreateTransactionRequestDTO.getPaymentPageHeaderText());
            requestParams.put("narration", pgCreateTransactionRequestDTO.getNarration());
            requestParams.put("allowedModes", pgCreateTransactionRequestDTO.getAllowedModes());

            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), getSecret());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("hash", hash);
            headers.set("mid", getMid());
            HttpEntity<Map> request = new HttpEntity<>(requestParams, headers);

            logger.info("Create pg transaction internal request: {}", mapper.writeValueAsString(request));
            int retryCount = 0;
            while (retryCount < 3) {
                try {
                    PgCreateTransactionResponseDTO response = restTemplate.postForObject(PG_URL + LendingConstants.CREATE_PG_TXN, request, PgCreateTransactionResponseDTO.class);
                    logger.info("Response received from Create pg transaction API {}", mapper.writeValueAsString(response));
                    return response;
                } catch (Exception e) {
                    logger.error("Exception in createVPA", e);
                }
                retryCount++;
            }
        } catch (HttpClientErrorException ex) {
            logger.info("Response from API GAteway : {}", ex.getResponseBodyAsString());
            logger.error("Error in api call to create pg transaction for merchant:{}", merchantId, ex);
        } catch (Exception ex) {
            logger.error("error processing txn for dynamic vpa for merchant id:{}", merchantId, ex);
        }
        return null;
    }

    private String getSecret() {
        Optional<Merchant> merchantOptional = merchantDao.findById(merchantId);
        if (merchantOptional.isPresent()) {
            Merchant merchant = merchantOptional.get();
            if (SECRET == null) {
                SECRET = aesEncryption.decrypt(merchant.getSecret());
            }
        }
        return SECRET;
    }

    private String getMid() {
        Optional<Merchant> merchantOptional = merchantDao.findById(merchantId);
        if (merchantOptional.isPresent()) {
            Merchant merchant = merchantOptional.get();
            if (MID == null) {
                MID = merchant.getMid();
            }
        }
        return MID;
    }

    public Map<String, String> signzyIdentityDetails(String proofType, Long merchantId, String signzyModule, String signzyPurpose, List<String> images) {
        logger.info("Calling Signzy Identity flow Api for proof:{}", proofType);
        try {
            SignzyCredential signzyCredential = signzyCredentialDao.findByModuleAndPurpose(signzyModule, signzyPurpose);
            if (signzyCredential == null) {
                logger.info("signzy credentials not found");
                return null;
            }
            Map<String, Object> body = new HashMap<>();
            body.put("type", proofType);
            body.put("email", "admin@signzy.com");
            body.put("callbackUrl", "https://your-domain.com/your-callback-system");
            body.put("images", images);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", signzyCredential.getAccessId());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String URL = SIGNZY_URL + CreditConstants.SIGNZY_IDENTITY_URL + "/" + signzyCredential.getUserId() + "/identities";
            logger.info("Request body to get signzy identity credentials URL {} request {}", URL, request);
            String response = null;
            int retryCount = 0;
            while (retryCount < 3) {
                try {
                    response = restTemplate.postForObject(URL, request, String.class);
                    logger.info("Signzy identity response {}", response);
                    if (response != null) {
                        JsonNode jsonNode = mapper.readTree(response);
                        if (jsonNode != null && jsonNode.has("id") && jsonNode.has("accessToken")) {
                            insertIntoSignzyReqRes(merchantId, null, "IDENTITY", "SUCCESS", mapper.writeValueAsString(request), response, signzyCredential.getModule());
                            Map<String, String> identityDetail = new HashMap<>();
                            identityDetail.put("itemId", jsonNode.get("id").asText());
                            identityDetail.put("accessToken", jsonNode.get("accessToken").asText());
                            identityDetail.put("module", signzyCredential.getModule());
                            return identityDetail;
                        } else {
                            insertIntoSignzyReqRes(merchantId, null, "IDENTITY", "FAILED", mapper.writeValueAsString(request), response, signzyCredential.getModule());
                        }
                    }
                    break;
                } catch (Exception e) {
                    logger.info("Error occurred while fetching identity details", e);
                    insertIntoSignzyReqRes(merchantId, null, "IDENTITY", "FAILED", mapper.writeValueAsString(request), response, signzyCredential.getModule());
                }
                retryCount++;
            }
        } catch (Exception e) {
            logger.error("Exception in Signzy Identity flow Api", e);
        }
        return null;
    }

    public String signzyPanFetch(String itemId, String accessToken, String pancard, Long merchantId, String module) {
        logger.info("Calling Signzy Pan Fetch Api for pancard:{}", pancard);
        try {
            Map<String, Object> body = new HashMap<String, Object>() {{
                put("service", "Identity");
                put("itemId", itemId);
                put("task", "verification");
                put("accessToken", accessToken);
                put("essentials", new HashMap<String, String>() {{
                    put("number", pancard);
                    put("name", "test");
                }});
            }};
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String URL = SIGNZY_URL + CreditConstants.SIGNZY_SNOOP_URL;
            logger.info("Signzy Pan Fetch URL {} request {}", URL, request);
            int retryCount = 0;
            String response = null;
            while (retryCount < 3) {
                try {
                    ResponseEntity<String> responseEntity = restTemplate.exchange(URL, HttpMethod.POST, request, String.class);
                    logger.info("Response for Signzy Pan Fetch: {}", responseEntity);
                    if (responseEntity.getBody() != null) {
                        response = responseEntity.getBody();
                        if (responseEntity.getStatusCode().is2xxSuccessful()) {
                            insertIntoSignzyReqRes(merchantId, null, "PAN_FETCH", "SUCCESS", mapper.writeValueAsString(request), responseEntity.getBody(), module);
                        } else {
                            insertIntoSignzyReqRes(merchantId, null, "PAN_FETCH", "FAILED", mapper.writeValueAsString(request), response, module);
                        }
                    }
                    break;
                } catch (HttpStatusCodeException e) {
                    logger.info("Error occurred while calling pan fetch api", e);
                    insertIntoSignzyReqRes(merchantId, null, "PAN_FETCH", "FAILED", mapper.writeValueAsString(request), response, module);
                    if (e.getRawStatusCode() == 404 || e.getRawStatusCode() == 400) {
                        break;
                    } else if (retryCount == 2) {
                        //to allow merchant to proceed if there's error from Experian end;
                        response = "ERROR_OCCURRED";
                    }
                    retryCount++;
                } catch (Exception e) {
                    logger.info("Error occurred while calling pan fetch api", e);
                    insertIntoSignzyReqRes(merchantId, null, "PAN_FETCH", "FAILED", mapper.writeValueAsString(request), response, module);
                    if (retryCount == 2) {
                        //to allow merchant to proceed if there's error from Experian end;
                        response = "ERROR_OCCURRED";
                    }
                    retryCount++;
                }
            }
            return response;
        } catch (Exception e) {
            logger.error("Exception in Signzy Pan Fetch Api", e);
        }
        return null;
    }

    public JsonNode signzyPanFetchV2(String pancard, Long merchantId, String signzyModule, String signzyPurpose) {
        logger.info("Calling Signzy pan fetch v2 Api for merchant:{} and pancard:{}", merchantId, pancard);
        try {
            SignzyCredential signzyCredential = signzyCredentialDao.findByModuleAndPurpose(signzyModule, signzyPurpose);
            if (signzyCredential == null) {
                logger.info("signzy credentials not found");
                return null;
            }
            Map<String, Object> body = new HashMap<>();
            body.put("task", "fetch");
            body.put("essentials", new HashMap<String, String>() {{
                put("number", pancard);
            }});
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", signzyCredential.getAccessId());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String URL = SIGNZY_URL + CreditConstants.SIGNZY_IDENTITY_URL + "/" + signzyCredential.getUserId() + "/panv2";
            logger.info("Signzy pan fetch v2 url:{} and request:{} for merchant:{}", URL, request, merchantId);
            try {
                Long a = DateTime.now().getMillis();
                ResponseEntity<String> response = restTemplate.exchange(URL, HttpMethod.POST, request, String.class);
                Long b = DateTime.now().getMillis();
                logger.info("Signzy API response time---{}ms", (b - a));
                logger.info("Signzy pan fetch v2 response:{} for merchant:{}", response, merchantId);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    insertIntoSignzyReqRes(merchantId, null, "PAN_FETCH_V2", "SUCCESS", mapper.writeValueAsString(request), response.getBody(), signzyModule);
                    return mapper.readTree(response.getBody());
                } else {
                    insertIntoSignzyReqRes(merchantId, null, "PAN_FETCH_V2", "FAILED", mapper.writeValueAsString(request), response.getBody(), signzyModule);
                }
            } catch (Exception e) {
                logger.info("Error occurred while calling signzy pan fetch v2 for merchant:{}", merchantId, e);
                insertIntoSignzyReqRes(merchantId, null, "PAN_FETCH_V2", "FAILED", mapper.writeValueAsString(request), null, signzyModule);
            }
        } catch (Exception e) {
            logger.error("Exception in Signzy pan fetch v2 for merchant:{}", merchantId, e);
        }
        return null;
    }

    public String getOcrResponse(Long merchantId, Map<String, String> identityDetails, String ocrType, Long applicationId) {
        String response = null;
        String itemId = identityDetails.get("itemId");
        String accessToken = identityDetails.get("accessToken");
        if (itemId != null && accessToken != null && !itemId.isEmpty() && !accessToken.isEmpty()) {
            Map<String, Object> body = new HashMap<String, Object>() {{
                put("service", "Identity");
                put("itemId", itemId);
                put("task", "autoRecognition");
                put("accessToken", accessToken);
                put("essentials", new HashMap<>());
            }};
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
//	        headers.set("Authorization", authorization);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String URL = SIGNZY_URL + CreditConstants.SIGNZY_SNOOP_URL;
            logger.info("Signzy OCR URL {} request {}", URL, request);
            int retryCount = 0;
            while (retryCount < 3) {
                try {
                    response = restTemplate.postForObject(URL, request, String.class);
                    logger.info("Response for pancard authentication {}", response);
                    insertIntoSignzyReqRes(merchantId, applicationId, ocrType, "SUCCESS", mapper.writeValueAsString(request), response, "LENDING");

                    break;
                } catch (Exception e) {
                    logger.error("Error occured while doing ocr", e);
                    try {
                        insertIntoSignzyReqRes(merchantId, applicationId, ocrType, "FAILED", mapper.writeValueAsString(request), response, "LENDING");
                    } catch (JsonProcessingException e1) {
                        e1.printStackTrace();
                    }
                    retryCount++;
                }
            }
        }
        return response;
    }

    public void insertIntoSignzyReqRes(Long merchantId, Long applicationId, String apiName, String status, String request, String response, String module) {
        try {
            SignzyRequestResponse signzyRequestResponse = new SignzyRequestResponse(merchantId, applicationId, StringUtils.substring(apiName, 0, 20), status, request, response, module);
            signzyRequestResponseDao.save(signzyRequestResponse);
        } catch (Exception e) {
            logger.error("Error occured while inserting into signzy req res table ", e);
        }
    }

    public JsonNode crifStage1(String firstName, String lastName, String pancard, String mobile, Long merchantId) {
        try {
            logger.info("Calling CRIF stage1 api for merchant:{} with pancard:{}", merchantId, pancard);
            String accessCode = generateAccessCode();
            String orderId = LoanUtil.getRandomNumberString() + merchantId;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set("orderId", orderId);
            headers.set("accessCode", accessCode);
            headers.set("appID", env.getProperty("crif.appId"));
            headers.set("merchantID", env.getProperty("crif.customerId"));
            String body = firstName + "||" + lastName + "|||||" + mobile + "|||||" + pancard + "|||||||||||||||||||||||" + env.getProperty("crif.customerId") + "|BBC_CONSUMER_SCORE#85#2.0|Y|";
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            logger.info("CRIF stage1 request:{}", request);
            CrifRequestResponse crifRequestResponse = new CrifRequestResponse(merchantId, firstName, lastName, pancard, mobile, orderId, accessCode, null, "STAGE1", null, mapper.writeValueAsString(request), null);
            crifRequestResponseDao.save(crifRequestResponse);
            int retryCount = 0;
            ResponseEntity<String> response = null;
            while (retryCount < 2) {
                try {
                    response = restTemplate.exchange(Objects.requireNonNull(env.getProperty("crif.stage1.url")), HttpMethod.POST, request, String.class);
                    logger.info("CRIF stage1 response:{}", response);
                    break;
                } catch (ResourceAccessException e) {
                    logger.info("Crif Stage1 api timeout for merchant:{}", merchantId, e);
                }
                retryCount++;
            }
            if (response != null && response.getStatusCode().equals(HttpStatus.OK) && response.getBody() != null) {
                JsonNode jsonNode = mapper.readTree(response.getBody());
                crifRequestResponse.setReportId(jsonNode.get("reportId") != null ? jsonNode.get("reportId").asText() : null);
                crifRequestResponse.setResponse(response.getBody());
                crifRequestResponse.setStatus("SUCCESS");
                crifRequestResponseDao.save(crifRequestResponse);
                return jsonNode;
            } else {
                crifRequestResponse.setStatus("FAILED");
                crifRequestResponseDao.save(crifRequestResponse);
                return null;
            }
        } catch (Exception e) {
            logger.error("Exception in crif stage1 api", e);
            return null;
        }
    }

    public JsonNode crifStage2(Long merchantId, String orderId, String reportId, String redirectUrl, boolean stage3, String userAns) {
        try {
            String stage = stage3 ? "stage3" : "stage2";
            logger.info("Calling CRIF " + stage + " api for merchant:{} with orderId:{}", merchantId, orderId);
            String accessCode = generateAccessCode();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            if (!stage3) {
                headers.set("requestType", "Authorization");
            }
            headers.set("accessCode", accessCode);
            headers.set("appID", env.getProperty("crif.appId"));
            headers.set("merchantID", env.getProperty("crif.customerId"));
            headers.set("orderId", orderId);
            headers.set("reportId", reportId);
            String body = orderId + "|" + reportId + "|" + accessCode + "|" + "https://cir.crifhighmark.com/Inquiry/B2B/secureService.action" + "|N|N|N|" + userAns;
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            logger.info("CRIF " + stage + " request:{}", request);
            CrifRequestResponse crifRequestResponse = new CrifRequestResponse(merchantId, null, null, null, null, orderId, accessCode, reportId, stage3 ? "STAGE3" : "STAGE2", null, mapper.writeValueAsString(request), null);
            crifRequestResponseDao.save(crifRequestResponse);
            int retryCount = 0;
            ResponseEntity<String> response = null;
            while (retryCount < 2) {
                try {
                    response = restTemplate.exchange(Objects.requireNonNull(env.getProperty("crif.stage2.url")), HttpMethod.POST, request, String.class);
                    logger.info("CRIF " + stage + " response:{}", response.getBody());
                    break;
                } catch (ResourceAccessException e) {
                    logger.info("CRIF " + stage + " api timeout for merchant:{}", merchantId, e);
                }
                retryCount++;
            }
            if (response != null && response.getStatusCode().equals(HttpStatus.OK) && response.getBody() != null) {
                crifRequestResponse.setStatus("SUCCESS");
                crifRequestResponse.setResponse(response.getBody());
                crifRequestResponseDao.save(crifRequestResponse);
                if (stage3) {
                    JSONObject jsonObject = XML.toJSONObject(response.getBody());
                    return mapper.readTree(jsonObject.toString());
                }
                return mapper.readTree(response.getBody());
            } else {
                crifRequestResponse.setStatus("FAILED");
                crifRequestResponseDao.save(crifRequestResponse);
                return null;
            }
        } catch (Exception e) {
            logger.error("Exception in crif stage2 api", e);
            return null;
        }
    }

    private String generateAccessCode() {
        String value = env.getProperty("crif.userId") + "|" + env.getProperty("crif.customerId") + "|BBC_CONSUMER_SCORE#85#2.0|" + env.getProperty("crif.password") + "|" + new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
        return Base64.getEncoder().encodeToString(value.getBytes());
    }

    public List<PaymentDetailDto> getPaymentModes(RequestDTO<CreditSpendRequestDTO> requestDTO, String token) {
        List<PaymentDetailDto> paymentDetails = new ArrayList<>();
        try {
            UriComponents requestUrl = UriComponentsBuilder.fromHttpUrl(env.getProperty("payment.service.host") + CreditConstants.PAYMENT_MODE_URL).build();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.set("token", token);
            headers.set("clientName", CLIENT);
            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("common_params", requestDTO.getMeta());
            requestParams.put("params", requestDTO.getSimInfo());
            HttpEntity<Object> entity = new HttpEntity<>(requestParams, headers);
            logger.info("Get payment modes request:{}", entity);
            ResponseEntity<Object> response = null;
            int retry = 0;
            while (retry < 3) {
                try {
                    response = restTemplate.exchange(requestUrl.encode().toUri(), HttpMethod.POST, entity, Object.class);
                    if (response.getBody() != null) {
                        break;
                    }
                } catch (Exception e) {
                    logger.error("Error occured while fetching payment mode", e);
                }
                retry++;
            }
            logger.info("Response : {} ", response);
            if (response != null && response.getBody() != null) {
                paymentDetails = mapper.readValue(mapper.writeValueAsString(((Map<String, Object>) response.getBody()).get("data")), new TypeReference<List<PaymentDetailDto>>() {
                });
            }
        } catch (HttpClientErrorException e) {
            logger.error("Error fetching Balance info", e);
        } catch (Exception e) {
            logger.error("Error parsing details from payment details", e);
        } finally {
            if (paymentDetails.isEmpty()) {
                logger.info("No Payment Mode Received, Falling Back to UPI Payment Mode");
                paymentDetails.addAll(fetchDefaultModes());
            }
        }
        return paymentDetails;
    }

    private List<PaymentDetailDto> fetchDefaultModes() {
        List<PaymentDetailDto> paymentDetails = new ArrayList<>();
        try {
            String content = "[\n" + "{\n" + "\"name\": \"Pay Using UPI\",\n"
                    + "                       \"type\": \"UPI\",\n"
                    + "                       \"fund_source\": \"UPI\",\n"
                    + "                       \"balance\": null,\n"
                    + "                       \"amount_limit\": 100000.0,\n"
                    + "                       \"description\": null,\n"
                    + "                       \"offers\": null,\n"
                    + "                       \"auth_type\": null,\n"
                    + "                       \"psps\": [\n"
                    + "                \"com.google.android.apps.nbu.paisa.user\",\n" + "\"net.one97.paytm\",\n" + "\"in.org.npci.upiapp\",\n"
                    + "                \"com.csam.icici.bank.imobile\",\n" + "\"com.mobikwik_new\",\n"
                    + "                \"com.myairtelapp\",\n" + "\"com.olacabs.customer\"\n" + "],\n"
                    + "                     \"auth_required\": false,\n"
                    + "                     \"default\": false,\n"
                    + "                     \"enable\": true,\n"
                    + "                     \"initiate_sb\": false,\n"
                    + "                     \"sb_link\": null\n" + "}\n" + "]";
            paymentDetails = mapper.readValue(content, new TypeReference<List<PaymentDetailDto>>() {
            });
        } catch (Exception e) {
            logger.error("Error Parsing payment Modes : {}", e.getMessage());
        }
        return paymentDetails;
    }

    public Map<String, Object> initiateTxn(MetaDTO meta, SimInfo simInfo, Double amount, String appHash, String orderId, String token, String beneficiaryName, String paymentSource) {
        Map<String, Object> result = new HashMap<>();
        InternalClient internalClient = internalClientDao.findByClientName(CLIENT);
        try {
            Map<String, Object> requestParams = generateBPBRequest(meta, simInfo, amount, appHash, orderId, beneficiaryName, paymentSource);
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getObjectPayload(requestParams), aesEncryption.decrypt(internalClient.getSecret()));
            UriComponents requestUrl = UriComponentsBuilder.fromHttpUrl(env.getProperty("payment.service.host") + CreditConstants.BP_BALANCE_CREATE_TXN_URL).build();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("token", token);
            headers.set("hash", hash);
            headers.set("clientName", CLIENT);
            HttpEntity<Object> entity = new HttpEntity<>(requestParams, headers);
            result.put("request", mapper.writeValueAsString(entity));
            long startTime = System.currentTimeMillis();
            int retryCount = 0;
            while (retryCount < 3) {
                try {
                    ResponseEntity<Object> response = restTemplate.exchange(requestUrl.encode().toUri(), HttpMethod.POST, entity, Object.class);
                    if (response.getBody() != null) {
                        result.put("response", mapper.writeValueAsString(response.getBody()));
                        logger.info("Response : {} ", mapper.writeValueAsString(response.getBody()));
                        result.put("success", ((Map<String, Object>) response.getBody()).get("success"));
                        Map<String, Object> responseData = (Map<String, Object>) ((Map<String, Object>) response.getBody()).get("data");
                        if (responseData != null) {
                            result.put("otp_flow", responseData.get("otp_flow"));
                            result.put("auth_mode", responseData.get("auth_mode"));
                            result.put("bp_txn_id", responseData.get("bp_txn_id"));
                        }
                        logger.info("Successfully created txn for BP Balance in {} ms", System.currentTimeMillis() - startTime);
                        return result;
                    }
                } catch (Exception e) {
                    logger.error("Error Starting txn for BP Balance info---", e);
                }
                retryCount++;
            }
        } catch (HttpClientErrorException e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error Starting txn for BP Balance info---", e);
        } catch (Exception e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error parsing details from BP Balance---", e);
        }
        return result;
    }

    private Map<String, Object> generateBPBRequest(MetaDTO meta, SimInfo simInfo, Double amount, String appHash, String orderId, String beneficiaryName, String paymentSource) {
        Map<String, Object> requestParams = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> commonParams = new HashMap<>();
        Map<String, Object> deviceInfo = new HashMap<>();
        List<Map<String, Object>> sims = new ArrayList<>();
        for (SimInfo.Sim sim : simInfo.getSims()) {
            Map<String, Object> map = new HashMap<>();
            map.put("slot", sim.getSlot());
            map.put("sim_id", sim.getSimId());
            map.put("carrier_name", sim.getCarrierName());
            map.put("phone", sim.getPhone());
            sims.add(map);
        }
        commonParams.put("app_version", meta.getAppVersion());
        commonParams.put("client", meta.getClient());
        commonParams.put("lat", meta.getLatitude());
        commonParams.put("lon", meta.getLongitude());
        commonParams.put("ip", meta.getIp());
        commonParams.put("device_id", meta.getDeviceId());
        deviceInfo.put("os", meta.getDeviceInfo().getOs());
        deviceInfo.put("manufacturer", meta.getDeviceInfo().getManufacturer());
        deviceInfo.put("device", meta.getDeviceInfo().getDevice());
        deviceInfo.put("is_virtual", meta.getDeviceInfo().getIsVirtual());
        commonParams.put("device_info", deviceInfo);
        params.put("amount", amount);
        params.put("order_id", orderId);
        params.put("beneficiary_name", beneficiaryName);
        params.put("source", paymentSource);
        if (appHash != null) {
            params.put("app_hash", appHash);
        }
        params.put("install_id", simInfo.getInstallId());
        params.put("device_id", simInfo.getDeviceId());
        params.put("sims", sims);
        requestParams.put("common_params", commonParams);
        requestParams.put("params", params);
        return requestParams;
    }

    public LendingVirtualAccount createLendingVAN(Long merchantId, Long loanId) {
        LendingVirtualAccount lendingVirtualAccount = lendingVirtualAccountDao.findByMerchantIdAndLoanId(merchantId, loanId);
        if (lendingVirtualAccount != null) {
            return lendingVirtualAccount;
        }
        logger.info("Creating virtual account for merchant:{}", merchantId);
        try {
            Map<String, String> requestParams = new HashMap<>();
            requestParams.put("type", "LOAN_PREPAYMENT");
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), getSecret());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("hash", hash);
            headers.set("mid", getMid());
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestParams, headers);
            logger.info("create virtual account request: {}", mapper.writeValueAsString(request));
            int retryCount = 0;
            VANResponseDTO response = null;
            while (retryCount < 3) {
                try {
                    response = restTemplate.postForObject(Objects.requireNonNull(env.getProperty("create.van.url")), request, VANResponseDTO.class);
                    logger.info("Response received from create VAN API {}", response);
                    break;
                } catch (Exception e) {
                    logger.error("Exception in createVPA", e);
                }
                retryCount++;
            }
            if (response != null) {
                if (response.getStatus() != null && "OK".equalsIgnoreCase(response.getStatus())) {
                    return lendingVirtualAccountDao.save(new LendingVirtualAccount(merchantId, loanId, response.getAccountNumber(), response.getIfsc()));
                }
            }
        } catch (Exception ex) {
            logger.error("Exception in create virtual account", ex);
        }
        return null;
    }

    public Map<String, Object> sendOTP(RequestDTO<PaymentResendOTP> requestDTO, String token) {
        Map<String, Object> result = new HashMap<>();
        InternalClient internalClient = internalClientDao.findByClientName(CLIENT);
        try {
            Map<String, Object> requestParams = generateSendMoneyVerify(requestDTO);
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getObjectPayload(requestParams), aesEncryption.decrypt(internalClient.getSecret()));
            UriComponents requestUrl = UriComponentsBuilder.fromHttpUrl(env.getProperty("payment.service.host") + CreditConstants.BP_BALANCE_RESEND_OTP_URL).build();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("token", token);
            headers.set("hash", hash);
            headers.set("clientName", CLIENT);

            HttpEntity<Object> entity = new HttpEntity<>(requestParams, headers);
            result.put("request", mapper.writeValueAsString(entity));
            long startTime = System.currentTimeMillis();
            int retryCount = 0;
            while (retryCount < 3) {
                try {
                    ResponseEntity<Object> response = restTemplate.exchange(requestUrl.encode().toUri(), HttpMethod.POST, entity, Object.class);
                    result.put("response", mapper.writeValueAsString(response.getBody()));
                    logger.info("Response : {} ", mapper.writeValueAsString(response.getBody()));
                    if (response.getBody() != null) {
                        result.put("success", ((Map<String, Object>) response.getBody()).get("success"));
                        logger.info("Successfully resend otp for BP Balance in {} ms", System.currentTimeMillis() - startTime);
                        return result;
                    }
                } catch (Exception e) {
                    logger.error("Error occured while sending otp", e);
                }
                retryCount++;
            }
        } catch (HttpClientErrorException e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error resend otp for BP Balance info---", e);
        } catch (Exception e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error parsing details from BP Balance---", e);
        }
        return result;
    }

    private Map<String, Object> generateSendMoneyVerify(RequestDTO<PaymentResendOTP> requestDTO) {
        Map<String, Object> requestParams = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> commonParams = new HashMap<>();
        Map<String, Object> deviceInfo = new HashMap<>();
        commonParams.put("app_version", requestDTO.getMeta().getAppVersion());
        commonParams.put("client", requestDTO.getMeta().getClient());
        commonParams.put("lat", requestDTO.getMeta().getLatitude());
        commonParams.put("lon", requestDTO.getMeta().getLongitude());
        commonParams.put("ip", requestDTO.getMeta().getIp());
        commonParams.put("device_id", requestDTO.getMeta().getDeviceId());
        deviceInfo.put("os", requestDTO.getMeta().getDeviceInfo().getOs());
        deviceInfo.put("manufacturer", requestDTO.getMeta().getDeviceInfo().getManufacturer());
        deviceInfo.put("device", requestDTO.getMeta().getDeviceInfo().getDevice());
        deviceInfo.put("is_virtual", requestDTO.getMeta().getDeviceInfo().getIsVirtual());
        commonParams.put("device_info", deviceInfo);
        if (requestDTO.getPayload().getOtp() != null) {
            params.put("otp", requestDTO.getPayload().getOtp());
        }
        if (requestDTO.getPayload().getAppHash() != null) {
            params.put("app_hash", requestDTO.getPayload().getAppHash());
        }
        params.put("order_id", requestDTO.getPayload().getOrderId());
        requestParams.put("common_params", commonParams);
        requestParams.put("params", params);
        return requestParams;
    }

    public Map<String, Object> verifyTxn(RequestDTO<PaymentResendOTP> requestDTO, String token) {
        Map<String, Object> result = new HashMap<>();
        InternalClient internalClient = internalClientDao.findByClientName(CLIENT);
        try {
            Map<String, Object> requestParams = generateSendMoneyVerify(requestDTO);
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getObjectPayload(requestParams), aesEncryption.decrypt(internalClient.getSecret()));
            UriComponents requestUrl = UriComponentsBuilder.fromHttpUrl(env.getProperty("payment.service.host") + CreditConstants.BP_BALANCE_CONFIRM_TXN_URL).build();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("token", token);
            headers.set("hash", hash);
            headers.set("clientName", CLIENT);

            HttpEntity<Object> entity = new HttpEntity<>(requestParams, headers);
            result.put("request", mapper.writeValueAsString(entity));
            long startTime = System.currentTimeMillis();
            int retryCount = 0;
            while (retryCount < 3) {
                try {
                    ResponseEntity<Object> response = restTemplate.exchange(requestUrl.encode().toUri(), HttpMethod.POST, entity, Object.class);
                    if (response.getBody() != null) {
                        result.put("response", mapper.writeValueAsString(response.getBody()));
                        logger.info("Response : {} ", mapper.writeValueAsString(response.getBody()));
                        result.put("success", ((Map<String, Object>) response.getBody()).get("success"));
                        Map<String, Object> responseData = (Map<String, Object>) ((Map<String, Object>) response.getBody()).get("data");
                        if (responseData != null) {
                            result.put("order_id", responseData.get("order_id"));
                            result.put("amount", responseData.get("amount"));
                            result.put("status", responseData.get("status"));
                        }
                        logger.info("Successfully verified txn for BP Balance in {} ms", System.currentTimeMillis() - startTime);
                        return result;
                    }
                } catch (Exception e) {
                    logger.error("Error occured while verifying txn", e);
                }
                retryCount++;
            }
        } catch (HttpClientErrorException e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error Verifying txn for BP Balance info---", e);
        } catch (Exception e) {
            result.put("success", Boolean.FALSE);
            logger.error("Error parsing details from BP Balance---", e);
        }
        return result;
    }

    public void signZyPanGst(Long merchantId) {
        logger.info("Calling Signzy For GST Number:{}", merchantId);
        try {
            SignzyCredential signzyCredential = signzyCredentialDao.findByModule("GST");
            if (signzyCredential == null) {
                logger.info("signzy credentials not found");
                return;
            }
            Experian experian = experianDao.getByMerchantId(merchantId);
            LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
            if (lendingPancard != null && lendingPancard.getGstNumber() != null && lendingPancard.getPancardNumber() != null && lendingPancard.getPancardNumber().equalsIgnoreCase(experian.getPancardNumber())) {
                logger.info("Already pulled gst for this pancard");
                return;
            }
            Integer pincode = experian.getPincode();
            PincodeCityStateMapping pincodeCityStateMapping = pincodeCityStateMappingDao.findByPincode(pincode);
            Map<String, Object> body = new HashMap<>();
            Map<String, Object> essentials = new HashMap<>();
            body.put("task", "panSearch");
            essentials.put("panNumber", experian.getPancardNumber());
            essentials.put("state", pincodeCityStateMapping != null ? pincodeCityStateMapping.getState() : "");
            essentials.put("email", "");
            body.put("essentials", essentials);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", signzyCredential.getAccessId());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String URL = SIGNZY_URL + CreditConstants.SIGNZY_IDENTITY_URL + "/" + signzyCredential.getUserId() + "/gstns";
            logger.info("Request body to get signzy identity credentials URL {} request {}", URL, request);
            String response = null;
            try {
                response = restTemplate.postForObject(URL, request, String.class);
                logger.info("Signzy Pan To Gst {}", response);
                insertIntoSignzyReqRes(merchantId, null, "GST", "SUCCESS", mapper.writeValueAsString(request), response, signzyCredential.getModule());
                if (response != null) {
                    JsonNode jsonNode = mapper.readTree(response);
                    if (jsonNode != null && jsonNode.get("result") != null && jsonNode.get("result").get("gstin") != null) {
                        if (lendingPancard == null) {
                            lendingPancard = new LendingPancard();
                            lendingPancard.setMerchantId(merchantId);
                        }
                        lendingPancard.setGstNumber(jsonNode.get("result").get("gstin").asText());
                        lendingPancardDao.save(lendingPancard);
                    }
                }
            } catch (Exception e) {
                logger.info("Error occurred while fetching gst details", e);
                insertIntoSignzyReqRes(merchantId, null, "GST", "FAILED", mapper.writeValueAsString(request), response, signzyCredential.getModule());
            }
        } catch (Exception e) {
            logger.error("Exception in Signzy GST flow Api", e);
        }
    }

    public String getEnachProvider(String token, Long merchantId) {
        logger.info("Fetching enach provider for merchant:{}", merchantId);
        Long digioFailedCount = bharatPeEnachDao.isDigioFailed(merchantId);
        if (digioFailedCount != null && digioFailedCount >= LendingConstants.DIGIO_FAILED_LIMIT) {
            return "bharatpe://enachtp";
        }
        if (merchantId.equals(1141505L) || merchantId.equals(3612680L) || merchantId.equals(6518986L) || merchantId.equals(4340760L) || merchantId.equals(2097359L) || merchantId.equals(7090157L) || merchantId.equals(5358374L)) {
            return "bharatpe://enachdigio";
        }
        if (ObjectUtils.isEmpty(token)) {
            TokenVerification tokenVerification = tokenVerificationDao.findByMerchantId(merchantId);
            if(ObjectUtils.isEmpty(tokenVerification)) {
                return null;
            }
            token = tokenVerification.getAccessToken();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(headers);
        try {
            logger.info("Enach provider request:{} for merchant:{}", request, merchantId);
            ResponseEntity<Object> response = restTemplate.exchange(env.getProperty("bpnach.endpoint") + LendingConstants.NACH_PROVIDER_URL, HttpMethod.GET, request, Object.class);
            logger.info("Enach provider response:{} for merchant:{}", response.getBody(), merchantId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, String> responseData = (Map<String, String>) ((Map<String, Object>) response.getBody()).get("data");
                return responseData.get("deep_link");
            }
        } catch (Exception e) {
            logger.error("Exception while fetching enach provider for merchant:{}", merchantId, e);
        }
        return null;
    }

    public String signzySnoop(String itemId, String accessToken, String task, Long merchantId, String module, HashMap<String, String> essentials) {
        logger.info("Calling Signzy Snoop Api for itemId:{} and task:{}", itemId, task);
        try {
            Map<String, Object> body = new HashMap<String, Object>() {{
                put("service", "Identity");
                put("itemId", itemId);
                put("task", task);
                put("accessToken", accessToken);
                put("essentials", essentials);
            }};
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String URL = SIGNZY_URL + CreditConstants.SIGNZY_SNOOP_URL;
            logger.info("Signzy Snoop URL:{} and request:{}", URL, request);
            int retryCount = 0;
            while (retryCount < 3) {
                try {
                    ResponseEntity<String> responseEntity = restTemplate.exchange(URL, HttpMethod.POST, request, String.class);
                    logger.info("Response for Signzy Snoop: {}", responseEntity);
                    if (responseEntity.getStatusCode().is2xxSuccessful()) {
                        insertIntoSignzyReqRes(merchantId, null, task.toUpperCase(), "SUCCESS", mapper.writeValueAsString(request), responseEntity.getBody(), module);
                        return responseEntity.getBody();
                    } else {
                        insertIntoSignzyReqRes(merchantId, null, task.toUpperCase(), "FAILED", mapper.writeValueAsString(request), responseEntity.getBody(), module);
                    }
                    break;
                } catch (Exception e) {
                    logger.info("Error occurred while calling signzy snoop api", e);
                    insertIntoSignzyReqRes(merchantId, null, task.toUpperCase(), "FAILED", mapper.writeValueAsString(request), e.getMessage(), module);
                    retryCount++;
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("Exception in Signzy Snoop Api", e);
        }
        return null;
    }

    public ENachIntitiationResponseDTO initiateEnach(EnachInitiateRequestDTO requestDTO) {
        logger.info("Enach initiate request:{} for merchant:{}", requestDTO, requestDTO.getMerchantId());
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", requestDTO.getToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<String, Object>() {{
            put("application_id", requestDTO.getApplicationId());
            put("client_name", requestDTO.getClientName());
            put("nach_amount", requestDTO.getNachAmount());
            put("enach_provider", requestDTO.getEnachProvider());
        }};
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            logger.info("Enach initiate request:{} for merchant:{}", request, merchantId);
            ResponseEntity<ENachIntitiationResponseDTO> responseEntity = restTemplate.exchange(env.getProperty("bpnach.endpoint") + LendingConstants.NACH_INITIATE_URL, HttpMethod.POST, request, ENachIntitiationResponseDTO.class);
            logger.info("Enach initiate response:{} for merchant:{}", responseEntity, merchantId);
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                responseEntity.getBody().setResponse(responseEntity.getBody().isSuccess());
                return responseEntity.getBody();
            }
        } catch (Exception e) {
            logger.error("Exception while initiating enach for merchant:{}", requestDTO.getMerchantId(), e);
        }
        return null;
    }

    public void submitEnach(ENachSubmitRequestDTO requestDTO, String token, Long merchantId, String provider) {
        logger.info("Enach submit request:{} for merchant:{}", requestDTO, merchantId);
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<String, Object>() {{
            put("application_id", requestDTO.getApplicationId());
            put("client_name", "LENDING");
            put("identifier", requestDTO.getIdentifier());
            put("mandate_id", requestDTO.getMandateId());
            put("enach_provider", requestDTO.getProvider() != null ? requestDTO.getProvider() : provider);
            put("status", requestDTO.getStatus());
            put("status_message", requestDTO.getStatusMessage());
            put("response", requestDTO.getResponse());
            put("transaction_identifier", requestDTO.getTransactionIdentifier());
        }};
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            logger.info("Enach submit request:{} for merchant:{}", request, merchantId);
            ResponseEntity<String> response = restTemplate.exchange(env.getProperty("bpnach.endpoint") + LendingConstants.NACH_SUBMIT_URL, HttpMethod.POST, request, String.class);
            logger.info("Submit enach response:{} for merchant:{}", response.getBody(), merchantId);
        } catch (Exception e) {
            logger.info("Exception in enach submit for merchant:{}", merchantId, e);
        }
    }

    public String getTemporarySignzyURL(String base64File) {
        Map<String, Object> body = new HashMap<>();
        body.put("base64String", base64File);
        body.put("mimetype", "image/jpeg");
        body.put("ttl", "7 days");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            String response = restTemplate.postForObject("https://persist.signzy.tech/api/base64", request, String.class);
            logger.info("signzy image URL response:{}", response);
            Map<String, Object> responseMap = mapper.readValue(response, new TypeReference<Map<String, Object>>() {
            });
            Map<String, Object> res = (Map<String, Object>) responseMap.get("file");
            return (String) res.get("directURL");
        } catch (Exception e) {
            logger.info("Exception in signzy image URL", e);
        }
        return null;
    }

    public MerchantInfoDTO getMerchantAddress(Long merchantId) {
        logger.info("Fetching address for merchant:{}", merchantId);
        Map<String, String> requestParams = new HashMap<String, String>() {{
            put("scopes", "address");
            put("merchantids", String.valueOf(merchantId));
        }};
        String payload = hmacCalculator.getPayload(requestParams);
        String hash = hmacCalculator.calculateHMACHexEncoded(payload, getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Hash", hash);
        headers.set("Client-Name", "LENDING");
        HttpEntity<Map<String, String>> request = new HttpEntity<>(headers);
        logger.info("Fetch merchant address request:{}", request);
        int retryCount = 0;
        while (retryCount < 3) {
            try {
                ResponseEntity<MerchantInfoDTO> responseEntity = restTemplate.exchange(Objects.requireNonNull(env.getProperty("merchantinfo.endpoint")) + "?scopes=address&merchantids=" + merchantId, HttpMethod.GET, request, MerchantInfoDTO.class);
                logger.info("Merchant address response:{}", responseEntity.getBody());
                if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && responseEntity.getBody().getStatus()) {
                    return responseEntity.getBody();
                }
                break;
            } catch (Exception e) {
                logger.error("Error occurred while fetching merchant address ", e);
            }
            retryCount++;
        }
        return null;
    }

    private String getInternalSecret() {
        if (org.springframework.util.StringUtils.isEmpty(clientSecret)) {
            InternalClient client = internalClientDao.findByClientName(CLIENT);
            if (client != null) {
                clientSecret = aesEncryption.decrypt(client.getSecret());
            }
        }
        return clientSecret;
    }

    public JsonNode experianRefreshApi(Long merchantId, String hitId) {
        logger.info("Calling Experian Refresh API for merchant:{} with hitId:{}", merchantId, hitId);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("clientName", ExperianConstants.CLIENT_NAME);
        body.add("hitId", hitId);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body);
        Long a = DateTime.now().getMillis();
        String response = restTemplate.postForObject(ExperianConstants.REFRESH_API_URL, request, String.class);
        Long b = DateTime.now().getMillis();
        logger.info("Experian Refresh API response time---{}ms", (b - a));
        try {
            JsonNode jsonNode = mapper.readTree(response);
            if (jsonNode == null || jsonNode.get("showHtmlReportForCreditReport").isNull()) {
                experianService.insertExperianCallRecord(null, "REFRESH_API_URL", mapper.writeValueAsString(request), merchantId, null, null, null);
                return null;
            }
            String xmlResponse = jsonNode.get("showHtmlReportForCreditReport").asText().replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("&quot;", "\"");
            JSONObject jsonObject = XML.toJSONObject(xmlResponse);
            experianService.insertExperianCallRecord(mapper.readTree(jsonObject.toString()).toString(), "REFRESH_API_URL", mapper.writeValueAsString(request), merchantId, null, null, null);
            return mapper.readTree(jsonObject.toString());
        } catch (Exception e) {
            logger.info("Exception while parsing experian refresh api response", e);
            return null;
        }
    }

    public Double getNameMatchPercentage(String authorization, String patronId, String name1, String name2, Long merchantId, Long applicationId) {
        if (name1 == null || name1.isEmpty() || name2 == null || name2.isEmpty()) {
            return 0D;
        }
        String response = null;
        Map<String, Object> body = new HashMap<>();
        body.put("task", "nameMatch");
        body.put("essentials", new HashMap<String, Object>() {{
            put("nameBlock", new HashMap<String, String>() {{
                put("name1", name1);
                put("name2", name2);
            }});
        }});

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authorization);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String URL = SIGNZY_URL + CreditConstants.SIGNZY_NAME_MATCH_URL + patronId + "/matchers";
        int retryCount = 0;
        while (retryCount < 3) {
            try {
                logger.info("Name match URL {} request {}", URL, mapper.writeValueAsString(request));
                response = restTemplate.postForObject(URL, request, String.class);
                logger.info("Name match response : {}", response);
                JsonNode jsonNode = mapper.readTree(response);
                if (jsonNode.has("result") && !jsonNode.get("result").isNull()) {
                    JsonNode result = jsonNode.get("result");
                    if (result.has("name1_vs_name2_matchScore") && !result.get("name1_vs_name2_matchScore").isNull()) {
                        insertIntoSignzyReqRes(merchantId, applicationId, "NAME_MATCH", "SUCCESS", mapper.writeValueAsString(request), response, "lending");
                        return result.get("name1_vs_name2_matchScore").asDouble();
                    } else if (result.has("name2_vs_name1_matchScore") && !result.get("name2_vs_name1_matchScore").isNull()) {
                        insertIntoSignzyReqRes(merchantId, applicationId, "NAME_MATCH", "SUCCESS", mapper.writeValueAsString(request), response, "lending");
                        return result.get("name2_vs_name1_matchScore").asDouble();
                    }
                    break;
                }
            } catch (Exception e) {
                try {
                    insertIntoSignzyReqRes(merchantId, applicationId, "NAME_MATCH", "SUCCESS", mapper.writeValueAsString(request), response, "lending");
                } catch (JsonProcessingException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                logger.error("Error occured while matching names", e);
            }
            retryCount++;
        }
        return 0D;
    }

    public GlobalLimitResponse getGlobalLimit(Long merchantId) throws Exception {
        return getGlobalLimit(merchantId, null, null);
    }

    //    @Async
    public GlobalLimitResponse getGlobalLimit(Long merchantId, String source, Integer appVersion) throws Exception {
        logger.info("Get global limit for merchant:{}", merchantId);
        Map<String, Object> requestParams = new HashMap<String, Object>() {{
            put("merchantId", merchantId);
            put("source", source);
            put("appVersion", appVersion);
        }};
        StringBuilder queryParams = new StringBuilder("?merchantId=").append(merchantId);
        if (!ObjectUtils.isEmpty(source)) {
            queryParams.append("&source=").append(source);
        }
        if (!ObjectUtils.isEmpty(appVersion)) {
            queryParams.append("&appVersion=").append(appVersion);
        }
        String url = Objects.requireNonNull(env.getProperty("lending.global.endpoint")) + "/global_limit" + queryParams;
        String payload = hmacCalculator.getObjectPayload(requestParams);
        String hash = hmacCalculator.calculateHmac(payload, getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("hash", hash);
        headers.set("clientName", CLIENT);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(headers);
        logger.info("Get Global Limit request:{} for merchant:{}, Url :{}", request, merchantId, url);
        int retryCount = 0;
        while (retryCount < 3) {
            try {
                ResponseEntity<GlobalLimitResponse> responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, GlobalLimitResponse.class);
                logger.info("Get Global Limit response:{} for merchant:{}", responseEntity, merchantId);
                if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && responseEntity.getBody().isSuccess()) {
                    return responseEntity.getBody();
                }
                break;
            }
            catch (ResourceAccessException ex) {
                logger.info("Global Limit Api timed out for merchantId: {} {}", merchantId, ex);
            }
            catch (Exception e) {
                logger.error("Error occurred while getting global limit for merchant:{}", merchantId, e.getMessage());
            }
            retryCount++;
        }
        return null;
    }

    public GlobalLimitResponse getGlobalLimit(Long merchantId, String source) throws Exception {
        return getGlobalLimit(merchantId, source, null);
    }

    public void globalLimitTxn(Long merchantId, String mode, Double amount) {
        logger.info("Global limit txn for merchant:{}, mode:{} and amount:{}", merchantId, mode, amount);
        Map<String, Object> requestBody = new HashMap<String, Object>() {{
            put("merchant_id", merchantId);
            put("amount", amount);
            put("mode", mode);
        }};
        String payload = hmacCalculator.getObjectPayload(requestBody);
        String hash = hmacCalculator.calculateHmac(payload, getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("hash", hash);
        headers.set("clientName", CLIENT);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        logger.info("Global Limit txn request:{} for merchant:{}", request, merchantId);
        int retryCount = 0;
        while (retryCount < 3) {
            try {
                ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(Objects.requireNonNull(env.getProperty("lending.global.endpoint")) + "/global_limit/txn", HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
                });
                logger.info("Global Limit txn response:{} for merchant:{}", responseEntity, merchantId);
                if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && responseEntity.getBody().containsKey("success") && Boolean.parseBoolean(responseEntity.getBody().get("success").toString())) {
                    logger.info("Global limit txn success for merchant:{}", merchantId);
                } else {
                    logger.info("Global limit txn failed for merchant:{}", merchantId);
                }
                break;
            } catch (Exception e) {
                logger.error("Error occurred while global limit txn", e);
            }
            retryCount++;
        }
    }

    public Map<String, Object> riskByPspApp(Merchant merchant) {
        Map<String, Object> data = new HashMap<>();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("merchant_id", merchant.getId());
            body.put("limit", 1);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            List<String> responseBody = restTemplate.postForObject(MONGET_API + "?collection_name=merchant_psp_dump", request, List.class);

            if (Objects.isNull(responseBody) || responseBody.isEmpty()) {
                data.put("status", false);
                return data;
            }
            JsonNode responseData = mapper.readTree(responseBody.get(0));


            HashSet<String> pspSet = new HashSet<String>();

            if (Objects.nonNull(responseData.get("app_details"))) {
                for (JsonNode response : responseData.get("app_details")) {
                    if (Objects.nonNull(response.get("appName"))) {
                        pspSet.add(response.get("appName").asText());
                    } else if (Objects.nonNull(response.get("app_name"))) {
                        pspSet.add(response.get("app_name").asText());
                    }
                }
            }

            if (pspSet.contains("BharatPe FOS")) {
                data.put("status", true);
                data.put("reason", ExperianConstants.FOS_APP);
                return data;
            }
            pspSet.retainAll(LendingConstants.OTHER_LENDING_APPS);

            if (pspSet.size() >= 10) {
                data.put("status", true);
                data.put("reason", ExperianConstants.MULTIPLE_PSP_APPS);
                return data;
            }
        } catch (Exception ex) {
            logger.error("Error occurred while fetching psp apps Error:", ex);
            data.put("status", false);
        }
        data.put("status", false);

        return data;
    }

    public void updateApplicationPriority(Long merchantId, Long applicationId) {
        logger.info("Updating application priority for merchant:{} and application:{}", merchantId, applicationId);
        Map<String, Object> requestBody = new HashMap<String, Object>() {{
            put("merchant_id", merchantId);
            put("application_id", applicationId);
        }};
        String payload = hmacCalculator.getObjectPayload(requestBody);
        String hash = hmacCalculator.calculateHmac(payload, getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("hash", hash);
        headers.set("clientName", CLIENT);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        logger.info("Application priority request:{} for merchant:{}", request, merchantId);
        int retryCount = 0;
        while (retryCount < 3) {
            try {
                ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(Objects.requireNonNull(env.getProperty("lending.global.endpoint")) + "/application_priority", HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
                });
                logger.info("Application priority response:{} for merchant:{}", responseEntity, merchantId);
                if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && responseEntity.getBody().containsKey("success") && Boolean.parseBoolean(responseEntity.getBody().get("success").toString())) {
                    logger.info("Application priority success for merchant:{}", merchantId);
                } else {
                    logger.info("Application priority failed for merchant:{}", merchantId);
                }
                break;
            } catch (Exception e) {
                logger.error("Error occurred while Application priority", e);
            }
            retryCount++;
        }
    }

    public Boolean eligibleForProcessingFee(Long merchantId) {

        try {
            logger.info("processing fee redemption eligibility check for merchant:{}", merchantId);
            Map<String, Object> requestParams = new HashMap<String, Object>() {{
                put("merchant_id", merchantId);
            }};
            String payload = hmacCalculator.getObjectPayload(requestParams);
            String hash = hmacCalculator.calculateHmac(payload, getInternalSecret());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("clientName", CLIENT);
            headers.set("hash", hash);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestParams, headers);

            logger.info("processing fee redemption eligibility request:{} for merchant:{}", request, merchantId);

            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(BHARATPE_CLUB_URL, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            logger.info("processing fee redemption eligibility response:{} for merchant:{}", responseEntity, merchantId);
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && responseEntity.getBody().containsKey("success") && Boolean.parseBoolean(responseEntity.getBody().get("success").toString())) {
                JsonNode response = mapper.convertValue(responseEntity.getBody(), JsonNode.class);
                if (Objects.nonNull(response.get("data")) && Objects.nonNull(response.get("data").get("eligibile")) && response.get("data").get("eligibile").asBoolean()) {
                    if (Objects.isNull(response.get("data").get("rewards"))) {
                        return true;
                    }

                    List<Map> rewards = mapper.convertValue(response.get("data").get("rewards"), List.class);
                    System.out.println(rewards);
                    for (Map reward : rewards) {
                        if (Objects.nonNull(reward.get("source_module")) && reward.get("source_module").toString().equals("LOAN")) {
                            return false;
                        }
                    }

                    return true;
                }
            }
        } catch (Exception ex) {
            logger.error("Error occurred while checking processing fee redemption eligibility", ex);
        }

        return false;
    }

    public boolean sendCommunicationForNewOffer(LendingPaymentSchedule activeLoan) {
        if ("CLOSED".equalsIgnoreCase(activeLoan.getStatus())) {
            logger.info("Checking loan offer from Lending for merchant:{}", activeLoan.getMerchant().getId());
            TokenVerification tokenVerification = tokenVerificationDao.findByMerchantId(activeLoan.getMerchant().getId());
            if (tokenVerification == null) {
                logger.info("Token not found for merchant:{}", activeLoan.getMerchant().getId());
                return false;
            }
            try {
                Map<String, Object> body = new HashMap<>();
                body.putIfAbsent("meta", new HashMap<>());
                body.putIfAbsent("payload", new HashMap<String, Object>() {
                });
                logger.info("Calling loan details api from Lending for merchant: {}", activeLoan.getMerchant().getId());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("token", tokenVerification.getAccessToken());
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
                ResponseEntity<LoanDetailsResponseDTO> responseEntity = restTemplate.exchange(Objects.requireNonNull(env.getProperty("loan.details.url")), HttpMethod.POST, request, LoanDetailsResponseDTO.class);
                if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && responseEntity.getBody().getDetails() != null && responseEntity.getBody().getDetails().isEligible() && responseEntity.getBody().getDetails().getEligibility() != null && !responseEntity.getBody().getDetails().getEligibility().isEmpty()) {
                    logger.info("Eligibility found from Lending for merchant:{}", activeLoan.getMerchant().getId());
                    sendComm(activeLoan.getMerchant().getId(), responseEntity.getBody().getDetails().getEligibility().get(0).getAmount(), responseEntity.getBody().getDetails().getEligibility().get(0).getInterestRate());
                    return true;
                }
            } catch (Exception e) {
                logger.error("Unable to call loan details api for merchant:{}", activeLoan.getMerchant().getId(), e);
            }
        }
        return false;
    }

    private void sendComm(Long merchantId, Integer amount, Double interestRate) {
        Merchant merchant = merchantDao.getById(merchantId);

        String identifier = "LENDING_PAYMENT_3_PUSH";
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("loan_amount", amount);
        templateParams.put("interest_rate", interestRate);
        String deeplink = notificationUtil.getDeeplink(merchant, "LOAN_DASHBOARD");
        NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
        notificationPayloadDto.setTemplateIdentifier(identifier);
        notificationPayloadDto.setPushTitle("The loan is closed successfully");
        notificationPayloadDto.setPushDeepLink(deeplink);
        notificationPayloadDto.setTemplateIdentifier(identifier);
        notificationPayloadDto.setMobile(merchant.getMobile());
        notificationPayloadDto.setClientName("LENDING");
        notificationPayloadDto.setTemplateParams(templateParams);
        lendingNotificationService.notify(notificationPayloadDto);
    }

    public LendingPayoutResponse lendingPayout(LendingPayoutRequest lendingPayoutRequest) {
        logger.info("Calling lending payout api for merchant:{}", lendingPayoutRequest.getMerchantId());
        Map<String, Object> requestBody = new HashMap<String, Object>() {{
            put("owner_id", lendingPayoutRequest.getOwnerId());
            put("order_id", lendingPayoutRequest.getOrderId());
            put("amount", lendingPayoutRequest.getAmount());
            put("txn_type", lendingPayoutRequest.getTxnType());
            put("payment_txn_id", lendingPayoutRequest.getPaymentTxnId());
            put("merchant_id", lendingPayoutRequest.getMerchantId());
            put("merchant_store_id", lendingPayoutRequest.getMerchantStoreId());
            put("message", lendingPayoutRequest.getMessage());
        }};
        String payload = hmacCalculator.getObjectPayload(requestBody);
        String hash = hmacCalculator.calculateHmac(payload, getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("hash", hash);
        headers.set("clientName", CLIENT);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        logger.info("Lending payout request:{} for merchant:{}", lendingPayoutRequest, lendingPayoutRequest.getMerchantId());
        String url = lendingPayoutRequest.getTxnType().name().contains("INCENTIVE") ? LendingConstants.LENDING_INCENTIVE_URL : LendingConstants.LENDING_REFUND_URL;
        try {
            ResponseEntity<LendingPayoutResponse> responseEntity = restTemplate.exchange(Objects.requireNonNull(env.getProperty("lending.refund.endpoint")) + url, HttpMethod.POST, request, LendingPayoutResponse.class);
            logger.info("Lending payout response:{} for merchant:{}", responseEntity, lendingPayoutRequest.getMerchantId());
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && responseEntity.getBody().isSuccess() && responseEntity.getBody().getData() != null && "SUCCESS".equals(responseEntity.getBody().getData().getTransactionStatus())) {
                logger.info("Lending payout success for merchant:{}", lendingPayoutRequest.getMerchantId());
                return responseEntity.getBody();
            } else {
                logger.info("Lending payout failed for merchant:{}", lendingPayoutRequest.getMerchantId());
            }
        } catch (Exception e) {
            logger.error("Error occurred while lending payout api", e);
        }
        return null;
    }

    public LdcVirtualAccount createDisbursalVPA(Merchant merchant, LendingApplication lendingApplication) {
        logger.info("Coming In Create Virtual Account");
        LdcVirtualAccount ldcVirtualAccount = ldcVirtualAccountDao.findByMerchantId(merchant.getId());
        if (ldcVirtualAccount != null) {
            return ldcVirtualAccount;
        }
        try {
            Map requestParams = new HashMap<>();
            requestParams.put("type", "LOAN_DISBURSAL");
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), getSecret());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("hash", hash);
            headers.set("mid", getMid());
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestParams, headers);
            logger.info("create virtual account request for disbursal: {}", mapper.writeValueAsString(request));
            int retryCount = 0;
            VANResponseDTO response = null;
            while (retryCount < 3) {
                try {
                    response = restTemplate.postForObject(Objects.requireNonNull(env.getProperty("create.van.url")), request, VANResponseDTO.class);
                    logger.info("Response received from create VAN API for Loan Disbursal {}", response);
                    break;
                } catch (Exception e) {
                    logger.error("Exception in createVPA for Loan Disbursal", e);
                }
                retryCount++;
            }
            if (response != null) {
                if (response.getStatus() != null && "OK".equalsIgnoreCase(response.getStatus())) {
                    return ldcVirtualAccountDao.save(new LdcVirtualAccount(merchant.getId(), lendingApplication.getId(), response.getAccountNumber(), response.getIfsc(), "LENDING"));
                }
            }
        } catch (Exception ex) {
            logger.info("Exception In Create Loan Disbursal VPA:{}", ex);
        }
        return ldcVirtualAccount;
    }

    public JsonNode fetchCrifResponse(Merchant merchant, Experian experian) {
        try {
            logger.info("Fetching Crif for merchant:{}", merchant.getId());
            Map<String, String> merchantName = getFirstLastName(merchant, experian.getPancardNumber());
            String firstName = merchantName.get("first");
            String lastName = merchantName.get("last");
            String contact = merchant.getMobile().substring(2);
            JsonNode stage1Response = crifStage1(firstName, lastName, experian.getPancardNumber(), contact, merchant.getId());
            if (stage1Response != null && stage1Response.get("status") != null && stage1Response.get("status").asText().equals("S06")) {
                logger.info("Crif stage1 success for merchant:{}", merchant.getId());
                JsonNode stage2Response = crifStage2(merchant.getId(), stage1Response.get("orderId").asText(), stage1Response.get("reportId").asText(), null, false, "");
                if (stage2Response != null && stage2Response.get("status") != null && (stage2Response.get("status").asText().equals("S10") || stage2Response.get("status").asText().equals("S01"))) {
                    logger.info("Crif stage2 success for merchant:{}", merchant.getId());
                    JsonNode stage3Response = crifStage2(merchant.getId(), stage1Response.get("orderId").asText(), stage1Response.get("reportId").asText(), null, true, "");
                    if (stage3Response != null) {
                        logger.info("Crif stage3 success for merchant:{}", merchant.getId());
                        if (isValidReport(experian.getPancardNumber(), contact, stage3Response)) {
                            logger.info("Found crif report for merchant:{}", merchant.getId());
                            return stage3Response;
                        } else {
                            logger.info("Invalid crif report for merchant:{}", merchant.getId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Exception while fetching crif for merchant:{}", merchant.getId(), e);
        }
        return null;
    }

    public String getLoanAgreement(Long merchantId, Long applicationId) {
        try {
            LoanAgreement loanAgreement = loanAgreementDao.findByApplicationIdAndType(applicationId, "agreement");
            if (loanAgreement != null && loanAgreement.getShortUrl() != null) {
                return loanAgreement.getShortUrl();
            }
        } catch (Exception ex) {
            logger.info("Exception in Fetching Loan Agreement :{}", ex);
        }
        return null;
    }

    private Map<String, String> getFirstLastName(Merchant merchant, String pancard) {
        MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchant.getId());
        String firstName;
        String lastName;
        if (lendingPancard == null || lendingPancard.getName() == null || !lendingPancard.getPancardNumber().equalsIgnoreCase(pancard)) {
            lendingPancard = fetchNameFromSignzy(pancard, merchant.getId());
        }
        if (lendingPancard != null && lendingPancard.getName() != null && !lendingPancard.getName().trim().equalsIgnoreCase("") && lendingPancard.getPancardNumber() != null && lendingPancard.getPancardNumber().equalsIgnoreCase(pancard)) {
            firstName = LoanUtil.getFirstName(lendingPancard.getName());
            lastName = LoanUtil.getLastName(lendingPancard.getName());
        } else {
            firstName = LoanUtil.getFirstName(merchantBankDetail.getBeneficiaryName());
            lastName = LoanUtil.getLastName(merchantBankDetail.getBeneficiaryName());
        }
        return new HashMap<String, String>() {{
            put("firstName", firstName);
            put("lastName", lastName);
        }};
    }

    public LendingPancard fetchNameFromSignzy(String pancardNumber, Long merchantId) {
        logger.info("Calling Pan Fetch Api for merchant:{}", merchantId);
        try {
            Map<String, String> identityDetail = signzyIdentityDetails("individualPan", merchantId, "PAN", "PAN", new ArrayList<>());
            if (identityDetail != null) {
                String response = signzyPanFetch(identityDetail.get("itemId"), identityDetail.get("accessToken"), pancardNumber, merchantId, identityDetail.get("module"));
                if (response != null && response.equalsIgnoreCase("ERROR_OCCURRED")) {
                    return new LendingPancard(merchantId, pancardNumber, "NAME", null);
                } else if (response != null) {
                    JsonNode responseNode = mapper.readTree(response);
                    if (responseNode != null && responseNode.has("response") && !responseNode.get("response").isNull() && responseNode.get("response").has("result") && !responseNode.get("response").get("result").isNull() && responseNode.get("response").get("result").get("upstreamName") != null) {
                        String name = responseNode.get("response").get("result").get("upstreamName").asText();
                        logger.info("Name:{} found in pancard:{}", name, pancardNumber);
                        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
                        if (lendingPancard != null) {
                            lendingPancard.setName(name);
                            lendingPancard.setResponse(response);
                            lendingPancard.setPancardNumber(pancardNumber);
                            return lendingPancardDao.save(lendingPancard);
                        }
                        return lendingPancardDao.save(new LendingPancard(merchantId, pancardNumber, name, response));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Exception in Signzy Pan Fetch Api", e);
        }
        return null;
    }

    public String getPincodeArea(Integer pincode) {
        LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(pincode);
        return lendingPincodes != null ? lendingPincodes.getColor().name() : PincodeColor.RED.name();
    }

    public String findNtc(Experian experian) {
        if ("2N".equalsIgnoreCase(experian.getCategory()) || "3N".equalsIgnoreCase(experian.getCategory()) || "4N".equalsIgnoreCase(experian.getCategory())) {
            return "Y";
        }
        return "N";
    }

    public Map getKycDetails(LendingApplication lendingApplication) {
        Map result = new HashMap();
        if (ObjectUtils.isEmpty(lendingApplication.getCkycId())) {
            String dob = null;
            DocKycDetails panDetail = docKycDetailsDao.fetchLatestPanCardDetails(lendingApplication.getMerchant().getId(), lendingApplication.getId());
            DocKycDetails poaDetail = docKycDetailsDao.fetchLatestBackAddressDetails(lendingApplication.getMerchant().getId(), lendingApplication.getId());
            if (panDetail == null) {
                panDetail = docKycDetailsDao.fetchPanMerchantId(merchantId);
            }
            if (poaDetail == null) {
                poaDetail = docKycDetailsDao.fetchPoaMerchantId(merchantId);
            }
            Date dateOfBirth = null;
            dob = panDetail.getDob();
            try {
                DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                dateOfBirth = sdf.parse(dob);
            } catch (ParseException e) {
                try {
                    DateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                    dateOfBirth = sdf.parse(dob);
                } catch (ParseException ex) {
                    logger.error("Exception while parsing DOB date:{}", dob, ex);
                }
            }
            String pancardUrl = "";
            String addressProof1 = "";
            String addressProof2 = "";
            try {
                pancardUrl = s3BucketHandler.getPreSignedPublicURL(panDetail.getDocumentsIdProof().getProofFrontSide(), "loan-document");
                if (!"eAadhar".equalsIgnoreCase(poaDetail.getDocumentsIdProof().getProofType()) && !"e_aadhaar".equalsIgnoreCase(poaDetail.getDocumentsIdProof().getProofType())) {
                    addressProof1 = s3BucketHandler.getPreSignedPublicURL(poaDetail.getDocumentsIdProof().getProofFrontSide(), "loan-document");
                    addressProof2 = s3BucketHandler.getPreSignedPublicURL(poaDetail.getDocumentsIdProof().getProofBackSide(), "loan-document");
                }
                if ("eAadhar".equalsIgnoreCase(poaDetail.getDocumentsIdProof().getProofType()) || "e_aadhaar".equalsIgnoreCase(poaDetail.getDocumentsIdProof().getProofType())) {
                    addressProof1 = s3BucketHandler.getPreSignedPublicURL(poaDetail.getDocumentsIdProof().getProofFrontSide(), "lending-ekyc");
                }
            } catch (Exception ex) {
                logger.info("Fetching Document From Bucket", ex);
            }
            LendingEkyc lendingEkyc = lendingEkycDao.findSuccessEkyc(lendingApplication.getMerchant().getId(), lendingApplication.getId());
            if (Objects.nonNull(lendingEkyc)) {
                result.put("ekyc_response", lendingEkyc.getResponse());
            }
            result.put("person_name", panDetail.getPersonName() != null ? panDetail.getPersonName() : panDetail.getDocumentsIdProof().getLendingApplication().getMerchant().getBeneficiaryName());
            result.put("dob", dateOfBirth != null ? new SimpleDateFormat("yyyy-MM-dd").format(dateOfBirth) : poaDetail.getDob());
            result.put("proof_type", poaDetail.getDocType());
            result.put("gender", poaDetail.getGender() != null && poaDetail.getGender() != "" ? poaDetail.getGender() : "Male");
            result.put("pancardUrl", pancardUrl);
            result.put("addressproof1", addressProof1);
            result.put("addressproof2", addressProof2);
        } else {
            List<KycDoc> kycDocs = kycHandler.getKycDoc(lendingApplication.getMerchant().getId());
            for (KycDoc kycDoc : kycDocs) {
                if (KycDocType.POA.equals(kycDoc.getDocType())) {
                    result.put("proof_type", kycDoc.getSubDocType());
                    result.put("addressproof1", kycDoc.getDocFrontImageUrl());
                    result.put("addressproof2", kycDoc.getDocBackImageUrl());
                    result.put("gender", ObjectUtils.isEmpty(kycDoc.getGender()) ? "Male" : (kycDoc.getGender().startsWith("M") ? "Male" : "Female"));
                    result.put("ekyc_response", kycDoc.getResponse());
                }
                if (KycDocType.PAN_CARD.equals(kycDoc.getDocType())) {
                    if (Objects.nonNull(kycDoc.getDob())) {
                        String dob = kycDoc.getDob();
                        Date dateOfBirth = null;
                        try {
                            DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                            dateOfBirth = sdf.parse(dob);
                        } catch (ParseException e) {
                            try {
                                DateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                                dateOfBirth = sdf.parse(dob);
                            } catch (ParseException ex) {
                                logger.error("Exception while parsing DOB date:{}", dob, ex);
                            }
                        }
                        result.put("dob", dateOfBirth != null ? new SimpleDateFormat("yyyy-MM-dd").format(dateOfBirth) : kycDoc.getDob());
                    }
                    result.put("pancardUrl", kycDoc.getDocFrontImageUrl());
                    result.put("person_name", ObjectUtils.isEmpty(kycDoc.getName()) ? lendingApplication.getMerchant().getBeneficiaryName() : kycDoc.getName());

                }
            }
        }
        return result;
    }

    private boolean isValidReport(String panCard, String phoneNumber, JsonNode response) {
        boolean checkPan = false;
        boolean checkPhone = false;
        if (response != null) {
            JsonNode personalData = response.get(CrifConstants.REPORT_HEADER)
                    .get(CrifConstants.PERSONAL_VARIATIONS);
            if (personalData == null || personalData.toString().equalsIgnoreCase("\"\"")) {
                return false;
            }
            if (personalData.get(CrifConstants.PAN_VARIATIONS) == null
                    || personalData.get(CrifConstants.PAN_VARIATIONS).toString().equalsIgnoreCase("\"\"")) {
                return false;
            }
            if (personalData.get(CrifConstants.PHONE_VARIATIONS) == null
                    || personalData.get(CrifConstants.PHONE_VARIATIONS).toString().equalsIgnoreCase("\"\"")) {
                return false;
            }
            List<JsonNode> panVariations = LoanUtil
                    .jsonNodeArrayUtil(personalData.get(CrifConstants.PAN_VARIATIONS).get(CrifConstants.VARIATION));
            List<JsonNode> phoneVariations = LoanUtil
                    .jsonNodeArrayUtil(personalData.get(CrifConstants.PHONE_VARIATIONS).get(CrifConstants.VARIATION));
            if (phoneNumber.length() > 10) {
                phoneNumber = phoneNumber.substring(2);// remove 91
            }
            for (JsonNode pan : panVariations) {
                checkPan = pan.get("VALUE").asText().equalsIgnoreCase(panCard);
                if (checkPan) break;
            }
            for (JsonNode phone : phoneVariations) {
                checkPhone = phone.get("VALUE").asText().equalsIgnoreCase(phoneNumber) || phone.get("VALUE").asText().equalsIgnoreCase("91" + phoneNumber);
                if (checkPhone) break;
            }
        }
        return checkPan && checkPhone;
    }

    public ResponseEntity<Object> fosAttribution(Long merchantId, String taskName, String status) {
        try {
            logger.info("FOS Attribution Service Function called lending appliaction: {},taskName: {},status: {}", merchantId, taskName, status);

            Integer taskId = 0;
            if (SupportConstants.NTB_LOAN.equalsIgnoreCase(taskName)) {
                taskId = 18;
            } else if (SupportConstants.NTB_LOAN_V2.equalsIgnoreCase(taskName)) {
                taskId = 21;
            } else if (SupportConstants.CPV.equalsIgnoreCase(taskName)) {
                taskId = 13;
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put(SupportConstants.TASK_ID, taskId);
            requestBody.put(SupportConstants.MERCHANT_ID, merchantId);
            requestBody.put(SupportConstants.STATUS, status);

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(SupportConstants.HASH, hmacCalculator.calculateHmac(hmacCalculator.getObjectPayload(requestBody), getInternalSecret()));
            headers.set(SupportConstants.CLIENT_NAME, CLIENT);

            HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity responseEntity = null;
            logger.info("FOS Attribution Service API request:{} for merchantId:{}", entity, merchantId);

            responseEntity = restTemplate.exchange(env.getProperty("salesdashboard.fos.attribution") + LendingConstants.FOS_ATTRIBUTION, HttpMethod.POST, entity, String.class);

            logger.info("FOS Attribution Service API response:{} for merchantId:{}", responseEntity, merchantId);

        } catch (Exception e) {
            logger.error("Error occurred while calling FOS Attribution Api for merchant:{}", merchantId, e);
        }

        return null;

    }

    public Boolean ldcDisburse(LendingApplication lendingApplication) {
        try {
            Map<String, Object> requestParams = new HashMap<String, Object>() {{
                put("application_id", lendingApplication.getId());
                put("lender_name", lendingApplication.getLender());
            }};
            String payload = hmacCalculator.getObjectPayload(requestParams);
            String hash = hmacCalculator.calculateHmac(payload, getInternalSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.set("Hash", hash);
            headers.set("Client-Name", CLIENT);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestParams, headers);
            logger.info("Ldc Service Request :{}", request);
            ResponseEntity<String> response = null;
            try {
                response = restTemplate.postForEntity(NBFC_URL, request, String.class);
                logger.info("Nbfc Service Response :{}", response);
            } catch (HttpClientErrorException | HttpServerErrorException ex) {
                logger.error("Exception In Calling NBFC Service:{}", ex.getResponseBodyAsString());
                return Boolean.FALSE;
            }
            logger.info("Ldc Service Response :{}", response);
            if (response != null && response.getStatusCode().equals(HttpStatus.OK) && response.getBody() != null) {
                JsonNode jsonNode = mapper.readTree(response.getBody());
                if (jsonNode.get("data") != null && jsonNode.get("data").get("loan_id") != null) {
                    lendingApplication.setSendToNbfc("YES");
                    lendingApplication.setNbfcSendDate(new Date());
                    lendingApplication.setDisbursalPartner("BHARATPE");
                    lendingApplication.setNbfcId(jsonNode.get("data").get("loan_id").textValue());
                    lendingApplication.setLoanDisbursalStatus("PENDING");
                    lendingApplicationDao.save(lendingApplication);
                    return Boolean.TRUE;
                }
            }
        } catch (Exception ex) {
            logger.error("Exception In Creating Loan Agreement :{}", ex);
        }
        return Boolean.FALSE;
    }

    public void cancelEnach(Long merchantId) {
        logger.info("Cancel enach for merchant:{}", merchantId);
        Map<String, Object> requestParams = new HashMap<String, Object>() {{
            put("merchant_id", merchantId);
        }};
        String payload = hmacCalculator.getObjectPayload(requestParams);
        String hash = hmacCalculator.calculateHmac(payload, getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("hash", hash);
        headers.set("clientName", CLIENT);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestParams, headers);
        logger.info("Cancel enach request:{} for merchant:{}", request, merchantId);
        try {
            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(env.getProperty("bpnach.endpoint") + LendingConstants.CANCEL_ENACH_URL, HttpMethod.PUT, request, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            logger.info("Cancel enach response:{} for merchant:{}", responseEntity, merchantId);
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && responseEntity.getBody().containsKey("success") && Boolean.parseBoolean(responseEntity.getBody().get("success").toString())) {
                logger.info("Cancel enach success for merchant:{}", merchantId);
            } else {
                logger.info("Cancel enach failed for merchant:{}", merchantId);
            }
        } catch (Exception e) {
            logger.error("Error occurred in cancel enach for merchant:{}", merchantId, e);
        }
    }

    public JsonNode getMerchantSmsAnalysisData(Merchant merchant) {
        try {
            logger.info("Getting SMS analysis data for merchant:{}", merchant.getId());
            Map<String, Object> body = new HashMap<>();
            body.put("identifier", merchant.getMid());
            body.put("limit", 1);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            logger.info("Monget sms analysis request:{} for merchant:{}", request, merchant.getId());
            ResponseEntity<List<String>> responseBody = restTemplate.exchange(env.getProperty("monget.generic.url") + "?collection_name=merchant_sms_analysis", HttpMethod.POST, request, new ParameterizedTypeReference<List<String>>() {
            });
            logger.info("Monget sms analysis response:{} for merchant:{}", responseBody, merchant.getId());
            if (responseBody.getBody() == null || responseBody.getBody().isEmpty()) {
                logger.info("SMS analysis data not found for merchant:{}", merchant.getId());
                return null;
            }
            return mapper.readTree(responseBody.getBody().get(0));
        } catch (Exception ex) {
            logger.error("Error occurred while fetching sms analysis data for merchant:{}", merchant.getId(), ex);
        }
        return null;
    }

    public boolean checkPennyDrop(MerchantBankDetail merchantBankDetail, Merchant merchant) {
        boolean success = false;
        try {
            logger.info("Calling penny drop api for merchantId:{}", merchant.getId());
            Map<String, String> requestParams = new HashMap<String, String>() {{
                put("accountNo", merchantBankDetail.getAccountNumber());
                put("ifsc", merchantBankDetail.getIfscCode());
                put("mobile", merchant.getMobile());
            }};
            String url = "http://payout-java.bharatpe.in/pennyDrop/v3";
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), getInternalSecret());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("hash", hash);
            headers.set("clientName", CLIENT);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestParams, headers);
            logger.info("Penny drop request: {} for merchant:{} ", mapper.writeValueAsString(request), merchant.getId());
            ResponseEntity<PennyDropResponse> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, PennyDropResponse.class);
            logger.info("Penny Drop Response:{} for merchant:{}", responseEntity, merchant.getId());
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && "100".equals(responseEntity.getBody().getResponseCode()) && "OK".equals(responseEntity.getBody().getStatus())) {
                logger.info("Penny drop success for merchant:{}", merchant.getId());
                success = true;
            } else {
                logger.info("Penny drop failed for merchant:{}", merchant.getId());
            }
            LendingPennydrop lendingPennydrop = new LendingPennydrop();
            lendingPennydrop.setAccountNumber(merchantBankDetail.getAccountNumber());
            lendingPennydrop.setMerchantId(merchantBankDetail.getMerchant().getId());
            lendingPennydrop.setIfscCode(merchantBankDetail.getIfscCode());
            lendingPennydrop.setRetryCount(1L);
            lendingPennydrop.setStatus(success ? "SUCCESS" : "FAILED");
            lendingPennydropDao.save(lendingPennydrop);
        } catch (Exception e) {
            logger.info("Bank Penny Drop Exception for merchant:{}", merchant.getId(), e);
        }
        return success;
    }

    public PgStatusResponse checkPgStatus(String orderId) {
        logger.info("Pg status check for  and orderId:{}", orderId);
        try {
            Map<String, String> requestBody = new HashMap<String, String>() {{
                put("orderId", orderId);
            }};
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestBody), getSecret());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("hash", hash);
            headers.set("mid", getMid());
            HttpEntity<Map> request = new HttpEntity<>(headers);

            logger.info("Pg status Check internal request: {}", mapper.writeValueAsString(request));
            int retryCount = 0;
            while (retryCount < 3) {
                try {
                    ResponseEntity<PgStatusResponse> response = restTemplate.exchange(PG_URL + LendingConstants.PG_STATUS_CHECK + orderId, HttpMethod.GET, request, PgStatusResponse.class);
                    logger.info("Response received from Pg status Check API {}", mapper.writeValueAsString(response));
                    return response.getBody();
                } catch (Exception e) {
                    logger.error("Exception in Pg status for order_id:{} error ", orderId, e);
                }
                retryCount++;
            }
        } catch (HttpClientErrorException ex) {
            logger.info("Response from API GAteway : {}", ex.getResponseBodyAsString());
            logger.error("Error in api call to Pg status Check for order id:{}, error:", orderId, ex);
        } catch (Exception ex) {
            logger.error("error in Pg status Check for order id:{}, error", orderId, ex);
        }
        return null;
    }

    private String getBeneficiaryName(String beneficiaryName) {
        if (beneficiaryName.length() > 25) {
            beneficiaryName = beneficiaryName.substring(0, 25);
        }
        return beneficiaryName;
    }

    public Boolean isSdkInvoke(Merchant merchant) {
        try {
            logger.info("Getting variable SMS data for merchant:{}", merchant.getId());
            Map<String, Object> body = new HashMap<>();
            body.put("identifier", merchant.getMid());
            body.put("limit", 1);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            logger.info("Monget variable sms request:{} for merchant:{}", request, merchant.getId());
            ResponseEntity<List<String>> responseBody = restTemplate.exchange(env.getProperty("monget.generic.url") + "?collection_name=merchant_variable_sms_data", HttpMethod.POST, request, new ParameterizedTypeReference<List<String>>() {
            });
            logger.info("Monget variable sms response:{} for merchant:{}", responseBody, merchant.getId());
            if (responseBody.getBody() == null || responseBody.getBody().isEmpty()) {
                logger.info("Variable SMS analysis data not found for merchant:{}", merchant.getId());
                return true;
            }
        } catch (Exception ex) {
            logger.error("Error occurred while fetching variable sms data for merchant:{}", merchant.getId(), ex);
        }
        return false;
    }

    public Double getCreditCardDueAmount(Long merchantId) {
        try {
            logger.info("Getting credit card due amount for merchant:{}", merchantId);
            String url = env.getProperty("credit.card.base.url") + LendingConstants.CREDIT_CARD_STATUS_URL + merchantId;
            Map<String, Object> requestParams = new HashMap<String, Object>() {{
                put("merchant_id", merchantId);
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(headers);
            ResponseEntity<CreditCardStatus> responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, CreditCardStatus.class);
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                CreditCardStatus creditCardStatus = responseEntity.getBody();
                if (creditCardStatus.getPayload() != null && creditCardStatus.getPayload().getDueAmount() != null) {
                    return creditCardStatus.getPayload().getDueAmount();
                }
            }
        } catch (Exception e) {
            logger.error("Exception in getCreditCardDueAmount for merchant:{}", merchantId, e);
        }
        return 0D;
    }

    public Double getGoldLoanDueAmount(Long merchantId) {
        try {
            logger.info("Getting gold loan due amount for merchant:{}", merchantId);
            String url = env.getProperty("gold.loan.base.url") + LendingConstants.GOLD_LOAN_DUE_URL + merchantId;
            Map<String, Object> requestParams = new HashMap<String, Object>() {{
                put("merchantId", merchantId);
            }};
            HttpHeaders headers = getApiHeaders(requestParams);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(headers);
            logger.info("Request of get gold-loan due amount is {}, for merchantId: {}", request, merchantId);
            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>() {
            });
            logger.info("Response fof get gold-loan due amount is {}, for merchantId: {}", request, merchantId);
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && responseEntity.getBody().get("data") != null) {
                Map<String, Object> dataMap = mapper.convertValue(responseEntity.getBody().get("data"), Map.class);
                if (dataMap.get("amount") != null) {
                    return Double.parseDouble(dataMap.get("amount").toString());
                }
            }
        } catch (Exception e) {
            logger.error("Exception in getGoldLoanDueAmount for merchant:{}", merchantId, e);
        }
        return 0D;
    }

    private HttpHeaders getApiHeaders(Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("clientName", CLIENT);
        headers.set("hash", getHmacBase64(requestBody));
        return headers;
    }

    public String getHmacBase64(Map<String, Object> requestBody) {
        String hmac = "";
        try {
            String payload = hmacCalculator.getNestedPayload(requestBody);
            hmac = hmacCalculator.calculateHmac(payload, getInternalSecret());
        } catch (Exception e) {
            logger.error("Exception occurred while generating hmac", e);
        }
        return hmac;
    }

    public String getShortUrl(String url)  {
        try {
            String shortApiUrl = "https://bharatpe.in/yourls-api.php?signature=a872b1348e&action=shorturl&format=json&keyword=&url="+ URLEncoder.encode(url,"UTF-8");
            JsonNode response = restTemplate.getForObject(shortApiUrl,JsonNode.class);
            logger.info("ShortUrl Api Response : {}", response);
            if(Objects.nonNull(response) && "success".equalsIgnoreCase(response.get("status").asText())) {
                return response.get("shorturl").asText();
            }
        } catch (Exception e) {
            logger.error("Exception while parsing short url---", e);
        }
        return null;
    }

    public DsImageValidationResponseDto validateImage(DsImageValidationRequestDto dsImageValidationRequestDto) {
        try {
            logger.info("validating image from ds api : {}", dsImageValidationRequestDto);
            String url = env.getProperty("ds.image.validation.url");
            String user = env.getProperty("ds.image.validation.user");
            String pass = env.getProperty("ds.image.validation.pass");
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-type", "multipart/form-data; boundary=---011000010111000001101001");
            headers.setBasicAuth(user, pass);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("s3_url", dsImageValidationRequestDto.getS3Url());
            body.add("file", dsImageValidationRequestDto.getFile());
            body.add("extract_structure", dsImageValidationRequestDto.getExtractStructure());
            body.add("extract_existence", dsImageValidationRequestDto.getExtractExistence());
            body.add("extract_category", dsImageValidationRequestDto.getExtractCategory());
            body.add("verify_google", dsImageValidationRequestDto.getVerifyGoogle());
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            int retryCount = 0;
            ResponseEntity<DsImageValidationResponseDto> responseEntity = null;
            while (retryCount < 2) {
                try {
                    responseEntity = customRestTemplate.exchange(url, HttpMethod.POST, request, DsImageValidationResponseDto.class);
                    logger.info("ds vision api response:{}", responseEntity);
                    break;
                } catch (Exception e) {
                    logger.info("ds vision api timeout {}", e);
                }
                retryCount++;
            }
            if (!ObjectUtils.isEmpty(responseEntity) && responseEntity.getStatusCode().is2xxSuccessful() && !ObjectUtils.isEmpty(responseEntity.getBody())) {
                DsImageValidationResponseDto dsImageValidationResponseDto = responseEntity.getBody();
                return dsImageValidationResponseDto;
            }
        } catch (Exception e) {
            logger.error("error occurred while validating image", e);
        }
        return null;
    }

    public AddressValidationDto validateAddress(AddressDetails addressDetails) {
        try {
            logger.info("fetching address validation score from delhivery for address {}", addressDetails);
            String url = env.getProperty("delhivery.address.validation.url");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("x-api-key", env.getProperty("delhivery.address.validation.key"));
            Map<String, Object> body = new HashMap<>();
            Map<String, Object> requestBodyParams = new HashMap<>();
            String address = "";
            address += !ObjectUtils.isEmpty(addressDetails.getAddress1()) ? addressDetails.getAddress1(): "";
            address += !ObjectUtils.isEmpty(addressDetails.getAddress2()) ? ", " + addressDetails.getAddress2(): "";
            address += !ObjectUtils.isEmpty(addressDetails.getLandmark()) ? ", " +addressDetails.getLandmark(): "";
            requestBodyParams.put("address", address);
            requestBodyParams.put("pincode", addressDetails.getPincode());
            requestBodyParams.put("city",addressDetails.getCity());
            requestBodyParams.put("state", addressDetails.getState());
            body.put("data", requestBodyParams);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            logger.info("Request {} for address validation", request);
            int retryCount = 0;
            ResponseEntity<AddressValidationDto> responseEntity = null;
            while (retryCount < 2) {
                try {
                    responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, AddressValidationDto.class);
                    logger.info("Delhivery address validation response:{}", responseEntity);
                    break;
                } catch (Exception e) {
                    logger.info("Delhivery api timeout", e);
                }
                retryCount++;
            }
            if (!ObjectUtils.isEmpty(responseEntity) && responseEntity.getStatusCode().is2xxSuccessful() && !ObjectUtils.isEmpty(responseEntity.getBody()) ) {
                AddressValidationDto addressValidationDto = responseEntity.getBody();
                return addressValidationDto;
            } else {
                logger.info("unable to fetch address validation score from delhivery");
            }
        } catch (Exception e) {
            logger.error("Exception occurred while fetching validity of the address {}", addressDetails, e);
        }
        return null;
    }
}