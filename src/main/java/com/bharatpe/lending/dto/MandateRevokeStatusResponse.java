package com.bharatpe.lending.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class MandateRevokeStatusResponse {
    private String statusCode;
    private String message;
    private Data data;

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @lombok.Data
    public static class Data {
        private String mandateId;
        private String status;
    }
}
// sample response {\"headers\":{},\"body\":{\"statusCode\":\"200\",\"message\":\"OK\",
// \"data\":{\"mandateId\":\"3i1CgMmXhNdMyWiRc8AnPy\",\"status\":\"REVOKED\"}},\"statusCodeValue\":200,\"statusCode\":\"OK\"}
