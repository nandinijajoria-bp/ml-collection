package com.bharatpe.lending.interceptor;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.ExternalClientDao;
import com.bharatpe.common.entities.ExternalClient;
import com.bharatpe.common.utils.AesEncryption;
import com.bharatpe.common.utils.HmacCalculator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class ExternalClientHmacInterceptor implements HandlerInterceptor {
    Logger logger = LoggerFactory.getLogger(ExternalClientHmacInterceptor.class);

    @Autowired
    private HmacCalculator hmacCalculator;

    @Autowired
    private ExternalClientDao externalClientDao;

    @Autowired
    Environment env;

    @Autowired
    AesEncryption aesEncryption;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        logger.info("Pre handle Interceptor of Hmac Interceptor for {}",request);
        String hmac = request.getHeader("hash") != null ? request.getHeader("hash") : request.getParameter("hash");
        String clientName = request.getHeader("clientName");

        try{
            if (StringUtils.isEmpty(hmac) || StringUtils.isEmpty(clientName)) {
                logger.error("hmac or mid Value is Blank or Empty for request {}", request);
                sendFailureResponse(response);
                return false;
            }

            ExternalClient externalClient = externalClientDao.findByModuleAndClientName("LENDING", clientName);
            if(externalClient == null) {
                logger.error("Client not found {}", clientName);
                sendFailureResponse(response);
                return false;
            }

            String payload = getRequestBody(request);

            if(payload == null) {
                logger.error("Blank payload mid: {}, client: {}", clientName, clientName);
                sendFailureResponse(response);
                return false;
            }
            String secretKey = aesEncryption.decrypt(externalClient.getSecret());
            if(hmacCalculator.validateHmac(payload, secretKey, hmac)) {
                logger.info("Hmac Verification successfull for hmac Value for the hmac {} and client {}", hmac, clientName);
                return true;
            }
            logger.info("Hmac Verification failed for hmac Value for the hmac {} and client {}", hmac, clientName);
        } catch (Throwable th){
            logger.error("Exception occurred in validating Hmac Value for the client {} and hmac {}", clientName, hmac, th);
        }
        sendFailureResponse(response);
        return false;
    }

    private void sendFailureResponse(HttpServletResponse response) {
        response.setStatus(Integer.parseInt(ResponseCode.UNAUTHORIZED));
    }

    public String getRequestBody(HttpServletRequest request) throws Exception {
        InterceptorRequestWrapper interceptorRequestWrapper = (InterceptorRequestWrapper) request;
        Map<String, String> paramMap = null;
        if(HttpMethod.GET.toString().equals(interceptorRequestWrapper.getMethod())) {
            Map<String, String> map = new HashMap<>();
            Collections.list(interceptorRequestWrapper.getParameterNames()).forEach(paraName -> map.put(paraName, request.getParameter(paraName)));
            paramMap = map;
        } else {
            String requestBody = interceptorRequestWrapper.getBody();
            if(!StringUtils.isEmpty(requestBody)) {
                ObjectMapper mapper = new ObjectMapper();
                paramMap = mapper.readValue(requestBody, new TypeReference<Map<String, String>>(){});
            }
        }
        if(paramMap != null && !paramMap.isEmpty())
            return hmacCalculator.getPayload(paramMap);

        return null;
    }
}