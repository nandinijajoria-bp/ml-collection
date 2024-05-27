package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DigitalDataUploadRequest {
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
    public static class Payload {
         String accountId;
         String location;
         @JsonProperty("ccc_id")
         String cccId;
         String companyCategory;
         String subIndustryType;
         String cabTransactionData;
         Integer bureauScore;
         Integer courseTenure;
         String personalEmailId;
         BigInteger mobileNumber;
         String category;
         String incomeDocumentProof;
         String addressLine2;
    }

}
