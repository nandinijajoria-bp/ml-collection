package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    public boolean success;
    public String message;
    public T data;
    private String errorCode;


    public ApiResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public ApiResponse(boolean success, String errorCode,String message) {
        this.success = success;
        this.message = message;
        this.errorCode=errorCode;
    }


    public ApiResponse(T data) {
        this.data = data;
        this.success = true;
        this.message = "success";
    }
}
