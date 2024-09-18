package com.bharatpe.lending.loanV3.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayUKycResponseDTO {

    private List<ErrorsList> errors;

    @JsonProperty("application_id")
    private String applicationId;

    private DataList data;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorsList {

        private String message;

        @JsonProperty("error_code")
        private String errorCode;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataList {

        @JsonProperty("kyc_type")
        private String kycType;

        @JsonProperty("kyc_data")
        private KycData kycData;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class KycData {

            @JsonProperty("name_prefix")
            private String namePrefix;

            @JsonProperty("first_name")
            private String firstName;

            @JsonProperty("middle_name")
            private String middleName;

            @JsonProperty("last_name")
            private String lastName;

            private String name;

            private String gender;

            private String dob;

            @JsonProperty("aadhaar_number")
            private String aadhaarNumber;

            @JsonProperty("ckyc_number")
            private String ckycNumber;

            private List<Address> address;

            @Data
            @AllArgsConstructor
            @NoArgsConstructor
            @Builder
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public static class Address {

                @JsonProperty("address_line_1")
                private String addressLine1;

                @JsonProperty("address_line_2")
                private String addressLine2;

                @JsonProperty("address_line_3")
                private String addressLine3;

                private String locality;

                private String city;

                private String district;

                private String state;

                @JsonProperty("pincode")
                private String pinCode;

                private String country;

                @JsonProperty("address_type")
                private String addressType;

            }

            @JsonProperty("record_age")
            private Integer recordAge;

            private String image;

            @JsonProperty("updated_at")
            private String updatedAt;

        }

        @JsonProperty("kyc_details_match")
        private String kycDetailsMatch;

        @JsonProperty("image_match")
        private String imageMatch;

        @JsonProperty("kyc_identity")
        private String kycIdentity;

        @JsonProperty("overall_kyc")
        private String overallKyc;

    }

}
