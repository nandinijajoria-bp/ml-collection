package com.bharatpe.lending.loanV3.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForeclosureChargesRequestDto {
    private long applicationId;
    private String productName;
    private String lender;
    private Payload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payload {
        private String accountId;
        private String uniqueId;
        private String dealNo;
        private String loanNo;
        private String transactionId;
        private String chargeType;
        private String businessPartnerType;
        private String chargeAmount;
        private String taxInclusive;
        private String finalAmount;
        private String chargeCode;
    }
}
