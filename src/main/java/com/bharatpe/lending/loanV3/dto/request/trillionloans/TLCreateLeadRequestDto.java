package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLCreateLeadRequestDto {

    private Long clientId;
    private Long loanOfficerId;
    private Double amount;
    private String losProductKey;
    private LeadApplicationTerms leadApplicationTerms;
    private String externalId;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LeadApplicationTerms {
        private Double maxEligibleAmount;
        private Long numberOfRepayments;
        private Integer repayEvery;
        private Integer repaymentPeriodFrequencyEnum;
        private Integer termPeriodFrequencyEnum;
        private Integer termFrequency;
        private Double interestRatePerPeriod;
        private Double graceOnPrincipalPayment;
        private Double graceOnInterestCharged;
        private Double amountForUpfrontCollection;
    }

    private List<Charge> charges;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Charge {
        private Long chargeId;
        private String amount;
        private Boolean isAmountNonEditable;
        private Boolean isMandatory;
        private Boolean canLendCharge;
        private Boolean canAddChargeToPrincipalForComputation;
    }
}
