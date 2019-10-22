package com.bharatpe.lending.interceptor;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InitFilter implements Filter{
	private static final Logger logger = LoggerFactory.getLogger(InitFilter.class);

    public void init(FilterConfig filterConfig) throws ServletException {
    }
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        long startTime = System.currentTimeMillis();

        InterceptorRequestWrapper interceptorRequestWrapper = new InterceptorRequestWrapper((HttpServletRequest) request);
        chain.doFilter(interceptorRequestWrapper, response);
        long endTime = System.currentTimeMillis();
        logger.info("Time taken by API: " + interceptorRequestWrapper.getRequestURI() + " = " + (endTime - startTime) );
    }

    public void destroy() {
    }
}
