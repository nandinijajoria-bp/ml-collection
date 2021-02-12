package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SupportResponseDTO {

    private boolean success;

    private String message;

    private Object data;

    public SupportResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
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

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "SupportResponseDTO{" +
            "success=" + success +
            ", message='" + message + '\'' +
            ", data=" + data +
            '}';
    }
}
