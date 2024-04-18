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
public class CapriCreateClientRequestDTO {
    String locale;
    String dateFormat;
    ClientData clientData;
    List<AddressData> addressData;
    List<ClientIdentifierData> clientIdentifierData;
    List<BankDetailsData> bankDetailsData;
    AdditionalDetail additionalDetail;
    EmploymentDetailData employmentDetailData;
    List<FamilyDetailData> familyDetailData;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClientData {
        String firstName;
        String dateFormat;
        String gender;
        String dateOfBirth;
        String mobileNo;
        String locale;
        String active;
        String middleName;
        String lastName;
        Integer maritalStatusId;
        Integer officeId;
        String officeName;
        String alternateMobileNo;
        String education;
        String externalId;
        String submittedOnDate;
        String activationDate;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AddressData {
        List<String> addressType;
        String ownershipType;
        String villageTown;
        String addressLineOne;
        String addressLineTwo;
        String landmark;
        String district;
        String state;
        String country;
        String postalCode;
        String locale;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BankDetailsData {
        Integer accountTypeId;
        String accountType;
        String name;
        String accountNumber;
        String ifscCode;
        String mobileNumber;
        String email;
        Boolean useAsPrimaryAccount;
        Boolean doPennyDrop;
        Boolean supportedForRepayment;
        Boolean supportedForDisbursement;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClientIdentifierData {
        String dateFormat;
        String locale;
        Integer documentTypeId;
        String documentKey;
        String description;
        Integer status;
        String documentIssueDate;
        String documentExpiryDate;
        String authStatus;
        String documentType;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdditionalDetail {
        String dataTableName;
        String appTable;
        String cibil;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmploymentDetailData {
        String companyType;
        String employmentType;
        String monthlySalary;
        String totalWorkExperience;
        String currentEmployerName;
        String occupationType;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FamilyDetailData {
        String firstname;
        String lastname;
        String dateOfBirth;
        String relationship;
        String gender;
        String documentType;
        String documentKey;
    }
}
