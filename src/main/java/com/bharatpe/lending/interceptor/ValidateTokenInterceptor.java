package com.bharatpe.lending.interceptor;

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException.Forbidden;
import org.springframework.web.client.HttpClientErrorException.Unauthorized;
import org.springframework.web.servlet.HandlerInterceptor;

import com.bharatpe.common.constants.ResponseCode;

@Component
public class ValidateTokenInterceptor implements HandlerInterceptor {

	Logger logger = LoggerFactory.getLogger(ValidateTokenInterceptor.class);
	
	@Autowired
	Environment env;

	@Autowired
	MerchantService merchantService;

    @Value("${token.failure.screen.rollout.percent:0}")
    private Integer tokenFailureScreenPercent;
	
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		logger.info("Pre handle Interceptor of ValidateTokenInterceptor for {}",request);
        String token = request.getHeader("token");

		UUID requestId = UUID.randomUUID();
		MDC.put("requestId", requestId.toString());
        
        try {
        	if(StringUtils.isEmpty(token)) {
				InterceptorRequestWrapper interceptorRequestWrapper = new InterceptorRequestWrapper(request);
                long millis = System.currentTimeMillis();
                if((millis % 100) < tokenFailureScreenPercent) {
                    logger.error("Token Value is Blank or Empty for request - Error 511 : {}", interceptorRequestWrapper.getRequestURI());
                    sendFailureResponse(response, String.valueOf(HttpStatus.NETWORK_AUTHENTICATION_REQUIRED.value()));
                } else {
                    logger.error("Token Value is Blank or Empty for request {}", interceptorRequestWrapper.getRequestURI());
                    sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
                }
    			return false;
        	} else {

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
        } catch (Unauthorized e){
            logger.error("Unauthorized Exception in ValidateTokenInterceptor preHandle: {}", e.getMessage(), e);
            sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
        } catch (Forbidden e){
            logger.error("Forbidden Exception in ValidateTokenInterceptor preHandle: {}", e.getMessage(), e);
            sendFailureResponse(response, String.valueOf(HttpStatus.FORBIDDEN.value()));
        } catch (Throwable e) {
            long millis = System.currentTimeMillis();
            if((millis % 100) < tokenFailureScreenPercent) {
                logger.error("Exception in ValidateTokenInterceptor preHandle - Error 511, token : {}, {}", token, e.getMessage(), e);
                sendFailureResponse(response, String.valueOf(HttpStatus.NETWORK_AUTHENTICATION_REQUIRED.value()));
            } else {
                logger.error("Exception in ValidateTokenInterceptor preHandle: {}", e.getMessage(), e);
                sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
            }
        }
        return false;
    }
	
	private void sendFailureResponse(HttpServletResponse response, String responseCode) throws Exception{
        response.setStatus(Integer.parseInt(responseCode));
    }
}
