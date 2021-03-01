package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseDTO {

    private boolean success;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    private String message;

    private Object data;

    private List<String> maskedMobiles;

    private String mobile;

    private boolean crif = false;

    public ResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public ResponseDTO(boolean success, String message, List<String> maskedMobiles, String uuid) {
        this.success = success;
        this.message = message;
        this.maskedMobiles = maskedMobiles;
        this.uuid = uuid;
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

    public boolean isCrif() {
        return crif;
    }

    public void setCrif(boolean crif) {
        this.crif = crif;
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
