package com.bharatpe.lending.loanV3.dto.request.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroCreateLeadRequest {
    private String product;
    private ProfileData profileData;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProfileData {
        private String name;
        private String referenceId;
        private String email;
        private String mobile;
        private String panNumber;
        private Long dob;
        private String gender;
        private Integer workExperienceMonths;
        private Address address;
        private Address workAddress;

        @Data
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Address {
            private String house;
            private String street;
            private String pincode;
            private String locality;
            private String landmark;
        }

        private String employeeId;
        private String currentEmployer;
        private String workCin;
        private String workUdyamNumber;
        private String referenceNumber;
        private String referenceName;

        private FinanceBasicInfo financeBasicInfo;

        @Data
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class FinanceBasicInfo {
            private Double netMonthlyIncome;
            private String employmentType;
        }

        private List<BankAccount> bankAccounts;

        @Data
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class BankAccount {
            private String beneficiaryName;
            private String ifsc;
            private String accountNumber;
            private String purposeType;
        }

        private LoanRequest loanRequest;

        @Data
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class LoanRequest {
            private Double amount;
            private Integer tenure;
            private Double interestRate;
            private Double processingFeePct;
            private String purpose;
        }
    }

    private AcquisitionPlatformData acquisitionPlatformData;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AcquisitionPlatformData {
        private String loanSegment;
        private String segmentBand;
        private String pincodeSegment;
        private Double tpv;
        private Double netFreeIncome;
        private Integer vintageMonths;
        private Boolean isActiveTransactor;

        private Integer last_3_months_total_no_of_active_days;

        @JsonProperty("transaction_data")
        private Map<String, TransactionData> transactionData;

        @Data
        @Builder
        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class TransactionData {
            private Integer transactionAmount;
            private Integer commission;
            private Integer transactionCount;
        }
    }

    private UdyamRegistrationFields udyamRegistrationFields;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UdyamRegistrationFields {
        private String enterpriseName;
        private String socialCategory;
        private String residentialAddressLine;
        private String residentialCity;
        private String residentialState;
        private String residentialDistrict;
        private String residentialPincode;
        private String typeOfOrganization;
        private String businessAddressLine;
        private String businessCity;
        private String businessState;
        private String businessDistrict;
        private String businessPincode;
        private String businessSector;
        private String bsrCode;
        private Integer noOfEmployees;
    }
}

