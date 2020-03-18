package com.bharatpe.lending.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.bharatpe.common.dao.ExternalGatewayDao;
import com.bharatpe.common.dao.LendingPancardDao;
import com.bharatpe.common.entities.ExternalGateway;
import com.bharatpe.common.entities.LendingPancard;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;

@Component
public class LiquiloansService {
	
    private Logger logger = LoggerFactory.getLogger(LiquiloansService.class);

    @Autowired
    ExternalGatewayDao externalGatewayDao;

    @Autowired
    HmacCalculator hmacCalculator;
    
    @Autowired
    AesEncryption aesEncryption;
    
    @Autowired
    RestTemplate restTemplate;
    
    @Autowired
    LendingPancardDao lendingPancardDao;
    
    public LendingPancard fetchNameOnPancard(String pancardNumber, Long merchantId) {
        String name = null;
        String apiResponse = null;
        try {
            ExternalGateway externalGateway = externalGatewayDao.findByGatewayNameAndTypeAndStatus("LIQUILOANS", null, "ACTIVE");
            if (externalGateway != null) {
                Map<String, String> requestParams = new HashMap<>();
                Date currentTime = new Date();
                String payload = pancardNumber + "||" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentTime);
                String checksum = hmacCalculator.calculateHMACHexEncoded(payload, aesEncryption.decrypt(externalGateway.getSecret()));
                logger.info("Liquiloans Checksum:{} for payload: {} for merchant:{}, PAN: {}", checksum, payload, merchantId, pancardNumber);
                requestParams.put("MID", externalGateway.getMbid());
                requestParams.put("Pan", pancardNumber);
                requestParams.put("Timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentTime));
                requestParams.put("Checksum", checksum);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setCacheControl(CacheControl.noCache());
                HttpEntity<Map<String, String>> request = new HttpEntity<>(requestParams, headers);
                try {
                    long startTime = System.currentTimeMillis();
                    Map response = restTemplate.postForObject("https://api.liquiloans.com/api/apiintegration/v3/VerifyPanNumber", request, Map.class);
                    logger.info("Liquloans PAN validation API response: {}, response time: {}ms", response, (System.currentTimeMillis() - startTime));
                    if (response != null && response.containsKey("status")) {
                        apiResponse= response.toString();
                        boolean status = (boolean) response.get("status");
                        Map responseDataMap = (Map) response.get("data");
                        String statusCode = (String) responseDataMap.get("status-code");
                        if (status && statusCode.equals("101")) {
                            Map responseResultMap = (Map) responseDataMap.get("result");
                            name = (String) responseResultMap.get("name");
                            logger.info("Liquiloans Set status success for merchant: {}", merchantId);
                        } else {
                            logger.info("Liquiloans Set status failed Response params status : {}, status code: {} for merchant: {}", status, statusCode.equals("101"), merchantId);
                        }
                    } else {
                        logger.info("Liquiloans Set status failed response not contain status for merchant: {}", merchantId);
                    }
                } catch (RestClientException e) {
                    logger.error("RestClient Exception accrue in Liquiloans API calling", e);
                }
            }
        } catch (Exception e) {
            logger.error("Exception while fetching name from liquiloans for merchant: {}", merchantId);
            logger.error("Exception---", e);
        }
        return lendingPancardDao.save(new LendingPancard(merchantId, pancardNumber, name, apiResponse));
    }
}
