package com.bharatpe.lending.logging.filter;

import com.bharatpe.lending.logging.util.RequestLoggerUtil;
import com.bharatpe.lending.logging.wrapper.SpringRequestWrapper;
import com.bharatpe.lending.logging.wrapper.SpringResponseWrapper;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@Component
public class RequestLoggerFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(RequestLoggerFilter.class);

    @Autowired
    RequestLoggerUtil requestLoggerUtil;

    @Value("#{'${logging.exclude.urls}'.split(',')}")
    List<String> loggingExcludedUrls;

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String path = httpServletRequest.getRequestURI();

        if (!allowLogging(path)) {
            chain.doFilter(request, response);
        } else {
            Long startTime = System.currentTimeMillis();

            requestLoggerUtil.generateAndSetMDC(httpServletRequest);
            SpringRequestWrapper wrappedRequest = new SpringRequestWrapper(httpServletRequest);
            requestLoggerUtil.logRequest(logger, wrappedRequest);

            SpringResponseWrapper wrappedResponse = new SpringResponseWrapper((HttpServletResponse) response);
            wrappedResponse.setHeader(RequestLoggerUtil.REQUEST_ID_HEADER_NAME, MDC.get(RequestLoggerUtil.REQUEST_ID_HEADER_NAME));
            wrappedResponse.setHeader(RequestLoggerUtil.CORRELATION_ID_HEADER_NAME, MDC.get(RequestLoggerUtil.CORRELATION_ID_HEADER_NAME));


            try {
                chain.doFilter(wrappedRequest, wrappedResponse);
            } catch (Exception e) {
                requestLoggerUtil.logResponse(logger, wrappedResponse, startTime, System.currentTimeMillis(), 500);
                throw e;
            }
            requestLoggerUtil.logResponse(logger, wrappedResponse, startTime, System.currentTimeMillis(), wrappedResponse.getStatus());
        }
    }

    boolean allowLogging(String path) {

        for (String url : loggingExcludedUrls) {
            if ((StringUtils.isNotEmpty(url) || StringUtils.isNotBlank(url)) && path.contains(url)) {
                return false;
            }
        }
        return true;
    }

    public void destroy() {
    }

}