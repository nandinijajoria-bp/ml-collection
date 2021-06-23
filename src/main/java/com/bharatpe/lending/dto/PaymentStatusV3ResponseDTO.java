package com.bharatpe.lending.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import java.util.Date;
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentStatusV3ResponseDTO {
    private boolean success;
    private String message;
    private Data data;
    public PaymentStatusV3ResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    public PaymentStatusV3ResponseDTO(boolean success, String message, Data data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
    @lombok.Data
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Data {
        private String paymentStatus;
        private String orderId;
        private Double amount;
        private String referenceNumber;
        private Date transferTime;
        private String paymentMode;
    }
}