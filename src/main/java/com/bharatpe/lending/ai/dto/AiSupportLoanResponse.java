package com.bharatpe.lending.ai.dto;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class AiSupportLoanResponse {
    private boolean success;
    private String message;
    private AiSupportLoanData data;

    public AiSupportLoanResponse(boolean success, String message){
        this.success=success;
        this.message=message;
    }
}
