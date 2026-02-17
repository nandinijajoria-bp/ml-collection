package com.bharatpe.lending.interceptor;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.common.enums.Status;
import com.bharatpe.lending.common.query.dao.InternalClientDaoSlave;
import com.bharatpe.lending.common.query.entity.InternalClientSlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.dto.Response;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
public class DataHmacInterceptor implements HandlerInterceptor {

    @Autowired
    private LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    private InternalClientDaoSlave internalClientDaoSlave;

    @Autowired
    private Environment env;

    @Autowired
    private AesEncryptionUtil aesEncryptionUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("[DataHmacInterceptor] Pre-handle triggered for {}", request.getRequestURI());

        String hmac = request.getHeader("hash");
        String clientName = request.getHeader("clientName");

        if (StringUtils.isEmpty(hmac) || StringUtils.isEmpty(clientName)) {
            log.warn("[DataHmacInterceptor] Missing HMAC or clientName for request {}", request.getRequestURI());
            sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
            return false;
        }

        try {
            InternalClientSlave internalClient = internalClientDaoSlave.findByClientName(clientName);
            if (internalClient == null) {
                log.error("[DataHmacInterceptor] Client not found: {}", clientName);
                sendFailureResponse(response, ResponseCode.CLIENT_NOT_FOUND);
                return false;
            }

            String payload = getFlattenedRequestBody(request);
            if (payload == null) {
                log.error("[DataHmacInterceptor] Blank payload for client: {}", clientName);
                sendFailureResponse(response, ResponseCode.INVALID_DATA);
                return false;
            }

            String decryptedKey = aesEncryptionUtil.decrypt(internalClient.getSecret());
            log.debug("[DataHmacInterceptor] Decrypted Secret Key: {}", decryptedKey);

            String computedHash = lendingHmacCalculator.calculateHmac(payload, decryptedKey);
            log.debug("[DataHmacInterceptor] Computed HMAC: {}", computedHash);

            if (computedHash.equalsIgnoreCase(hmac)) {
                log.info("[DataHmacInterceptor] HMAC verification successful for client {}", clientName);
                return true;
            } else {
                log.error("[DataHmacInterceptor] HMAC verification failed. Expected {}, got {}", computedHash, hmac);
            }
        } catch (Throwable th) {
            log.error("[DataHmacInterceptor] Exception occurred during HMAC validation for client {}", clientName, th);
        }

        sendFailureResponse(response, ResponseCode.UNAUTHORIZED);
        return false;
    }

    private void sendFailureResponse(HttpServletResponse response, String responseCode) throws Exception {
        Response apiResponse = new Response();
        apiResponse.setStatus(Status.ApiStatus.FAIL.toString());
        apiResponse.setResponseCode(responseCode);
        apiResponse.setResponseMessage(env.getProperty(responseCode));

        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(apiResponse));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
    }

    private String getFlattenedRequestBody(HttpServletRequest request) throws Exception {
        InterceptorRequestWrapper wrapper = (InterceptorRequestWrapper) request;
        ObjectMapper mapper = new ObjectMapper();

        // Handle GET and POST both
        if (HttpMethod.GET.matches(wrapper.getMethod())) {
            Map<String, String> params = new HashMap<>();
            Collections.list(wrapper.getParameterNames()).forEach(
                    name -> params.put(name, wrapper.getParameter(name))
            );
            return lendingHmacCalculator.getPayload(params);
        } else {
            String body = wrapper.getBody();
            if (!StringUtils.isEmpty(body)) {
                Map<String, Object> nestedMap = mapper.readValue(body, new TypeReference<Map<String, Object>>() {});
                return lendingHmacCalculator.getNestedPayload(nestedMap);
            }
        }
        return null;
    }
}
