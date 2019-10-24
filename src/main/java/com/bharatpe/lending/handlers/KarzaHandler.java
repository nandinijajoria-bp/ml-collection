package com.bharatpe.lending.handlers;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.bharatpe.lending.constants.LendingConstants;

@Component
public class KarzaHandler {
	private Logger logger = LoggerFactory.getLogger(KarzaHandler.class);
	
	@Autowired
    RestTemplate restTemplate;
	
	public String curlKarzaKycAPI(String signedURL) {
		String response = null;
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		
	    body.add("url", signedURL);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        headers.setCacheControl(CacheControl.noCache());
        headers.set("x-karza-key", LendingConstants.X_KARZA_KEY);
        
        try {
        	Instant start = Instant.now();
        	HttpEntity<MultiValueMap<String, Object>> request  = new HttpEntity<>(body, headers);
        	
        	logger.info("Karza KYC request : {}", request);
            response = restTemplate.postForObject(LendingConstants.KARZA_KYC_URL, request, String.class);
            logger.info("Karza KYC response : {}", response);
            Instant end = Instant.now();
    		logger.info("Time Taken by Karza KYC API : {} miliseconds", Duration.between(start, end).toMillis());
        }catch(Exception e) {
        	e.printStackTrace();
        	logger.info("exception while Karza KYC API, signedURL : {}",signedURL);
        }
		
		return response;
	}
	
	public String curlKarzaPanAuthenticationAPI(String panNumber, String name, String dob) {
		String response = null;
		
		Map<String, String> paramMap = new LinkedHashMap<>();
		paramMap.put("pan", panNumber);
        paramMap.put("name", name);
        paramMap.put("dob", dob);
        paramMap.put("consent", "Y");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setCacheControl(CacheControl.noCache());
        headers.set("x-karza-key", LendingConstants.X_KARZA_KEY);
        
        try {
        	Instant start = Instant.now();
        	HttpEntity<Map<String, String>> request = new HttpEntity<>(paramMap, headers);
        	logger.info("Karza Pan Authentication request : {}", request);
            response = restTemplate.postForObject(LendingConstants.KARZA_PAN_AUTHENTICATION_URL, request, String.class);
            logger.info("Karza Pan Authentication response : {}", response);
            Instant end = Instant.now();
    		logger.info("Time Taken by Karza Pan Authentication API : {} miliseconds", Duration.between(start, end).toMillis());
        }catch(Exception e) {
        	e.printStackTrace();
        	logger.info("Exception while Karza Pan Authentication API");
        }
        
		return response;
	}
}
