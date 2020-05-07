package com.bharatpe.lending.interceptor;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.dao.InternalClientDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.entities.InternalClient;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.enums.Status;
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
public class HmacForMIDAndInternalClientInterceptor implements HandlerInterceptor {
    Logger logger = LoggerFactory.getLogger(HmacForMIDAndInternalClientInterceptor.class);

    @Autowired
    private HmacCalculator hmacCalculator;

    @Autowired
    private MerchantDao merchantDao;

    @Autowired
    private InternalClientDao internalClientDao;

    @Autowired
    Environment env;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        logger.info("Pre handle Interceptor of Hmac Interceptor for {}", request);
        String hmac = request.getHeader("hash") != null ? request.getHeader("hash") : request.getParameter("hash");
        String mid = request.getHeader("mid") != null ? request.getHeader("mid") : request.getParameter("mid");
        String clientName = request.getHeader("clientName");
        logger.info("mid: {}, hash: {}, clientName: {}", mid, hmac, clientName);

        try {
            if (StringUtils.isEmpty(hmac) || (StringUtils.isEmpty(mid) && StringUtils.isEmpty(clientName))) {
                logger.error("hmac or (mid and clientName) Value is Blank or Empty for request {}", request);
                sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
                return false;
            }

            String payload = getRequestBody(request);
            
            if (payload == null) {
            	logger.error("Blank payload mid: {}, client: {}", mid, clientName);
            	sendFailureResponse(response, ResponseCode.INVALID_DATA);
            	return false;
            }
            
            Merchant merchant = null;
            InternalClient internalClient = null;
            
            if(StringUtils.isEmpty(clientName)) {
            	merchant = merchantDao.findByMid(mid);
            	if (merchant == null || Status.MerchantStatus.INACTIVE.toString().equalsIgnoreCase(merchant.getStatus())) {
            		logger.error("Merchant not found (}", mid);
            		sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
            		return false;
            	}
            	
            	request.setAttribute("merchant", merchant);
            } else {
            	internalClient = internalClientDao.findByClientName(clientName);
            	if (internalClient == null) {
            		logger.error("Client not found {}", clientName);
            		sendFailureResponse(response, ResponseCode.CLIENT_NOT_FOUND);
            		return false;
            	}
            }
            
            if ((internalClient != null && hmacCalculator.validateClient(payload, internalClient, hmac)) || (internalClient == null && hmacCalculator.validateHmac(payload, merchant, hmac))) {
                logger.info("Hmac Verification successfull for hmac Value for the hmac {} and mid {}", hmac, mid);
                return true;
            }
            logger.info("Hmac Verification failed for hmac Value for the hmac {} and mid {}", hmac, mid);
        } catch (Throwable th) {
            logger.error("Exception occurred in validating Hmac Value for the ,id {} and hmac {}", mid, hmac, th);
        }
        sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
        return false;
    }

    private void sendFailureResponse(HttpServletResponse response, String responseCode) throws Exception {
        response.setStatus(Integer.parseInt(responseCode));
    }


    public String getRequestBody(HttpServletRequest request) throws Exception {
        InterceptorRequestWrapper interceptorRequestWrapper = (InterceptorRequestWrapper) request;
        Map<String, String> paramMap = null;
        if (HttpMethod.GET.toString().equals(interceptorRequestWrapper.getMethod())) {
            Map<String, String> map = new HashMap<>();
            Collections.list(interceptorRequestWrapper.getParameterNames()).forEach(paraName -> map.put(paraName, request.getParameter(paraName)));
            paramMap = map;
        } else {
            String requestBody = interceptorRequestWrapper.getBody();
            if (!StringUtils.isEmpty(requestBody)) {
                ObjectMapper mapper = new ObjectMapper();
                paramMap = mapper.readValue(requestBody, new TypeReference<Map<String, String>>() {
                });
            }
        }
        if (paramMap != null && !paramMap.isEmpty())
            return hmacCalculator.getPayload(paramMap);

        return null;
    }
}
