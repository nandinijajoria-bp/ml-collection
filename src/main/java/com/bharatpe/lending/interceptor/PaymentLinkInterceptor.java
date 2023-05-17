package com.bharatpe.lending.interceptor;

import com.bharatpe.common.constants.ResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Value("${payment.link.secret:secretKey}")
    private String secretKey;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        logger.info("Validating hashId in payment link interceptor for request:{}",request);
        String hashId = request.getParameter("hash_id");
        // Get hash of loan_id and merchant_id and validate with hash_id;
        String merchantId=request.getParameter("merchant_id");
        String externalLoanId=request.getParameter("external_loan_id");

        // Do not change this logic,as this being used while generating the url.
        String inputString=merchantId+"|"+externalLoanId+"|"+secretKey;
        if(!StringUtils.isEmpty(hashId) && hashId.equals(getHash(inputString))){
            logger.info("HashId validated in payment link interceptor for request:{}",request);
            request.setAttribute("merchantId", merchantId);
            request.setAttribute("externalLoanId", externalLoanId);
            return true;
        }
        response.setStatus(Integer.parseInt(ResponseCode.UNAUTHORIZED));
        return false;
    }


    private String getHash(String inputString) {
        {
            try {
                // Create MessageDigest instance for MD5
                MessageDigest md = MessageDigest.getInstance("MD5");
                // Add password bytes to digest
                md.update(inputString.getBytes());
                // Get the hash's bytes
                byte[] bytes = md.digest();
                // This bytes[] has bytes in decimal format. Convert it to hexadecimal format
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < bytes.length; i++) {
                    sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
                }
                // Get complete hashed value in hex format
                return sb.toString();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

}
