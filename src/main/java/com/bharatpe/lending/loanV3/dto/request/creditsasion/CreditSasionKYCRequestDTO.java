package com.bharatpe.lending.loanV3.dto.request.creditsasion;

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
public class CreditSasionKYCRequestDTO {

    private String partnerLoanId;
    private String kycType;
    private String partnerAppId;
    private String partnerId;
    private String partnerSanctionTime;
    private String source;
    private String loanType;
    private List<LinkedIndividual> linkedIndividuals;
    private Loan loan;
    private List<CustomerConsent> customerConsents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LinkedIndividual {
        private String aadhaarXmlType;
        private String applicantType;
        private Individual individual;
        private List<Address> addresses;
        private List<Contact> contacts;
        private List<Kyc> kyc;
        private List<Doc> docsList;
        private Nsdl nsdl;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Individual {
            private String firstName;
            private String middleName;
            private String lastName;
            private String salutation;
            private String gender;
            private String fullName;
            private String dob;
            private String birthCountry;
            private String fatherName;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Address {
            private String type;
            private String line1;
            private String line2;
            private String city;
            private String state;
            private String country;
            private String pinCode;
            private Integer priority;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Contact {
            private String type;
            private String value;
            private Integer priority;
            private String typeCode;
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
        public static class Doc {
            private String type;
            private String url;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Nsdl {
            private String name;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Loan {
        private String loanProduct;
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
        private ConsentIdentifier consentIdentifier;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class ConsentIdentifier {
            private String ip;
            private String rmn;
        }
    }
}
