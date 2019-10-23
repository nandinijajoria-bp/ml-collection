package com.bharatpe.lending.interceptor;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.bharatpe.lending.dao.TokenVerificationDao;
import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.TokenVerification;

@Component
public class ValidateTokenInterceptor implements HandlerInterceptor {

	Logger logger = LoggerFactory.getLogger(ValidateTokenInterceptor.class);
	
	@Autowired
	private TokenVerificationDao tokenVerificationDao;
	
	@Autowired
    private MerchantDao merchantDao;
	
	@Autowired
	Environment env;
	
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		logger.info("Pre handle Interceptor of ValidateTokenInterceptor for {}",request);
		
        Merchant merchant = null;
        Boolean status = false;

        //fetch token from header
        String token = request.getHeader("token");
        
        try {
        	if(StringUtils.isEmpty(token)) {
	            logger.error("Token Value is Blank or Empty for request {}", request);
        	}else {
        		//Make token alphanumeric
                String normalized = Normalizer.normalize(token, Form.NFD);
                token = normalized.replaceAll("[^A-Za-z0-9]", "");
                
        		//fetch token details from verify
        		List<TokenVerification> tokenDetails = tokenVerificationDao.fetchTokenDetails(token);
        		
        		if(tokenDetails != null && !tokenDetails.isEmpty()) {
        			
        			for(TokenVerification tv : tokenDetails) {
        				merchant = tv.getMerchant();
        			}

                	if(merchant != null) {
            			status = true;
            		}
            	}
        	}
        }catch(Throwable th) {
        	logger.error("Exception occurred in Pre handle Interceptor ValidateTokenInterceptor {}", th);
        }
        if(status == true) {
        	logger.info("ValidateToken Interceptor Success: merchant : {}", merchant);
			request.setAttribute("merchant", merchant);
        }else {
        	sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
        }
        return status;
    }
	
	private void sendFailureResponse(HttpServletResponse response, String responseCode) throws Exception{
        response.setStatus(Integer.parseInt(responseCode));
    }
}
