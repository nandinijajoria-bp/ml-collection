package com.bharatpe.lending.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Service
public class PaymentLinkInterceptor  implements HandlerInterceptor {

    Logger logger = LoggerFactory.getLogger(PaymentLinkInterceptor.class);

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        logger.info("Validating hashId in payment link interceptor for request:{}",request);
        String hashId = request.getParameter("hash_id");
        // Get hash of loan_id and merchant_id and validate with hash_id;

        return true;
    }

}
