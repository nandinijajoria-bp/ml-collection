package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PEKycCallbackResponseDTO {
     String requestReferenceId;
     String leadReferenceId;
     String applicantReferenceId;
     String kycStatus;
     String kycType;
     AadharDetail aadharDetail;
     String mode;
     SelfieDetail selfieDetail;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AadharDetail{
         String name;
         String dob;
         Address address;
         String maskedAadhaarNumber;
         String gender;
         String completeAddress;
         String xmlRefId;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Address{
         String addressLine1;
         String addressLine2;
         String city;
         String pinCode;
         String stateCode;
         String country;
         String vtcName;
         String district;
         String state;
         String location;
         String careOf;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SelfieDetail{
         String imageRefId;
         Date createdDate;
         Date updatedDate;
         String imageBase64;
    }
}
