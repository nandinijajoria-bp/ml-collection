package com.bharatpe.lending.interceptor;

import com.bharatpe.lending.dto.RequestResponseAuditDto;
import com.bharatpe.lending.service.RequestAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RequestLoggingInterceptor implements HandlerInterceptor {

    @Autowired
    RequestAuditService requestAuditService;

    @Autowired
    ObjectMapper objectMapper;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        log.info("auditing request response for {}", request.getRequestURI());
        RequestResponseAuditDto requestResponseAuditDto = RequestResponseAuditDto.builder()
                .request(new InterceptorRequestWrapper(request).getBody())
                .response(new String(((ContentCachingResponseWrapper) response).getContentAsByteArray()))
                .requestUri(request.getRequestURI())
                .requestHeaders(objectMapper.writeValueAsString(
                        Collections.list(request.getHeaderNames()).stream()
                                .collect(Collectors.toMap(x -> x,x->request.getHeader(x))
                        )))
                .requestParams(objectMapper.writeValueAsString(
                        Collections.list(request.getParameterNames()).stream()
                                .collect(Collectors.toMap(x -> x,x->request.getParameter(x))
                        )))
                .requestId(MDC.get("requestId"))
                .build();
        requestAuditService.auditApiRequestResponseData(requestResponseAuditDto);
    }
}
