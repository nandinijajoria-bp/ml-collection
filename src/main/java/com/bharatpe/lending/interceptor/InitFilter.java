package com.bharatpe.lending.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
public class InitFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(InitFilter.class);

    public static final String REQUEST_ID = "requestId";
    public static final String LOGGER_CODE = "loggerCode";

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

//        Random rand = new Random();
//        String loggerCode = rand.nextInt(10000) + "-" + rand.nextInt(10000);
//        UUID uniqueId = UUID.randomUUID();
//
//
//        MDC.put(REQUEST_ID, uniqueId.toString());
//        MDC.put(LOGGER_CODE, loggerCode);
//        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
//        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(
//                httpServletResponse
//        );
//        chain.doFilter(request, responseWrapper);
//        responseWrapper.setHeader(REQUEST_ID, uniqueId.toString());
//        responseWrapper.setHeader(LOGGER_CODE, loggerCode);
//        responseWrapper.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "loggerCode, requestId");
//        responseWrapper.copyBodyToResponse();
        long startTime = System.currentTimeMillis();

        InterceptorRequestWrapper interceptorRequestWrapper = new InterceptorRequestWrapper((HttpServletRequest) request);
        chain.doFilter(interceptorRequestWrapper, response);
        long endTime = System.currentTimeMillis();
        logger.info("Time taken by API: " + interceptorRequestWrapper.getRequestURI() + " = " + (endTime - startTime) + " miliseconds");

    }

    public void destroy() {
    }
}

