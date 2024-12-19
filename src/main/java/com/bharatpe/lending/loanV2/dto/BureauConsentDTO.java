package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class BureauConsentDTO {

    private String message;
    private String status;
    private Data Data;

    @lombok.Data
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Data {
        Integer pincode;
        String pan;
        boolean consent_expired;
        long merchantId;
        String consent_date;
        String created_at;
        String source;
        String mobile;
        String bureau_mobile;
    }
}
