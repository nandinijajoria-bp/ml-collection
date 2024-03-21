package com.bharatpe.lending.loanV3.dto.response.muthoot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MFKycCallbackResponseDTO {
    private KycData data;
    private String error;
    private String statusCode;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KycData {
        private String referenceID;
        private String action;
        private PersonalKYC[] personalKYC;
        private Results results;
        private String status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PersonalKYC {
        private Address address;
        private String dob;
        private String gender;
        private String name;
        private String type;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Address {
        private String city;
        private String country;
        private String district;
        private String landmark;
        private String line1;
        private String line2;
        private String locality;
        private String pincode;
        private String state;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Results {
        private boolean dobMatch;
        private boolean nameMatch;
    }
}

