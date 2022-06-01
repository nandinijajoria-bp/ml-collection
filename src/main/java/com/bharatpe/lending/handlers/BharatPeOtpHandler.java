package com.bharatpe.lending.handlers;
import com.bharatpe.common.dao.InternalClientDao;
import com.bharatpe.common.entities.InternalClient;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
@Component
public class BharatPeOtpHandler{
    private static final Logger logger = LoggerFactory.getLogger(BharatPeOtpHandler.class);
    @Autowired
    Environment env;
    @Autowired
    HmacCalculator hmacCalculator;

    @Autowired
    InternalClientDao internalClientDao;
    @Autowired
    AesEncryption aesEncryption;
    @Autowired
    RestTemplate restTemplate;
    private final String CLIENT = "LENDING";
    private static String clientSecret;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    private String getInternalSecret() {
        if(org.springframework.util.StringUtils.isEmpty(clientSecret)) {
            InternalClient client = internalClientDao.findByClientName(CLIENT);
            if (client != null) {
                clientSecret = aesEncryption.decrypt(client.getSecret());
            }
        }
        return clientSecret;
    }

    public Map<String, Object> sendOtp(String mobile, String message) {
        Map<String, Object> response = new HashMap<String, Object>();
        Map<String, Object> requestBody = new HashMap<String, Object>(){{
            put("phoneNumber", mobile);
            put("source", env.getProperty("send.otp.source"));
            put("purpose", env.getProperty("send.otp.purpose"));
            put("clientName", env.getProperty("send.otp.source"));
            put("smsTemplate", message);

        }};
        String payload = hmacCalculator.getObjectPayload(requestBody);
        logger.info("Payload: {}", payload);
        String hash = hmacCalculator.calculateHmac(payload, getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Hash", hash);
        headers.set("Client-Name", CLIENT);
        HttpEntity<Map<String, Object>> request  = new HttpEntity<>(requestBody, headers);
            try {
                logger.info("Bharatpe send otp Request: {}, URL: {}",request,Objects.requireNonNull(env.getProperty("lending.otp.endpoint")));
                ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(Objects.requireNonNull(env.getProperty("lending.otp.endpoint")), HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {});
                logger.info("Bharatpe send otp Response: {}", responseEntity);
                if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && responseEntity.getBody().get("status")!=null && responseEntity.getBody().get("status").equals("success")) {
                    response.put("success", Boolean.TRUE);
                    Map<String, Object> body = responseEntity.getBody();
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    response.put("uuid", data.get("mobileuuid"));
                }

            }
            catch(Exception e) {
                logger.error("Error occurred while generating otp", e);
            }
        return response;

    }


    public Boolean verifyOtp(BasicDetailsDto merchant, String otp, String uuid) {

        if(easyLoanUtil.isDummyMerchant(merchant.getId()) && "1234".equalsIgnoreCase(otp)) {
            //for dummy merchants skipped otp verification
            return Boolean.TRUE;
        }

        boolean responseFlag=false;
        Map<String, Object> requestBody = new HashMap<String, Object>(){{
            put("uuid", uuid);
            put("otp", otp);

        }};
        String payload = hmacCalculator.getObjectPayload(requestBody);
        logger.info("Payload: {}", payload);
        String hash = hmacCalculator.calculateHmac(payload, getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Hash", hash);
        headers.set("Client-Name", CLIENT);
        HttpEntity<Map<String, Object>> request  = new HttpEntity<>(requestBody, headers);
        try {
            logger.info("Bharatpe verify otp Request: {}, URL: {}",request,Objects.requireNonNull(env.getProperty("lending.verifyOtp.endpoint")));
            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(Objects.requireNonNull(env.getProperty("lending.verifyOtp.endpoint")), HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {});
            logger.info("Bharatpe verify otp Response: {}", responseEntity);
            Map<String,Object> body = responseEntity.getBody();
            Map<String,Object> data = (Map<String,Object>) body.get("data");
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null && responseEntity.getBody().get("status")!=null && responseEntity.getBody().get("status").equals("success") && data.get("status")!=null && data.get("status").equals("verified")) {
                responseFlag=true;
            }
        }
        catch(Exception e) {
            logger.error("Error occurred while verifying otp", e);
        }
        return responseFlag;

    }
}
