package com.bharatpe.lending.loanV3.dto.request.creditsasion;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditSasionRepaymentRequestDTO {
    private String partnerLoanId;
    private Double paidAmt;
    private Double principalPaidAmt;
    private Double interestPaidAmt;
    private Double penaltyPaidAmt;
    private List<PartnerCharges> partnerCharges;
    private String customerDebitDate;
    private String utr;
    private String customerModeOfPay;
    private String tag;
    private String productCode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PartnerCharges {
        private Double amt;
        private String component;

    }


}
