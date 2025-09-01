package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLKycValidityResponseDTO {
    private String status;
    private String message;
    private Object traceId;
    private ResponseData data;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseData{
        private String name;
        private List<AddressInfo> addressInfo;
        private String dob;
        private String fatherName;
        private String dependent;
        private List<LastUsed> lastUsed;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AddressInfo{
        private String addressLine1;
        private String addressLine2;
        private String addressType;
        private String city;
        private String pincode;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LastUsed{
        private String aadhaarxmlvaliditycheck;
        private String aadhaarxmlvaliditystatus;
        private String aadhaarxmlfacematch;
        private String aadhaarxmlfacematchStatus;
        private String aadhaarxmlnamematch;
        private String aadhaarxmlnamematchscore;
        private String aadhaarxmlnamematchStatus;
        private Integer loanApplicationId;
        private Integer clientId;
    }
}
