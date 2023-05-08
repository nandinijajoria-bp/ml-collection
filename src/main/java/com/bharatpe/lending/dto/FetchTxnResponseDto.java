package com.bharatpe.lending.dto;

import lombok.Data;

import java.util.List;

@Data
public class FetchTxnResponseDto {

    private List<Presentment> data;

    @Data
    public static class Presentment {
        private String Date;
        private Double presentmentAmt;
        private String status;
    }

}
