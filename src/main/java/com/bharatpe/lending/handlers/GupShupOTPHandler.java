package com.bharatpe.lending.handlers;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.bharatpe.lending.constant.LendingConstants;

@Component
public class GupShupOTPHandler {
	private Logger logger = LoggerFactory.getLogger(GupShupOTPHandler.class);
	
	@Autowired
	RestTemplate restTemplate;

	public Boolean sendOTP(String mobile, String message) {
		Boolean responseFlag = false;

		Map<String, String> urlParams = new LinkedHashMap<>();
		urlParams.put("userId", LendingConstants.GUPSHUP_OTP_API_USERID);
		urlParams.put("password", LendingConstants.GUPSHUP_OTP_API_PASSWORD);
		urlParams.put("method", LendingConstants.GUPSHUP_SEND_OTP_METHOD);
		urlParams.put("version", LendingConstants.GUPSHUP_API_VERSION);
		urlParams.put("phoneNumber", mobile);
		urlParams.put("message", message);
		urlParams.put("format", "text");
		urlParams.put("otpCodeLength", "4");
		urlParams.put("otpCodeType", "NUMERIC");
		
		String url = LendingConstants.GUPSHUP_SMS_SERVICE_URL + "?userid={userId}&password={password}&method={method}&v={version}&phone_no={phoneNumber}&msg={message}&format={format}&otpCodeLength={otpCodeLength}&otpCodeType={otpCodeType}";
        
        try {
        	Instant start = Instant.now();
        	logger.info("GUPSHUP sendOTP requestURL : {}", url);
            String response = restTemplate.getForObject(url, String.class, urlParams);
            
            response = response.replaceAll("\\s","");
			String[] responseSplit = response.split("\\|");
			
			if(responseSplit[0].equals("success") == true) {
				responseFlag = true;
			}
            
            Instant end = Instant.now();
            logger.info("GUPSHUP sendOTP response : {}", response);
    		logger.info("Time Taken by GUPSHUP sendOTP API : {} miliseconds", Duration.between(start, end).toMillis());
        }catch(Exception e) {
        	e.printStackTrace();
        	logger.info("exception while GUPSHUP sendOTP API, mobile : {}",mobile);
        }
        return responseFlag;
	}
	
	public Boolean verifyOTP(String mobile, String otp) {
		Boolean responseFlag = false;
		
		Map<String, String> urlParams = new LinkedHashMap<>();
		urlParams.put("userId", LendingConstants.GUPSHUP_OTP_API_USERID);
		urlParams.put("password", LendingConstants.GUPSHUP_OTP_API_PASSWORD);
		urlParams.put("method", LendingConstants.GUPSHUP_VERIFY_OTP_METHOD);
		urlParams.put("version", LendingConstants.GUPSHUP_API_VERSION);
		urlParams.put("phoneNumber", mobile);
		urlParams.put("otpCode", otp);
		
		String url = LendingConstants.GUPSHUP_SMS_SERVICE_URL + "?userid={userId}&password={password}&method={method}&v={version}&phone_no={phoneNumber}&otp_code={otpCode}";
        
        try {
        	Instant start = Instant.now();
        	logger.info("GUPSHUP verifyOTP requestURL : {}", url);
            String response = restTemplate.getForObject(url, String.class, urlParams);
            
            response = response.replaceAll("\\s","");
			String[] responseSplit = response.split("\\|");
			
			if(responseSplit[0].equals("success") == true) {
				responseFlag = true;
			}
            
            Instant end = Instant.now();
            logger.info("GUPSHUP verifyOTP response : {}", response);
    		logger.info("Time Taken by GUPSHUP verifyOTP API : {} miliseconds", Duration.between(start, end).toMillis());
        }catch(Exception e) {
        	e.printStackTrace();
        	logger.info("exception while GUPSHUP verifyOTP API, mobile : {}",mobile);
        }
        return responseFlag;
	}

}
