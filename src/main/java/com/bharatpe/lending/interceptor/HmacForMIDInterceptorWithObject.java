package com.bharatpe.lending.interceptor;

import com.amazonaws.HttpMethod;
import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class HmacForMIDInterceptorWithObject implements HandlerInterceptor {

    Logger logger = LoggerFactory.getLogger(HmacForMIDInterceptorWithObject.class);

    @Autowired
    HmacCalculator hmacCalculator;

//    @Autowired
//    MerchantDao merchantDao;
    @Autowired
    MerchantService merchantService;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        logger.info("Pre handle Interceptor of Hmac Interceptor for {}", request);
        String hmac = request.getHeader("hash") != null ? request.getHeader("hash") : request.getParameter("hash");
        String mid = request.getHeader("mid") != null ? request.getHeader("mid") : request.getParameter("mid");
        logger.info("mid: {}, hash: {}", mid, hmac);

        try {
            if (StringUtils.isEmpty(hmac) || StringUtils.isEmpty(mid)) {
                logger.error("hmac or mid Value is Blank or Empty for request {}", request);
                sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
                return false;
            }

            String payload = getRequestBody(request);

            if (payload == null) {
                logger.error("Blank payload mid: {}", mid);
                sendFailureResponse(response, ResponseCode.INVALID_DATA);
                return false;
            }

//            Merchant merchant = merchantDao.findByMid(mid);
            Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetailsByMid(mid);
            if (basicDetailsDto == null || Status.MerchantStatus.INACTIVE.toString().equalsIgnoreCase(basicDetailsDto.get().getStatus())) {
                logger.error("Merchant not found (}", mid);
                sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
                return false;
            }

            request.setAttribute("merchant", basicDetailsDto.get());

            if (hmacCalculator.validateExternalGateway(payload, basicDetailsDto.get().getSecret(), hmac)) {
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
        Map<String, Object> paramMap = null;
        if (HttpMethod.GET.toString().equals(interceptorRequestWrapper.getMethod())) {
            Map<String, Object> map = new HashMap<>();
            Collections.list(interceptorRequestWrapper.getParameterNames()).forEach(paraName -> map.put(paraName, request.getParameter(paraName)));
            paramMap = map;
        } else {
            String requestBody = interceptorRequestWrapper.getBody();
            if (!StringUtils.isEmpty(requestBody)) {
                ObjectMapper mapper = new ObjectMapper();
                paramMap = mapper.readValue(requestBody, new TypeReference<Map<String, Object>>() {
                });
            }
        }
        if (paramMap != null && !paramMap.isEmpty())
            return hmacCalculator.getObjectPayload(paramMap);

        return null;
    }
}
