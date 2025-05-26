package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EKycStatusCheckRequestApiDto {
    private String lender;
    private Long applicationId;
    private String productName;
    private Payload payload;
    private Boolean topup;
    private LinkedHashMap<String, Object> identifier;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        private String accountId;
    }
}
