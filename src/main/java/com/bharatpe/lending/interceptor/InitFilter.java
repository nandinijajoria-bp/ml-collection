package com.bharatpe.lending.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Component
@Slf4j
public class InitFilter implements Filter {


    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;

        Random rand = new Random();
        int rand_int1 = rand.nextInt(10000);
        int rand_int2 = rand.nextInt(10000);
        UUID uniqueId = UUID.randomUUID();
        String loggerCode = String.valueOf(rand_int1) + "-" + String.valueOf(rand_int2);

        MDC.put("requestId",
                ObjectUtils.isEmpty(httpServletRequest.getHeader("requestId")) ? uniqueId.toString() : httpServletRequest.getHeader("requestId"));
        MDC.put("loggerCode",
                ObjectUtils.isEmpty(httpServletRequest.getHeader("loggerCode")) ? loggerCode : httpServletRequest.getHeader("loggerCode"));

        log.info("Request IP address is {}", servletRequest.getRemoteAddr());
        log.info("Request content type is {}", servletRequest.getContentType());


        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(
                httpServletResponse
        );

        try {
            if (servletRequest != null && servletRequest.getContentType() != null &&
                    servletRequest.getContentType().equals(MediaType.APPLICATION_JSON_VALUE)) {
                InterceptorRequestWrapper interceptorRequestWrapper = new InterceptorRequestWrapper(
                        httpServletRequest);
                filterChain.doFilter(interceptorRequestWrapper, responseWrapper);
            } else {
                filterChain.doFilter(servletRequest, responseWrapper);
            }
        } finally {
            responseWrapper.setHeader("loggerCode", MDC.get("loggerCode"));
            responseWrapper.setHeader("requestId", MDC.get("requestId"));
            log.info("Response header is set with requestId {} and loggerCode: {}", responseWrapper.getHeader("requestId"), responseWrapper.getHeader("loggerCode"));
            responseWrapper.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "loggerCode, requestId");
            responseWrapper.copyBodyToResponse();
        }
    }

    public void destroy() {
    }
}

