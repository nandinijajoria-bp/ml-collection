package com.bharatpe.lending.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;

@Component
public class InitFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(InitFilter.class);

    public static final String REQUEST_ID = "requestId";
    public static final String LOGGER_CODE = "loggerCode";

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        Random rand = new Random();
        String loggerCode = rand.nextInt(10000) + "-" + rand.nextInt(10000);
        UUID uniqueId = UUID.randomUUID();


        MDC.put(REQUEST_ID, uniqueId.toString());
        MDC.put(LOGGER_CODE, loggerCode);
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(
                httpServletResponse
        );
        chain.doFilter(request, responseWrapper);
        responseWrapper.setHeader(REQUEST_ID, uniqueId.toString());
        responseWrapper.setHeader(LOGGER_CODE, loggerCode);
        responseWrapper.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "loggerCode, requestId");
        responseWrapper.copyBodyToResponse();
    }

    public void destroy() {
    }
}

