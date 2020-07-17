package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateTxnRequestDTO {
    private Double amount;
    private String mode;
    private String orderId;
    private String narration1;
    private String narration2;
    private String narration3;
    private String icon;

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getNarration1() {
        return narration1;
    }

    public void setNarration1(String narration1) {
        this.narration1 = narration1;
    }

    public String getNarration2() {
        return narration2;
    }

    public void setNarration2(String narration2) {
        this.narration2 = narration2;
    }

    public String getNarration3() {
        return narration3;
    }

    public void setNarration3(String narration3) {
        this.narration3 = narration3;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    @Override
    public String toString() {
        return "CreateTxnRequestDTO{" +
                "amount=" + amount +
                ", mode='" + mode + '\'' +
                ", orderId='" + orderId + '\'' +
                ", narration1='" + narration1 + '\'' +
                ", narration2='" + narration2 + '\'' +
                ", narration3='" + narration3 + '\'' +
                ", icon='" + icon + '\'' +
                '}';
    }
}
