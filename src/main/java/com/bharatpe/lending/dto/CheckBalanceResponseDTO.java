package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CheckBalanceResponseDTO {
    private Double amountLimit;
    private Double balance;
    private boolean status = true;
    private String message;

    public CheckBalanceResponseDTO(Double amountLimit, Double balance) {
        this.amountLimit = amountLimit;
        this.balance = balance;
    }

    public CheckBalanceResponseDTO(boolean status, String message) {
        this.status = status;
        this.message = message;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Double getAmountLimit() {
        return amountLimit;
    }

    public void setAmountLimit(Double amountLimit) {
        this.amountLimit = amountLimit;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }
}
