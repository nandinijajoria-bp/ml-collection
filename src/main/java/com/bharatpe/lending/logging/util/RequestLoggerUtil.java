package com.bharatpe.lending.logging.util;

import com.bharatpe.lending.logging.wrapper.SpringRequestWrapper;
import com.bharatpe.lending.logging.wrapper.SpringResponseWrapper;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;


@Component
public class RequestLoggerUtil {

    @Value("${logging.headers:false}")
    boolean logHeaders;

    public static final String REQUEST_ID_HEADER_NAME = "X-Request-ID";
    public static final String CORRELATION_ID_HEADER_NAME = "X-Correlation-ID";

    public void generateAndSetMDC(HttpServletRequest request) {
        MDC.clear();
        String requestId = request.getHeader(REQUEST_ID_HEADER_NAME);
        if (requestId == null)
            requestId = UUID.randomUUID().toString().replaceAll("-", "");
        MDC.put(REQUEST_ID_HEADER_NAME, requestId);

        String correlationId = request.getHeader(CORRELATION_ID_HEADER_NAME);
        if (correlationId == null)
            correlationId = UUID.randomUUID().toString().replaceAll("-", "");
        MDC.put(CORRELATION_ID_HEADER_NAME, correlationId);
    }

    public void logResponse(Logger logger, SpringResponseWrapper response, Long startTime, Long endTime, int status) throws IOException {
        if (logHeaders)
            logger.info("Response({} ms): status={}, payload={}, headers={}, audit={}", (endTime - startTime), status,
                    IOUtils.toString(response.getContentAsByteArray(), response.getCharacterEncoding()), response.getAllHeaders(), true);
        logger.info("Response({} ms): status={}, payload={}, audit={}", (endTime - startTime), status,
                IOUtils.toString(response.getContentAsByteArray(), response.getCharacterEncoding()), true);
    }

    public void logRequest(Logger logger, SpringRequestWrapper request) throws IOException {
        if (logHeaders)
            logger.info("Request: method={}, uri={}, payload={}, headers={}, audit={}", request.getMethod(), request.getRequestURI(),
                    IOUtils.toString(request.getInputStream(), request.getCharacterEncoding()), request.getAllHeaders(), true);
        logger.info("Request: method={}, uri={}, payload={}, audit={}", request.getMethod(), request.getRequestURI(),
                IOUtils.toString(request.getInputStream(), request.getCharacterEncoding()), true);
    }

}
