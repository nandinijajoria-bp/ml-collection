package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLEKycStatusCheckResponseDto {
    private String id;
    private String updatedAt;
    private String createdAt;
    private String status;
    private String customerIdentifier;
    private List<Action> actions;
    private String referenceId;
    private Integer expireInDays;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Action {
        private String id;
        private String type;
        private String status;
        private String executionRequestId;
        private Details details;

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Details {
            private Aadhaar aadhaar;

            @Data
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public static class Aadhaar {
                private String idNumber;
                private String name;
                private String dob;
                private String documentType;
                private String idProofType;
                private String gender;
                private String image;
                private String currentAddress;
                private String permanentAddress;
                private AddressDetails currentAddressDetails;
                private AddressDetails permanentAddressDetails;

                @Data
                @JsonInclude(JsonInclude.Include.NON_NULL)
                public static class AddressDetails {
                    private String address;
                    private String localityOrPostOffice;
                    private String districtOrCity;
                    private String state;
                    private String pincode;
                }
            }
        }
    }
}
