package com.bharatpe.lending.interceptor;

import java.text.Normalizer;
import java.text.Normalizer.Form;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.bharatpe.lending.dao.TokenVerificationDao;
import com.bharatpe.common.constants.ResponseCode;

@Component
public class ValidateTokenInterceptor implements HandlerInterceptor {

	Logger logger = LoggerFactory.getLogger(ValidateTokenInterceptor.class);
	
	@Autowired
	private TokenVerificationDao tokenVerificationDao;
	
	@Autowired
	Environment env;

	@Autowired
	MerchantService merchantService;
	
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		logger.info("Pre handle Interceptor of ValidateTokenInterceptor for {}",request);
        String token = request.getHeader("token");
        
        try {
        	if(StringUtils.isEmpty(token)) {
				InterceptorRequestWrapper interceptorRequestWrapper = new InterceptorRequestWrapper(request);
	            logger.error("Token Value is Blank or Empty for request {}", interceptorRequestWrapper.getRequestURI());
	            sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
    			return false;
        	} else {
                String normalized = Normalizer.normalize(token, Form.NFD);
                token = normalized.replaceAll("[^A-Za-z0-9-]", "");
//
//        		List<TokenVerification> tokenDetails = tokenVerificationDao.fetchTokenDetails(token);
//
//        		if(tokenDetails == null || tokenDetails.isEmpty()) {
//        			logger.error("No valid session found for the passed token, sending unauthorized response.");
//        			sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
//        			return false;
//        		}
// 				Merchant merchant = tokenDetails.get(0).getMerchant();

				BasicDetailsDto merchant = merchantService.fetchMerchantDetails(token);
        		if(merchant != null) {
        			String ip = request.getHeader("True-Client-IP");
        			if(StringUtils.isEmpty(ip)) {
						ip = request.getHeader("X-FORWARDED-FOR");
						if(StringUtils.isEmpty(ip)) {
							ip = request.getRemoteAddr();
						}
					}
					logger.info("Merchant found id:{}, ip:{}", merchant.getId(), ip);

					request.setAttribute("merchant", merchant);
					request.setAttribute("clientIp", ip);
					return true;
				}
        	}
        } catch(Throwable th) {
        	logger.error("Exception occurred in Pre handle Interceptor ValidateTokenInterceptor {}", th);
        }
        sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
        return false;
    }
	
	private void sendFailureResponse(HttpServletResponse response, String responseCode) throws Exception{
        response.setStatus(Integer.parseInt(responseCode));
    }
}
