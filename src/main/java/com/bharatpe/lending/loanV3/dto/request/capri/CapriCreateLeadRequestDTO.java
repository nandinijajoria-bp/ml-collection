package com.bharatpe.lending.loanV3.dto.request.capri;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapriCreateLeadRequestDTO {
     Long loanOfficerId;
     Long loanPurposeId;
     Double amount;
     Long sourcingChannelId;
     String losProductKey;
     Associations associations;
     LeadApplicationTerms leadApplicationTerms;
     List<Charge> charges;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Associations{
         String anchor;
         String merchant;
         String thirdparty;
         String self;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LeadApplicationTerms{
         Double maxEligibleAmount;
         Long numberOfRepayments;
         Integer repayEvery;
         Integer repaymentPeriodFrequencyEnum;
         Integer termPeriodFrequencyEnum;
         Long termFrequency;
         Double interestRatePerPeriod;
         String dateFormat;
         Long graceOnPrincipalPayment;
         Long graceOnInterestCharged;
         Long amountForUpfrontCollection;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Charge{
         Long chargeId;
         Double amount;
         Boolean isAmountNonEditable;
         Boolean isMandatory;
         Boolean canLendCharge;
         Boolean canAddChargeToPrincipalForComputation;
         String chargeName;
         Double value;
    }
}
