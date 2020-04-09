package com.bharatpe.lending.service;

import com.bharatpe.common.dao.InternalClientDao;
import com.bharatpe.common.entities.InternalClient;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

@Service
public class APIGatewayService {

    private static Logger logger = LoggerFactory.getLogger(APIGatewayService.class);

    @Value("${create.vpa.endpoint}")
    String createVPAEndpoint;

    @Autowired
    private HmacCalculator hmacCalculator;

    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private AesEncryption aesEncryption;
    
    @Autowired
    private InternalClientDao inernalClientDao;
    
    private static String clientSecret;
    
    ObjectMapper mapper = new ObjectMapper();
    
    @PostConstruct
    public void init() {
    	try { 
    		getSecret();
    	} catch(Exception ex) {
    		logger.error("Exception while loading Secret in APIGatewayService, Exception is {}", ex);
    	}
    }
 
    public Map createVPA(Merchant merchant, Double amount, String orderId) {
        logger.info("In Create VPA of APIGatewayService for merchnat id {}", merchant.getId());
        try {
            Map requestParams = new HashMap<>();
            requestParams.put("amount", amount);
            requestParams.put("orderId", orderId);
            requestParams.put("mid", merchant.getMid());
            requestParams.put("orderDescription", "Loan Repayment");
            requestParams.put("txnNote", "Loan Repayment");
            requestParams.put("type", "LOAN_PAYMENT");
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), getSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("clientName", "LENDING");
            headers.set("hash", hash);
            headers.set("mid", merchant.getMid());
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
    	if(StringUtils.isEmpty(this.clientSecret)) {
    		InternalClient client = inernalClientDao.findByClientName("LENDING");
    		this.clientSecret = aesEncryption.decrypt(client.getSecret());
    	} 
    	return this.clientSecret;
    }
}