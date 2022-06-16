package com.bharatpe.lending.interceptor;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.slave.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.slave.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class HmacForMIDAndInternalClientInterceptor implements HandlerInterceptor {
    Logger logger = LoggerFactory.getLogger(HmacForMIDAndInternalClientInterceptor.class);

    @Autowired
    private LendingHmacCalculator lendingHmacCalculator;

//    @Autowired
//    private MerchantDao merchantDao;

    @Autowired
    private InternalClientDaoSlave internalClientDaoSlave;

    @Autowired
    Environment env;
    @Autowired
    MerchantService merchantService;

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
            
//            Merchant merchant = null;
            InternalClientSlave internalClient = null;
            Optional<BasicDetailsDto> basicDetailsDto = Optional.empty();

            if (!ObjectUtils.isEmpty(mid))
                basicDetailsDto  = merchantService.fetchMerchantBasicDetailsByMid(mid);

            if(StringUtils.isEmpty(clientName)) {
//            	merchant = merchantDao.findByMid(mid);
            	if (basicDetailsDto == null || Status.MerchantStatus.INACTIVE.toString().equalsIgnoreCase(basicDetailsDto.get().getStatus())) {
            		logger.error("Merchant not found (}", mid);
            		sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
            		return false;
            	}
            	
            	request.setAttribute("merchant", basicDetailsDto.get());
            } else {
            	internalClient = internalClientDaoSlave.findByClientName(clientName);
            	if (internalClient == null) {
            		logger.error("Client not found {}", clientName);
            		sendFailureResponse(response, ResponseCode.CLIENT_NOT_FOUND);
            		return false;
            	}
            }
            
            if ((internalClient != null && lendingHmacCalculator.validateExternalGateway(payload, internalClient.getSecret(), hmac)) || (internalClient == null && lendingHmacCalculator.validateExternalGateway(payload, basicDetailsDto.get().getSecret(), hmac))) {
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
            return lendingHmacCalculator.getPayload(paramMap);

        return null;
    }
}
