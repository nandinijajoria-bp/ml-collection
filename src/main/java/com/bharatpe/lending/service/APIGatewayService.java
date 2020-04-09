package com.bharatpe.lending.service;

import com.bharatpe.common.dao.MerchantDao;
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
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

@Service
public class APIGatewayService {

    private static Logger logger = LoggerFactory.getLogger(APIGatewayService.class);

    private static String secret;

    private static String mid;

    @Value("${internal.merchant.id}")
    Long merchantId;
    
    @Value("${create.vpa.endpoint}")
    String createVPAEndpoint;

    @Autowired
    private MerchantDao merchantDao;

    @Autowired
    private HmacCalculator hmacCalculator;

    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private AesEncryption aesEncryption;
    
    ObjectMapper mapper = new ObjectMapper();
    
    @PostConstruct
    public void init() {
    	try { 
    		getSecret();
    		getMid();
    	} catch(Exception ex) {
    		logger.error("Exception while loading MID and Secret in APIGatewayService, Exception is {}", ex);
    	}
    }
 
    public Map createVPA(Double amount, String orderId) {
        logger.info("In Create VPA of APIGatewayService with orderId {}", orderId);
        try {
            Map requestParams = new HashMap<>();
            requestParams.put("amount", amount);
            requestParams.put("orderId", orderId);
            requestParams.put("mid", getMid());
            String hash = hmacCalculator.calculateHmac(hmacCalculator.getPayload(requestParams), getSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());
            headers.set("mid", getMid());
            headers.set("hash", hash);
            HttpEntity<Map> request = new HttpEntity<>(requestParams, headers);

            logger.info("createVPA internal request: {}", mapper.writeValueAsString(request));

            Map response = restTemplate.postForObject(createVPAEndpoint, request, Map.class);
            logger.info("Response received from create VPA API {}", mapper.writeValueAsString(response));
            return response;
        } catch (Exception ex) {
            logger.error("error processing txn for dynamic vpa, txn: {}, {}", orderId, ex);
        }
        return null;
    }

    private String getSecret() {
    	if(StringUtils.isEmpty(this.secret)) {
    		Optional<Merchant> merchantOptional = merchantDao.findById(merchantId);
    		if (merchantOptional.isPresent()) {
    			Merchant merchant = merchantOptional.get();
    			this.secret = aesEncryption.decrypt(merchant.getSecret());
    		}
    	}
        return secret;
    }

    private String getMid() {
    	if(StringUtils.isEmpty(this.mid)) {
    		Optional<Merchant> merchantOptional = merchantDao.findById(merchantId);
    		if (merchantOptional.isPresent()) {
    			Merchant merchant = merchantOptional.get();
    			this.mid = merchant.getMid();
    		}
    	}
        return mid;
    }
}