package com.bharatpe.lending.loanV3.dto.response.creditsasion;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditSasionUnderwritingResponseDTO {

    private List<LinkedIndividual> linkedIndividuals;
    private List<CustomerConsent> customerConsents;
    private Loan loan;
    private String partnerId;
    private String partnerLoanId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LinkedIndividual {
        private String applicantType;
        private List<Contact> contacts;
        private Individual individual;
        private List<Address> addresses;
        private List<Kyc> kyc;
        private Misc misc;
        private String employmentStatus;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Contact {
            private String countryCode;
            private String notify;
            private String priority;
            private String type;
            private String typeCode;
            private String value;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Individual {
            private String dob;
            private String firstName;
            private String middleName;
            private String lastName;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Address {
            private String type;
            private String city;
            private String state;
            private String pinCode;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Kyc {
            private String issuedCountry;
            private String kycType;
            private String kycValue;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Misc {
            private Integer income;
            private String loanSegment;
            private String riskGroup;
            private String tpv60;
            private String monthlyNfi;
            private String pincodeColour;
            private String merchantVintageDays;
            private String applicantProfile;
            private String applicationId;
            private String customerId;
            private String businessType;
            private String businessAddressOwnership;
            private String businessCity;
            private String businessAddressType;
            private String businessAddressCountry;
            private String businessAddressStreet1;
            private String businessAddressStreet2;
            private String businessAddressPostalCode;
            private String businessAddressBuilding;
            private String businessAddressState;
            private String businessIndustry;
            private String businessMonthlyIncome;
            private String businessName;
            private String derogCategory;
            private String gst;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CustomerConsent {
        private String consentFor;
        private String consentMode;
        private String consentChannel;
        private String consentTime;
        private String requestID;
        private ConsentIdentifier consentIdentifier;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class ConsentIdentifier {
            private String ip;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Loan {
        private String loanProduct;
        private Double loanIntRate;
        private Double monthlyRoi;
        private Integer tenure;
        private Integer amount;
    }
}
