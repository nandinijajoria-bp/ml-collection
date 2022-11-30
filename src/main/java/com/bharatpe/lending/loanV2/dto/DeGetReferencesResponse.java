package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.dto.MerchantReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class DeGetReferencesResponse {
    private String message;
    private String status;
    private Integer totalContacts;
    private DeGetReferencesResponse.Data data;

    @lombok.Data
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private List<MerchantReference> output;
    }

}
