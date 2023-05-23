package com.bharatpe.lending.interceptor;

import com.bharatpe.common.constants.ResponseCode;
import com.bharatpe.lending.common.util.PaymentLinkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class PaymentLinkInterceptor  implements HandlerInterceptor {

    Logger logger = LoggerFactory.getLogger(PaymentLinkInterceptor.class);


    @Autowired
    private PaymentLinkUtil paymentLinkUtil;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        logger.info("Validating hashId in payment link interceptor for request:{}",request);
        String hashId = request.getParameter("hash_id");
        // Get hash of loan_id and merchant_id and validate with hash_id;
        String merchantId=request.getParameter("merchant_id");
        String externalLoanId=request.getParameter("external_loan_id");

        // Do not change this logic,as this being used while generating the url.
        if(!StringUtils.isEmpty(hashId) && hashId.equals(paymentLinkUtil.getHashId(merchantId,externalLoanId))){
            logger.info("HashId validated in payment link interceptor for request:{}",request);
            request.setAttribute("merchantId", merchantId);
            request.setAttribute("externalLoanId", externalLoanId);
            return true;
        }
        response.setStatus(Integer.parseInt(ResponseCode.UNAUTHORIZED));
        return false;
    }




}
