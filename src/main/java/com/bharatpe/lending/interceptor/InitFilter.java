package com.bharatpe.lending.interceptor;

import com.bharatpe.common.constants.RequestConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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

        MDC.put(RequestConstants.requestId,
                ObjectUtils.isEmpty(httpServletRequest.getHeader(RequestConstants.requestId)) ? uniqueId.toString() : httpServletRequest.getHeader(RequestConstants.requestId));
        MDC.put(RequestConstants.loggerCode,
                ObjectUtils.isEmpty(httpServletRequest.getHeader(RequestConstants.loggerCode)) ? loggerCode : httpServletRequest.getHeader(RequestConstants.loggerCode));

        log.info("Request IP address is {}", servletRequest.getRemoteAddr());
        log.info("Request content type is {}", servletRequest.getContentType());

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpServletResponse);

        InterceptorRequestWrapper interceptorRequestWrapper = new InterceptorRequestWrapper(httpServletRequest);

        filterChain.doFilter(interceptorRequestWrapper, responseWrapper);
        responseWrapper.setHeader(RequestConstants.loggerCode, MDC.get(RequestConstants.loggerCode));
        responseWrapper.setHeader(RequestConstants.requestId, MDC.get(RequestConstants.requestId));
        log.info("Response header is set with requestId {} and loggerCode: {}", responseWrapper.getHeader(RequestConstants.requestId), responseWrapper.getHeader(RequestConstants.loggerCode));
        responseWrapper.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, RequestConstants.loggerCode + ", " + RequestConstants.requestId);
        responseWrapper.copyBodyToResponse();
    }

    public void destroy() {
    }
}

