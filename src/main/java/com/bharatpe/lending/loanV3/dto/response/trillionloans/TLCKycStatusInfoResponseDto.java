package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLCKycStatusInfoResponseDto {

        private PersonalInfo personalInfo;
        private List<AddressInfo> addressInfo;
        private List<DocumentInfo> documentInfo;

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class PersonalInfo {
            private String name;
            private String dob;
            private String gender;
            private String mobile;
            private String fatherName;
        }

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class AddressInfo {
            private String addressLine1;
            private String addressLine2;
            private String addressType;
            private String city;
            private String state;
            private String pincode;
        }

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class DocumentInfo {
            private String documentType;
            private String documentId;
            private String aadharId;
            private String documentExtension;
            private String documentBase64;
            private Boolean video;
        }

}
