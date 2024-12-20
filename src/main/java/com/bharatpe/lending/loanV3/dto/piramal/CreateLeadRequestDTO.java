package com.bharatpe.lending.loanV3.dto.piramal;

import com.bharatpe.lending.enums.Lender;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateLeadRequestDTO {
         String partnerApplicationId;
         String entityType;
         String productId;
         PrimaryApplicantDetail primaryApplicantDetail;
         AdditionalInformation additionalInformation;
         LocationInformation locationInformation;

         @Data
         @AllArgsConstructor
         @NoArgsConstructor
         @Builder
         @JsonInclude(JsonInclude.Include.NON_NULL)
         public static class PrimaryApplicantDetail {
             String firstName;
             String middleName;
             String lastName;
             String email;
             String mobileNo;
         }

         String leadId;
         List<ApplicantsDetail> applicantsDetail;

         @Data
         @AllArgsConstructor
         @NoArgsConstructor
         @Builder
         @JsonInclude(JsonInclude.Include.NON_NULL)
         public static class ApplicantsDetail {
             Applicant applicant;
             String customerId;
             BusinessInformation businessInformation;

             @Data
             @AllArgsConstructor
             @NoArgsConstructor
             @Builder
             @JsonInclude(JsonInclude.Include.NON_NULL)
             public static class Applicant {
                 String applicantType;
                 String firstName;
                 String middleName;
                 String lastName;
                 String nameAsPerKyc;
                 String countryOfBirth;
                 String gender;
                 String dateOfBirth;
                 String salutation;
                 String emailId;
                 String mobileNo;
                 String maritalStatus;
                 String residentialStatus;
                 String fatherName;
                 String occupationType;
                 Boolean incomeContributor = Boolean.TRUE;
                 PanCardDetail panCardDetail;

                 @Data
                 @AllArgsConstructor
                 @NoArgsConstructor
                 @Builder
                 @JsonInclude(JsonInclude.Include.NON_NULL)
                 public static class PanCardDetail {
                     String name;
                     String panCardNo;
                     String dateOfBirth;
                 }

                 Address currentAddress;

                 @Data
                 @AllArgsConstructor
                 @NoArgsConstructor
                 @Builder
                 @JsonInclude(JsonInclude.Include.NON_NULL)
                 public static class Address {
                     String addressType;
                     String addressCategory;
                     String buildingNumber;
                     String street;
                     String addressLine1;
                     String addressLine2;
                     String addressLine3;
                     String city;
                     String stateCode;
                     String country;
                     String postalCode;
                 }
                 Address permanentAddress;
                 Address kycAddress;
                 Address mailingAddress;

             }

             @Data
             @AllArgsConstructor
             @NoArgsConstructor
             @Builder
             @JsonInclude(JsonInclude.Include.NON_NULL)
             public static class BusinessInformation {
                 String businessName;
                 String businessType;
                 Double monthlyIncome;
                 Double annualIncome;
                 Applicant.Address businessAddress;
                 String industry;
                 String subIndustry;

                 String businessAddressType;
                 Boolean isGstEligible;
                 String gstNumber;
                 String incomeMode;
                 Double monthlyNFI;
             }

             KycDetail kycDetail;

             @Data
             @AllArgsConstructor
             @NoArgsConstructor
             @Builder
             @JsonInclude(JsonInclude.Include.NON_NULL)
             public static class KycDetail {
                 String kycType;
                 String ckycID;
             }
         }

         LoanInformation loanInformation;

         @Data
         @AllArgsConstructor
         @NoArgsConstructor
         @Builder
         @JsonInclude(JsonInclude.Include.NON_NULL)
         public static class LoanInformation {
             Double offeredAmount;
             Integer offeredTenure;
             Double offeredInterestRate;
             Double requestedAmount;
             Integer requestedTenure;
             Double requestedRateOfInterest;
             String loanPurpose;
             String loanTransactionType;
             String loanProductType;

         }

         AuditTrailInformation auditTrailInformation;

         @Data
         @AllArgsConstructor
         @NoArgsConstructor
         @Builder
         @JsonInclude(JsonInclude.Include.NON_NULL)
         public static class AuditTrailInformation {
             List<AuditTrailList> auditTrailList;

             @Data
             @AllArgsConstructor
             @NoArgsConstructor
             @Builder
             @JsonInclude(JsonInclude.Include.NON_NULL)
             public static class AuditTrailList {
                 String auditName;
                 String auditCode;
                 String ipAddress;
                 String timeStamp;
             }
         }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class AdditionalInformation {
            Double total60DaysTPV;
            String riskGroup;
            String pincodeColor;
            String applicantProfile;
            String loanSegment;
            Integer monthlyNFI;
            Integer bharatpeVintage;
            String piramalExistingLoanNo;
            String piramalExistingLeadId;
            Double percentPaidThroughQr;
            Integer existingLoanMaxDpd;
            Integer topUpCount;
            Double existingLoanOutStandingAmount;
            Double existingLoanOutStandingPrincipal;
            Integer existingLoanCurrentDpd;
            String loanDetailsDate;
            PartnerVerificationDetails partnerVerificationDetails;

        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class PartnerVerificationDetails {
            SelfieLiveliness selfieLiveliness;
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class SelfieLiveliness {
            String mode;
            String verificationType;
            Double score;
            String verificationStatus;
            String serviceProvider;
        }


        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class LocationInformation {
             String latitude;
             String longitude;
             Date timeStamp;
        }
 }

