package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaymentResendOTP {

    private String orderId;
    private String otp;
    private String appHash;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public String getAppHash() {
        return appHash;
    }

    public void setAppHash(String appHash) {
        this.appHash = appHash;
    }

    @Override
    public String toString() {
        return "PaymentResendOTP{" +
                "orderId='" + orderId + '\'' +
                ", otp='" + otp + '\'' +
                ", appHash='" + appHash + '\'' +
                '}';
    }
}
