package com.bharatpe.lending.dto;

import com.bharatpe.common.entities.Experian;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class GlobalLimitResponse {
    private boolean success;
    private String message;
    private Data data;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Data {
        private Long merchantId;
        private Double globalLimit;
        private boolean derog;
        private String rejectReason;
        private String pancardName;
        private Experian experian;
    }
}
