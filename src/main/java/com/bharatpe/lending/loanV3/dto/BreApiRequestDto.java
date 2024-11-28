package com.bharatpe.lending.loanV3.dto;

import com.bharatpe.lending.enums.Lender;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
//@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class BreApiRequestDto {
    String lender;
    String productName;
    Long applicationId;
    boolean topup;
    Payload payload;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
//    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class CustomerReport {
        KycInfo kycInfo;
        LoanApplicationRequest loanApplicationRequest;
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
//    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class KycInfo {
         String firstName;
         String middleName;
         String lastName;
         String mobile;
         String panNumber;
         String gender;
         String dob;
         String addressLine1;
         String addressLine2;
         String addressLine3;
         String city;
         String state;
         Long pincode;
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
//    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class LoanApplicationRequest {
        Double requestedLoanAmount;
        String roi;
        String tenure;
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
//    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Payload {
        String source;
        String accountId;
        String productCode;
        CustomerReport customerReport;
        String loanSegment;
        String pincodeColor;
        String riskGroup;
        String bpVintage;
        Long shopPincode;
        String tpv;
        String registeredBusinessName;
    }
}
