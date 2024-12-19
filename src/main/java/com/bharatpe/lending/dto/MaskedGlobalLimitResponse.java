package com.bharatpe.lending.dto;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "ResponseDTO")
public class MaskedGlobalLimitResponse {
    private boolean success;
    private String message;
    private String errorCode;
    private String requestId;
    private String debugMessage;
    private MaskedGlobalLimitResponseDTO data;

    @XmlElement
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @XmlElement
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @XmlElement
    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @XmlElement
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    @XmlElement
    public String getDebugMessage() {
        return debugMessage;
    }

    public void setDebugMessage(String debugMessage) {
        this.debugMessage = debugMessage;
    }

    @XmlElement
    public MaskedGlobalLimitResponseDTO getData() {
        return data;
    }

    public void setData(MaskedGlobalLimitResponseDTO data) {
        this.data = data;
    }

}