package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UPIRegisterResponseDto {

    private String message;
    private boolean success;
    private UPIRegisterResponseDto.Data data;

    public UPIRegisterResponseDto(String msg) {
        this.message = msg;
    }

    public UPIRegisterResponseDto(Data data) {
        this.success = true;
        this.data = data;
    }

    @lombok.Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Data {
        private Double paymentAmount;
        private String orderId;
        private String paymentURIDeeplink;
    }

}
