package com.bharatpe.lending.loanV3.dto;

import com.bharatpe.lending.enums.Lender;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.Date;
import java.util.List;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class SanctionWrapperApiRequestDto {
    String lender;
    Long applicationId;
    Payload payload;
    String productName;
    boolean topup;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Charges {
        String chargeType;
        String businessPartnerType;
        String chargeAmount;
        String taxRate1;
        String taxInclusive;
        String taxApplicable;
        String taxRate2;
        String businessPartnerName;
        String chargeCode;
        String chargeMethod;
        String chargeCalculatedOn;
        String finalAmount;
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Kyc {
        Integer operationsFlowType;
        Integer creditFlowType;
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class MandateDetails {
        String vendor;
        String status;
        String referenceNo;
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BankDetails {
        String accountNumber;
        @JsonProperty("IFSCCode")
        String IFSCCode;
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        String accountId;
        Integer loanAmount;
        Kyc kyc;
        List<Charges> charges;
        BankDetails bankDetails;
        MandateDetails mandateDetails;
    }
}
