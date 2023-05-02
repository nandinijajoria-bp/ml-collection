package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class AutoPayRegisterPgResponseDto {

    private String statusCode;
    private String message;
    private PgCreateTransactionResponseDTO.Data data;

    @lombok.Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Data {
        private Double paymentAmount;
        private String orderId;
        private String paymentURI;
        private String paymentURIDeeplink;

    }

}
