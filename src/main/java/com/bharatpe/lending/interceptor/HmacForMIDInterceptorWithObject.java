package com.bharatpe.lending.interceptor;

import com.amazonaws.HttpMethod;
import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.enums.Status;
import com.bharatpe.lending.common.query.dao.LendingPgMidConfigSlaveDao;
import com.bharatpe.lending.common.query.entity.LendingPgMidConfigSlave;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

@Component
public class HmacForMIDInterceptorWithObject implements HandlerInterceptor {

    Logger logger = LoggerFactory.getLogger(HmacForMIDInterceptorWithObject.class);

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

//    @Autowired
//    MerchantDao merchantDao;
    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingPgMidConfigSlaveDao lendingPgMidConfigSlaveDao;

    @Autowired
    LoanUtil loanUtil;

    List<String> pgMids = Arrays.asList("LENTRIgUqSD3gV0xCW6gCijBLsZU9eU2", "LENLDCmzpVvR90yJCzKJWuYgWMvpVPZg", "LENLLzV9L6C0FcejvkqDzVbZvQpBUQY4",
            "LENHIN7nhdRBCGrskaRyHQSOYrN3paPh", "LENMAM1T78fJ3PBkYPfOvHAOGdLuiopm", "LENLLOBmSHeoNlJ3lfd7M5SDu2HJXYwY", "LENLLOFr0ZxlHGwodR5bklVHWkTCHCk0",
            "LENABFLD34mSwvv1a9hKF3L3uzDPqyWt", "LENPRIxLIazvVGKRK5dngMzTWsvhFsWp", "LENMUTL1HAWyPzvF7g7HQrz73oa3tiwQ",  "LENCAPcZlZOB8o7GVzxHHRJZSKw3Rono", "LENPAYU8lfGGqVRwiurdRuFe48YPAKNA", "LENCSyakobsdygSUxWM7SdGF7UvLgG4W",
            "LENSMICC8XQH2MMVjh05l1ioNz2Lj3jo");


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
            String secret;
            Optional<BasicDetailsDto> basicDetailsDto = null;
            LendingPgMidConfigSlave pgMidConfig = null;
            if (!pgMids.contains(mid)) {
                basicDetailsDto = merchantService.fetchMerchantBasicDetailsByMid(mid);
                if (basicDetailsDto == null || Status.MerchantStatus.INACTIVE.toString().equalsIgnoreCase(basicDetailsDto.get().getStatus())) {
                    logger.error("Merchant not found (}", mid);
                    sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
                    return false;
                }
                secret = basicDetailsDto.get().getSecret();
                logger.info("secret in old mid flow: {}",secret);
                request.setAttribute("merchant", basicDetailsDto.get());
            } else {
                pgMidConfig = lendingPgMidConfigSlaveDao.findTop1ByMidAndStatus(mid, "ACTIVE");
                logger.info("pg mid config: {}", pgMidConfig);
                secret = pgMidConfig.getSecret();
            }

            if (ObjectUtils.isEmpty(secret)) {
                logger.info("secret is empty");
                sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
                return false;
            }

            if (lendingHmacCalculator.validateExternalGateway(payload, secret, hmac)) {
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
            return lendingHmacCalculator.getObjectPayload(paramMap);

        return null;
    }
}
