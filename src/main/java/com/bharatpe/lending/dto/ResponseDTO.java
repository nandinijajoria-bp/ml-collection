package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseDTO {

    private boolean success;

    private String message;

    private Object data;

    private List<String> maskedMobiles;

    private String mobile;

    public ResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public ResponseDTO(boolean success, String message, List<String> maskedMobiles) {
        this.success = success;
        this.message = message;
        this.maskedMobiles = maskedMobiles;
    }

    public ResponseDTO() {
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

    public List<String> getMaskedMobiles() {
        return maskedMobiles;
    }

    public void setMaskedMobiles(List<String> maskedMobiles) {
        this.maskedMobiles = maskedMobiles;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "ResponseDTO{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", maskedMobiles=" + maskedMobiles +
                '}';
    }
}
