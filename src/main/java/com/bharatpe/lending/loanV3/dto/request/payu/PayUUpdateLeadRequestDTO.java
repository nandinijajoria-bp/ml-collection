package com.bharatpe.lending.loanV3.dto.request.payu;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class PayUUpdateLeadRequestDTO {

    @JsonProperty("application-id")
    private String applicationId;

    @JsonProperty("applicant_details")
    private List<ApplicantDetailsDTO> applicantDetails;

    @JsonProperty("company_details")
    private List<CompanyDetailsDTO> companyDetails;

    @JsonProperty("bank_details")
    private List<BankDetailsDTO> bankDetails;

    @JsonProperty("compliance")
    private ComplianceDTO compliance;

    @JsonProperty("loan_requirement")
    private LoanRequirementDTO loanRequirement;

    private Location location;

    private BankDetailsDTO updatedBankDetails;

    private Boolean updatedAddress;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApplicantDetailsDTO {

        @JsonProperty("applicant_id")
        private Long applicantId;

        @JsonProperty("pan")
        private String pan;

        @JsonProperty("aadhaar")
        private String aadhaar;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("middle_name")
        private String middleName;

        @JsonProperty("last_name")
        private String lastName;

        @JsonProperty("email")
        private String email;

        @JsonProperty("mobile_number")
        private String mobileNumber;

        @JsonProperty("dob")
        private String dob;

        @JsonProperty("gender")
        private String gender;

        @JsonProperty("designation")
        private String designation;

        @JsonProperty("is_director")
        private boolean isDirector;

        @JsonProperty("is_auth_signatory")
        private boolean isAuthSignatory;

        @JsonProperty("is_shareholder")
        private boolean isShareholder;

        @JsonProperty("is_main_applicant")
        private boolean isMainApplicant;

        @JsonProperty("shareholding_percentage")
        private String shareholdingPercentage;

        @JsonProperty("din_number")
        private String dinNumber;

        @JsonProperty("address")
        private List<AddressDTO> address;

        @JsonProperty("residence_address_same_as_permanent_address")
        private Boolean residenceAddressSameAsPermanentAddress;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompanyDetailsDTO {

        @JsonProperty("parent_company_id")
        private Long parentCompanyId;

        @JsonProperty("company_id")
        private Long companyId;

        @JsonProperty("company_name")
        private String companyName;

        @JsonProperty("turnover_last_fy")
        private String turnoverLastFY;

        @JsonProperty("date_of_incorporation")
        private String dateOfIncorporation;

        @JsonProperty("gstin")
        private String gstin;

        @JsonProperty("is_primary_company")
        private boolean isPrimaryCompany;

        @JsonProperty("company_pan")
        private String companyPan;

        @JsonProperty("entity_type")
        private String entityType;

        @JsonProperty("type_of_business")
        private String typeOfBusiness;

        @JsonProperty("nature_of_industry")
        private String natureOfIndustry;

        @JsonProperty("address")
        private List<AddressDTO> address;

        @JsonProperty("partner_vintage")
        private String partnerVintage;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BankDetailsDTO {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("account_type_id")
        private String accountTypeId;

        @JsonProperty("bank_account_name")
        private String bankAccountName;

        @JsonProperty("account_number")
        private String accountNumber;

        @JsonProperty("bank_name")
        private String bankName;

        @JsonProperty("ifsc_code")
        private String ifscCode;

        @JsonProperty("branch")
        private String branch;

        @JsonProperty("name_match_percentage")
        private String nameMatchPercentage;

        @JsonProperty("disbursement_account")
        private boolean disbursementAccount;

        @JsonProperty("virtual_account")
        private boolean virtualAccount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComplianceDTO {

        @JsonProperty("bureau_consent")
        private boolean bureauConsent;

        @JsonProperty("general_consent")
        private boolean generalConsent;

        @JsonProperty("kyc_consent")
        private boolean kycConsent;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanRequirementDTO {

        @JsonProperty("amount")
        private Double amount;

        @JsonProperty("roi")
        private Double roi;

        @JsonProperty("tenure")
        private String tenure;

        @JsonProperty("purpose")
        private String purpose;

        @JsonProperty("loan_type_id")
        private String loanTypeId;

        @JsonProperty("roi_type")
        private String roiType;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AddressDTO {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("line1")
        private String line1;

        @JsonProperty("line2")
        private String line2;

        @JsonProperty("locality")
        private String locality;

        @JsonProperty("city")
        private String city;

        @JsonProperty("pincode")
        private String pincode;

        @JsonProperty("ownership_indicator")
        private String ownershipIndicator;

        @JsonProperty("address_type")
        private String addressType;

        @JsonProperty("state")
        private String state;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String latitude;
        private String longitude;
        private String ipAddress;
    }
}
