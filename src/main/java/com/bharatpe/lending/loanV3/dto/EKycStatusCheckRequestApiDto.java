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
    String lender;
    Long applicationId;
    String productName;
    Payload payload;
    Boolean topup;
    LinkedHashMap<String, Object> identifier;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        String accountId;
        String profileId;
        String journeyType;
    }
}
