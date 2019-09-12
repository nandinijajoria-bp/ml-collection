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
import com.bharatpe.common.enums.Status;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.bharatpe.lending.objects.Response;

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
		
        String mobile = null;
        String merchantId = null;
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
        				mobile = tv.getMobile();
        				merchantId = tv.getMerchantId();
        			}

                	if(!StringUtils.isEmpty(mobile) && !StringUtils.isEmpty(merchantId)) {
            			status = true;
            		}else if(!StringUtils.isEmpty(mobile) && StringUtils.isEmpty(merchantId)) {
            			//fetch merchantId if not found in verify
            			Merchant merchant = merchantDao.findByMobile(mobile);
            			if(merchant != null) {
            				merchantId = Long.toString(merchant.getId());
            				if(merchantId != null) {
            					status = true;
            				}
            			}
            		}
            	}
        	}
        }catch(Throwable th) {
        	logger.error("Exception occurred in Pre handle Interceptor ValidateTokenInterceptor {}", th);
        }
        if(status == true) {
        	logger.info("ValidateToken Interceptor Success:  mobile : {} and merchantId : {}", mobile, merchantId);
        	request.setAttribute("mobile", mobile);
			request.setAttribute("merchantId", merchantId);
        }else {
        	sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
        }
        return status;
    }
	
	private void sendFailureResponse(HttpServletResponse response, String responseCode) throws Exception{
        Response apiResponse = new Response();
        
        ObjectMapper mapper = new ObjectMapper();
        response.setStatus(Integer.parseInt(responseCode));
        response.getWriter().write(mapper.writeValueAsString(apiResponse));
    }
}
