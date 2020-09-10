package com.bharatpe.lending.dto;

public class CreditSpendRequestDTO {

    private Integer amount;
    private String mode;

    public CreditSpendRequestDTO(Integer amount, String mode) {
        this.amount = amount;
        this.mode = mode;
    }

    public CreditSpendRequestDTO() {
    }

    public Integer getAmount() {
        return amount;
    }

    public String getMode() {
        return mode;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    @Override
    public String toString() {
        return "CreditSpendRequestDTO{" +
                "amount=" + amount +
                ", mode='" + mode + '\'' +
                '}';
    }
}
