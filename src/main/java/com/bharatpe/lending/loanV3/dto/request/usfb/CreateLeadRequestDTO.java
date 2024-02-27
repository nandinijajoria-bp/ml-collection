package com.bharatpe.lending.loanV3.dto.request.usfb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CreateLeadRequestDTO {
    String mobileNumber;
    String fullName;
    String email;
    Integer gender;
    String dateOfBirth;
    String fathersName;
    String address;
    String pincode;
    Integer monthlyIncome;
    Integer monthlyEmi;
    Integer creditScore;
    Integer employmentType;
    String companyName;
    String loanApplicationDate;
    String agreementDate;
    Integer loanAmount;
    Long repaymentCount;
    Double InterestRate;
    Integer paymentFrequency;
    Integer loanType;
    List<Kyc> kyc;
    Integer agreementSignatureType;
    String partnerLoanId;
    Integer tenure;
    List<DisbursalDetails> disbursalDetail;
    String source;
    String product;
    AdditionalVariables additionalVariables;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Kyc {
        String kycName;
        Integer kycType;
        String identifier;
        Boolean isVerified;
        Integer verifiedUsing;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class DisbursalDetails {
        String date;
        Double amount;
        Integer disbursalType;
        Double processingFee;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class AdditionalVariables{
        String riskSegment;
        String riskCategory;
        String uploadDate;
        String cautionProfileSubCategory;
        String cautionProfileCategory;
        String longitude;
        String latitude;
        String pinCodeColour;
    }
}

