package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.dto.KycDoc;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class KycDocResponse {
    private String message;
    private boolean status;
    private Data data;

    @lombok.Data
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private List<KycDoc> docs;
    }
}
