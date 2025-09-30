package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLCreateClientRequestDto {

    private Long clientId;
    private ClientDetails clientDetails;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClientDetails {
        private String firstName;
        private String middleName;
        private String lastName;
        private String dateOfBirth;
        private String gender;
        private String mobileNo;
        private String alternateMobileNo;
        private String email;
        private String education;
        private String externalId;
    }

    private List<AddressDetails> addressDetails;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AddressDetails {
        private List<String> addressType;
        private String addressLineOne;
        private String addressLineTwo;
        private String landmark;
        private String ownershipType;
        private String postalCode;
    }

    private List<FamilyDetails> familyDetails;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FamilyDetails {
        private String firstName;
        private String lastName;
        private String dateOfBirth;
        private String relationship;
        private String gender;
    }

    private List<ClientIdentifierDetails> clientIdentifierDetails;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClientIdentifierDetails {
        private String documentType;
        private String documentKey;
        private String issueDate;
        private String expiryDate;
    }

    private List<BankDetails> bankDetails;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BankDetails {
        private String accountNumber;
        private String accountType;
        private String ifscCode;
        private String name;
        private Boolean supportedForRepayment;
        private Boolean supportedForDisbursement;
    }

    private EmploymentDetails employmentDetails;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmploymentDetails {
        private String companyType;
        private String employmentType;
        private Double monthlySalary;
        private Integer totalWorkExperience;
        private String currentEmployerName;
    }

    private List<AdditionalDetails> additionalDetails;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdditionalDetails {
        private String dataTableName;
        private String appTable;
        private String subIndustry;
        private String industry;
        private String businessType;
        private String businessAddress;
        private String state;
        private String city;
        private String country;
        private Long postalCode;
        private String businessAddressType;
        private String gstNumber;
        private String businessDocument;
        private String legalName;
        private String tradeName;
        private String bankBeneName;
    }
}
