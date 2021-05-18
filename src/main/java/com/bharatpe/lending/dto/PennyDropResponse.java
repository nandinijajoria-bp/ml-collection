package com.bharatpe.lending.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PennyDropResponse {
    private String responseCode;
    private String message;
    private String status;
    private Data data;

    @lombok.Data
    @NoArgsConstructor
    public static class Data {
        private String ifsc;
        private String beneficiaryName;
        private String accountNo;
    }

}
