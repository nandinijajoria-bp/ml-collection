package com.bharatpe.lending.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.ExternalClientDao;
import com.bharatpe.common.entities.Agent;
import com.bharatpe.common.entities.ExternalClient;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.lending.dao.AgentDao;

@Component
public class ValidateLiquiloanTokenInterceptor implements HandlerInterceptor{
	
	Logger logger = LoggerFactory.getLogger(ValidateLiquiloanTokenInterceptor.class);
	
	@Autowired
	ExternalClientDao exteralClientDao;
	
	@Autowired
	AesEncryption aesEncryption;
	
	@Autowired
	AgentDao agentDao;
	
	private static final String CLIENT_STATUS = "ACTIVE";
	private static final String CLIENT_MODULE = "LENDING";
	
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		
		
		logger.info("Pre handle Interceptor of liquiloan Interceptor for {}",request);
        String token = request.getHeader("token") != null ? request.getHeader("token") : request.getParameter("token");
        

        try{
            if (StringUtils.isEmpty(token)) {
                logger.error("token is Blank or Empty for request {}", request);
                sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
                return false;
            }
            
            /**
             * @todo
             * get encrypt secret with decrypted token
             * check application cpvId 
             */
            
            String secret = aesEncryption.encrypt(token);
            if(secret  == null) {
            	logger.error("problam in getting secret with decrypted key");
                sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
                return false;
            }
            
            logger.info("encrypted secret : {}", secret);

            ExternalClient externalClient = exteralClientDao.findBySecretAndStatusAndModule(secret, CLIENT_STATUS, CLIENT_MODULE);
            if(externalClient == null) {
                logger.error("Client not found");
                sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
                return false;
            }
            
            logger.info("external client: {}", externalClient);
            
            if(externalClient.getMobile()!=null) {
            
	            Agent agent = agentDao.fetchByReferalCode(externalClient.getMobile());
	           
	            if(agent == null) {
	            	sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
	                return false;
	            }
	            
	            String ip = request.getHeader("True-Client-IP");
				if(StringUtils.isEmpty(ip)) {
					ip = request.getHeader("X-FORWARDED-FOR");
					if(StringUtils.isEmpty(ip)) {
						ip = request.getRemoteAddr();
					}
				}
	            
	            request.setAttribute("external_client_mobile", externalClient.getMobile());
	            request.setAttribute("clientIp", ip);
	            request.setAttribute("agent", agent);
	            
	            logger.info("Client found id:{}, ip:{}", externalClient.getId(), ip);
            
            }
            
            return true;

            
        } catch (Throwable th){
            logger.error("Exception occurred in validating token {}", token);
        }
        sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
        return false;
	}
	
	private void sendFailureResponse(HttpServletResponse response, String responseCode) throws Exception{
        response.setStatus(Integer.parseInt(responseCode));
        
    }

}
