package com.bharatpe.lending.interceptor;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@Component
public class LiquiloanInterceptor implements HandlerInterceptor {

    Logger logger = LoggerFactory.getLogger(LiquiloanInterceptor.class);

    @Autowired
    AesEncryptionUtil aesEncryptionUtil;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Value("${liquiloan.secret}")
    private String secretKey;

    @Value("${liquiloan.nbfc.secret}")
    private String nbfcSecretKey;

    @Value("${liquiloan.p2p.secret}")
    private String p2pSecretKey;

    @Value("${liquiloan.p2pof.secret}")
    private String p2pOfSecretKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        logger.info("Pre handle Interceptor of liquiloan approveLoan for request:{}",request);
        try {
            boolean isSettlementApi = false;
            InterceptorRequestWrapper interceptorRequestWrapper = new InterceptorRequestWrapper(request);
            if (interceptorRequestWrapper.getRequestURI() != null && interceptorRequestWrapper.getRequestURI().equalsIgnoreCase("/lending/liquiloan/settlement")) {
                isSettlementApi = true;
            }
            if (StringUtils.isEmpty(request)) {
                logger.error("Empty request for liquiloan approveLoan");
                response.setStatus(Integer.parseInt(ResponseCode.UNAUTHORIZED));
                return false;
            }
            String checksumString = getChecksumString(request, isSettlementApi);
            String checksum = lendingHmacCalculator.calculateHMACHexEncoded(checksumString, getSecretFromUri(interceptorRequestWrapper.getRequestURI()));
            if (validateChecksum(request, checksum)) {
                return true;
            } else {
                logger.info("Invalid checksum for checksumString:{}", checksumString);
                logger.info("Valid checksum should be:{}", checksum);
            }
        } catch (Exception e) {
            logger.error("Exception validating liquiloan approveLoan request:{}", request);
        }
        logger.info("Validation Failed");
        response.setStatus(Integer.parseInt(ResponseCode.UNAUTHORIZED));
        return false;
    }

    private boolean validateChecksum(HttpServletRequest request, String checksum) throws IOException {
        InterceptorRequestWrapper interceptorRequestWrapper = (InterceptorRequestWrapper) request;
        Map<String, Object> paramMap = null;
        String requestBody = interceptorRequestWrapper.getBody();
        if(!StringUtils.isEmpty(requestBody)) {
            paramMap = objectMapper.readValue(requestBody, new TypeReference<Map<String, Object>>(){});
        }
        if (paramMap != null) {
            for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                if (entry.getKey().equalsIgnoreCase("checksum") && paramMap.get(entry.getKey()) != null && paramMap.get(entry.getKey()).equals(checksum)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getChecksumString(HttpServletRequest request, boolean isSettlementApi) throws IOException {
        InterceptorRequestWrapper interceptorRequestWrapper = (InterceptorRequestWrapper) request;
        Map<String, Object> paramMap = null;
        String requestBody = interceptorRequestWrapper.getBody();
        objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        objectMapper.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        if(!StringUtils.isEmpty(requestBody)) {
            paramMap = objectMapper.readValue(requestBody, new TypeReference<Map<String, Object>>(){});
        }
        if (paramMap != null) {
            paramMap.remove("checksum");
            paramMap.remove("Checksum");
            for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                if (entry.getValue() == null) entry.setValue("");
                if(entry.getValue() instanceof List || entry.getValue() instanceof Map) {
                    entry.setValue(objectMapper.writeValueAsString(entry.getValue()));
                }
            }
            if (isSettlementApi) {
                return paramMap.get("date") + "|" + paramMap.get("total_amount") + "|" + paramMap.get("utr_number") + "|" + paramMap.get("transfer_date");
            } else {
                return paramMap.get("loan_id") + "|" + paramMap.get("amount") + "|" + paramMap.get("urn") + "|" + paramMap.get("status") + "|" + paramMap.get("timestamp");
            }
        }
        return "";
    }

    private String getSecretFromUri(String uri) {
        switch(uri){
            case "/lending/liquiloan/nbfc/postPayout/callback":
                return this.nbfcSecretKey;
            case "/lending/liquiloan/p2p/postPayout/callback":
                return this.p2pSecretKey;
            case "/lending/liquiloan/p2p_of/postPayout/callback":
                return this.p2pOfSecretKey;
            default:
                return this.secretKey;
        }
    }
}
