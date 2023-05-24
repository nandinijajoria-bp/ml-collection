package com.bharatpe.lending.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class PaymentLinkUtil {
    @Value("${payment.link.secret.key:secretKey}")
    private String paymentLinkSecretKey;

    @Value("${payment.link.pg.redirection.url:https://easy-loans-payment.bharatpe.io/#/payment-status}")
    private String paymentLinkPGRedirectionUrl;


    public String getPGRedirectionUrl(Long merchantId,String externalLoanId,String orderId){
        return paymentLinkPGRedirectionUrl+"?merchant_id="+merchantId+"&external_loan_id="+externalLoanId+"&hash_id="+getHashId(String.valueOf(merchantId),externalLoanId)+"&resultCode=true&pageRoute=transactionStatus&isPgWebMode=true"+"&txnId="+orderId;
    }
    public String getHashId(String merchantId,String externalLoanId) {
        {
            // Do not change this logic,as this being used while generating the url.
            String inputString=merchantId+"|"+externalLoanId+"|"+paymentLinkSecretKey;
            try {
                // Create MessageDigest instance for MD5
                MessageDigest md = MessageDigest.getInstance("MD5");
                // Add password bytes to digest
                md.update(inputString.getBytes());
                // Get the hash's bytes
                byte[] bytes = md.digest();
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
