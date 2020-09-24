package com.bharatpe.lending.service;

import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.enums.Gateway;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.CrifRequestResponse;
import com.bharatpe.lending.common.entity.SignzyCredential;
import com.bharatpe.lending.common.entity.SignzyRequestResponse;
import com.bharatpe.lending.constant.CreditConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.*;

import javax.annotation.PostConstruct;

@Service
public class APIGatewayService {

    private static Logger logger = LoggerFactory.getLogger(APIGatewayService.class);

    @Value("${create.vpa.endpoint}")
    String createVPAEndpoint;
    
    @Value("${internal.merchant.id}")
    long merchantId;

    @Autowired
    private HmacCalculator hmacCalculator;

    @Autowired
    private AesEncryption aesEncryption;
    
    @Autowired
    private MerchantDao merchantDao;

    @Autowired
    private SignzyCredentialDao signzyCredentialDao;

    @Value("${signzy.url}")
    public String SIGNZY_URL;
    
    private static String SECRET;
    private static String MID;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    SignzyRequestResponseDao signzyRequestResponseDao;

    @Autowired
    Environment env;

    @Autowired
    CrifRequestResponseDao crifRequestResponseDao;
    
    @PostConstruct
    public void init() {
    	try { 
    		getSecret();
    		getMid();
    	} catch(Exception ex) {
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
            requestParams.put("gateway", Gateway.FEDERAL.name());
            requestParams.put("beneficiaryName", "BharatPe Loans");
            if(vpa!=null) {
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
            
            Map response = restTemplate.postForObject(createVPAEndpoint, request, Map.class);
            logger.info("Response received from create VPA API {}", mapper.writeValueAsString(response));
            return response;
        } catch (HttpClientErrorException ex) {
			logger.info("Response from API GAteway : {}" , ex.getResponseBodyAsString());
			logger.error("Error in api call to generate dynamic vpa for merchant {}, Exception is {}", merchant.getId(), ex);
		} catch (Exception ex) {
            logger.error("error processing txn for dynamic vpa for merchant id {}, Exception is {}", merchant.getId(), ex);
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

    public Map<String,String> signzyIdentityDetails(String proofType, Long merchantId) {
        logger.info("Calling Signzy Identity flow Api for proof:{}", proofType);
        try {
            SignzyCredential signzyCredential = signzyCredentialDao.findByModule("LENDING");
            if (signzyCredential == null) {
                logger.info("signzy credentials not found");
                return null;
            }
            Map<String, Object> body = new HashMap<>();
            body.put("type", proofType);
            body.put("email", "admin@signzy.com");
            body.put("callbackUrl", "https://your-domain.com/your-callback-system");
            body.put("images", new ArrayList<>());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", signzyCredential.getAccessId());
            HttpEntity<Map<String, Object>> request  = new HttpEntity<>(body, headers);
            String URL = SIGNZY_URL + CreditConstants.SIGNZY_IDENTITY_URL + "/" + signzyCredential.getUserId() + "/identities";
            logger.info("Request body to get signzy identity credentials URL {} request {}",URL,request);
            String response= null;
            int retryCount = 0;
            while(retryCount < 3) {
                try {
                    response = restTemplate.postForObject(URL,request, String.class);
                    logger.info("Signzy identity response {}",response);
                    if(response!=null) {
                        JsonNode jsonNode = mapper.readTree(response);
                        if(jsonNode!=null && jsonNode.has("id") && jsonNode.has("accessToken")) {
                            insertIntoSignzyReqRes(merchantId, null, "IDENTITY", "SUCCESS", mapper.writeValueAsString(request), response);
                            Map<String, String> identityDetail = new HashMap<>();
                            identityDetail.put("itemId", jsonNode.get("id").asText());
                            identityDetail.put("accessToken", jsonNode.get("accessToken").asText());
                            return identityDetail;
                        } else {
                            insertIntoSignzyReqRes(merchantId, null, "IDENTITY", "FAILED", mapper.writeValueAsString(request), response);
                        }
                    }
                    break;
                }
                catch(Exception e) {
                    logger.info("Error occurred while fetching identity details",e);
                    insertIntoSignzyReqRes(merchantId, null, "IDENTITY", "FAILED", mapper.writeValueAsString(request), response);
                }
                retryCount++;
            }
        } catch (Exception e) {
            logger.error("Exception in Signzy Identity flow Api", e);
        }
        return null;
    }

    public String signzyPanFetch(String itemId, String accessToken, String pancard, Long merchantId) {
        logger.info("Calling Signzy Pan Fetch Api for pancard:{}", pancard);
        try {
            Map<String, Object> body = new HashMap<String,Object>() {{
                put("service","Identity");
                put("itemId",itemId);
                put("task","fetch");
                put("accessToken",accessToken);
                put("essentials",new HashMap<String, String>(){{
                    put("number", pancard);
                }});
            }};
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request  = new HttpEntity<>(body, headers);
            String URL = SIGNZY_URL + CreditConstants.SIGNZY_SNOOP_URL;
            logger.info("Signzy Pan Fetch URL {} request {}",URL,request);
            String response = null;
            int retryCount = 0;
            while(retryCount < 3) {
                try {
                    response = restTemplate.postForObject(URL, request, String.class);
                    logger.info("Response for Signzy Pan Fetch: {}",response);
                    insertIntoSignzyReqRes(merchantId, null, "PAN_FETCH", "SUCCESS", mapper.writeValueAsString(request), response);
                    break;
                }
                catch(Exception e) {
                    logger.info("Error occurred while calling pan fetch api",e);
                    insertIntoSignzyReqRes(merchantId, null, "PAN_FETCH", "FAILED", mapper.writeValueAsString(request), response);
                    retryCount++;
                }
            }
            return response;
        } catch (Exception e) {
            logger.error("Exception in Signzy Pan Fetch Api", e);
        }
        return null;
    }

    public void insertIntoSignzyReqRes(Long merchantId, Long applicationId,String apiName, String status, String request,String response) {
        try {
            SignzyRequestResponse signzyRequestResponse=new SignzyRequestResponse(merchantId, applicationId, StringUtils.substring(apiName, 0, 20), status, request, response);
            signzyRequestResponseDao.save(signzyRequestResponse);
        }
        catch(Exception e) {
            logger.error("Error occured while inserting into signzy req res table ",e);
        }
    }

    public JsonNode crifStage1(String firstName, String lastName, String pancard, String mobile, Long merchantId){
        try {
            logger.info("Calling CRIF stage1 api for merchant:{} with pancard:{}", merchantId, pancard);
            String accessCode = generateAccessCode();
            String orderId = RandomStringUtils.randomAlphanumeric(6).toUpperCase();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set("orderId", orderId);
            headers.set("accessCode", accessCode);
            headers.set("appID", env.getProperty("crif.appId"));
            headers.set("merchantID", env.getProperty("crif.customerId"));
            String body = firstName + "||" + lastName + "|||||" + mobile + "|||||" + pancard + "|||||||||||||||||||||||" + env.getProperty("crif.customerId") + "|BBC_CONSUMER_SCORE#85#2.0|Y|";
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            logger.info("CRIF request:{}", request);
            CrifRequestResponse crifRequestResponse = new CrifRequestResponse(merchantId, firstName, lastName, pancard, mobile, orderId, accessCode, null, "STAGE1", null, mapper.writeValueAsString(request), null);
            crifRequestResponseDao.save(crifRequestResponse);
            ResponseEntity<String> response = restTemplate.exchange(Objects.requireNonNull(env.getProperty("crif.stage1.url")), HttpMethod.POST, request, String.class);
            logger.info("CRIF response:{}", response.getBody());
            if (response.getStatusCode().equals(HttpStatus.OK) && response.getBody() != null) {
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

    public JsonNode crifStage2(String orderId, String reportId, String redirectUrl) {

    }

    private String generateAccessCode() {
        String value = env.getProperty("crif.userId") + "|" + env.getProperty("crif.customerId") + "|BBC_CONSUMER_SCORE#85#2.0|" + env.getProperty("crif.password") + "|" + new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
        return Base64.getEncoder().encodeToString(value.getBytes());
    }
}