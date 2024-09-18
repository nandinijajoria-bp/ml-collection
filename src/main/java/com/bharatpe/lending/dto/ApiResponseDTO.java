package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.ToStringBuilder;

public class ApiResponseDTO {
    @JsonProperty("success")
    public boolean success;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("msg")
    public String msg = null;

    @JsonProperty("data")
    public Object data;

    public ApiResponseDTO(boolean success, String msg, Object data) {
        this.success = success;
        this.msg = msg;
        this.data = data;
    }

    public ApiResponseDTO() {
        
    }

    public boolean getSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
