package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class RegulatoryApiRequestDto {
    String lender;
    Long applicationId;
    String productName;
    Boolean isTopup;
    RegulatoryApiRequestDto.Payload payload;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        String accountId;
        String auditData;
    }
}
