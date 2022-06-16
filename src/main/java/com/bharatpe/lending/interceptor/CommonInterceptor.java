package com.bharatpe.lending.interceptor;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.slave.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.slave.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.dto.Response;
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
import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class CommonInterceptor implements HandlerInterceptor {

    Logger logger = LoggerFactory.getLogger(CommonInterceptor.class);

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    InternalClientDaoSlave internalClientDaoSlave;

    @Autowired
    Environment env;

    @Autowired
    AesEncryptionUtil aesEncryptionUtil;

    @Autowired
    MerchantService merchantService;


    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        logger.info("Pre handle Interceptor of common Interceptor for {}",request);
        String hmac = request.getHeader("hash") != null ? request.getHeader("hash") : request.getParameter("hash");
        String clientName = request.getHeader("clientName");
        String token = request.getHeader("token");
        if(!StringUtils.isEmpty(token)) {
            return validateToken(token, request, response);
        } else {
            return validateHash(hmac, clientName, request, response);
        }
    }

    private boolean validateHash(String hmac, String clientName, HttpServletRequest request, HttpServletResponse response) throws Exception {
        try{
            if (StringUtils.isEmpty(hmac) || StringUtils.isEmpty(clientName)) {
                logger.info("hmac or mid Value is Blank or Empty for request {}", request);
                sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
                return false;
            }

            InternalClientSlave internalClient = internalClientDaoSlave.findByClientName(clientName);
            if(internalClient == null) {
                logger.error("Client not found {}", clientName);
                sendFailureResponse(response, ResponseCode.CLIENT_NOT_FOUND);
                return false;
            }

            String payload = getRequestBody(request);

            if(payload == null) {
                logger.error("Blank payload mid: {}, client: {}", clientName, clientName);
                sendFailureResponse(response, ResponseCode.INVALID_DATA);
                return false;
            }
            logger.info("secret key:{}", aesEncryptionUtil.decrypt(internalClient.getSecret()));
            String hash = lendingHmacCalculator.calculateHmac(payload, aesEncryptionUtil.decrypt(internalClient.getSecret()));
            logger.info("hash:{}", hash);
            if(lendingHmacCalculator.validateExternalGateway(payload, internalClient.getSecret(), hmac)) {
                logger.info("Hmac Verification successfull for hmac Value for the hmac {} and client {}", hmac, clientName);
                return true;
            }
            logger.info("Hmac Verification failed for hmac Value for the hmac {} and client {}", hmac, clientName);
        } catch (Throwable th){
            logger.error("Exception occurred in validating Hmac Value for the client {} and hmac {}", clientName, hmac, th);
        }
        sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
        return false;
    }

    private boolean validateToken(String token, HttpServletRequest request, HttpServletResponse response) throws Exception {
        try {
            if(StringUtils.isEmpty(token)) {
                InterceptorRequestWrapper interceptorRequestWrapper = new InterceptorRequestWrapper(request);
                logger.error("Token Value is Blank or Empty for request {}", interceptorRequestWrapper.getRequestURI());
                sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
                return false;
            } else {
                String normalized = Normalizer.normalize(token, Normalizer.Form.NFD);
                token = normalized.replaceAll("[^A-Za-z0-9-]", "");

//                List<TokenVerification> tokenDetails = tokenVerificationDao.fetchTokenDetails(token);
//
//                if(tokenDetails == null || tokenDetails.isEmpty()) {
//                    logger.error("No valid session found for the passed token, sending unauthorized response.");
//                    sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
//                    return false;
//                }
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
  
        Response apiResponse = new Response();
        apiResponse.setStatus(Status.ApiStatus.FAIL.toString());
        apiResponse.setResponseCode(responseCode);
        apiResponse.setResponseMessage(env.getProperty(responseCode));

        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(apiResponse));
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
            return lendingHmacCalculator.getPayload(paramMap);

        return null;
    }
}
