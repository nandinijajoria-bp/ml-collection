package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaymentInitiateResponseDTO {
    private Long requestId;
    private String upiString;
    private Boolean otpFlow;
    private String authMode;
    private boolean success = true;
    private String message;

    public PaymentInitiateResponseDTO(Long requestId, String upiString, Boolean otpFlow, String authMode) {
        this.requestId = requestId;
        this.upiString = upiString;
        this.otpFlow = otpFlow;
        this.authMode = authMode;
    }

    public PaymentInitiateResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public String getUpiString() {
        return upiString;
    }

    public void setUpiString(String upiString) {
        this.upiString = upiString;
    }

    public Boolean getOtpFlow() {
        return otpFlow;
    }

    public void setOtpFlow(Boolean otpFlow) {
        this.otpFlow = otpFlow;
    }

    public String getAuthMode() {
        return authMode;
    }

    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
