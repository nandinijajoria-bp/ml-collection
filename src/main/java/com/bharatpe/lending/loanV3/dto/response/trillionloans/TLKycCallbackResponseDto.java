package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLKycCallbackResponseDto {

        private Integer taskConfigId;
        private String loanApplicationId;
        private Integer signedDocId;
        private String timeStamp;
        private String clientId;

        @JsonProperty("kycstatus")
        private String kycStatus;

        @JsonProperty("aadhaarxmlfacematch")
        private String aadhaarXmlFaceMatchPercentage;

        @JsonProperty("aadhaarxmlnamematchscore")
        private String aadhaarXmlNameMatchPercentage;

        @JsonProperty("aadhaarxmlfacematchStatus")
        private String aadhaarXmlFaceMatchStatus;

        @JsonProperty("aadhaarxmlnamematch")
        private String aadhaarXmlNameMatch;

        @JsonProperty("aadhaarxmlnamematchStatus")
        private String aadhaarXmlNameMatchStatus;

        @JsonProperty("productkey")
        private String productCode;

        @JsonProperty("aadhaarxmlvaliditycheck")
        private String aadhaarXmlValidityCheck;

        @JsonProperty("aadhaarxmlvaliditystatus")
        private String aadhaarXmlValidityStatus;

        @JsonProperty("aadhaarokycnamematch")
        private String aadhaarOkycNameMatch;

        @JsonProperty("aadhaarokycnamematchstatus")
        private String aadhaarOkycNameMatchStatus;

        @JsonProperty("aadhaarokycfacematch")
        private String aadhaarOkycFaceMatch;

        @JsonProperty("aadhaarokycfacematchstatus")
        private String aadhaarOkycFaceMatchStatus;

        @JsonProperty("amlStatus")
        private String amlStatus;

        @JsonProperty("amlstatusCode")
        private String amlStatusCode;

        @JsonProperty("bestMatchName")
        private String amlBestMatchName;

        @JsonProperty("bestMatchScore")
        private String amlBestMatchScore;

        @JsonProperty("amlThreshold")
        private String amlThreshold;

        private List<String> kycRejectionReason;
}
