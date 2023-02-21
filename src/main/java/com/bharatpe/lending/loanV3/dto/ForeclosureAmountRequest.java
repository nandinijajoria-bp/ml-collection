package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForeclosureAmountRequest {
    String lender;
    String productName;
    Long applicationId;
    Payload payload;


    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        String accountId;
        String dealNo;
        String loanNo;
    }

}
