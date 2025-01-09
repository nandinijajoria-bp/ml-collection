package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLCKycCallbackResponseDto {
        private Integer taskConfigId;
        private String loanApplicationId;
        private Integer signedDocId;
        private String timeStamp;
        private String clientId;

        @JsonProperty("kycstatus")
        private String kycStatus;

        @JsonProperty("aadhaarxmlfacematch")
        private String aadhaarXmlFaceMatch;

        @JsonProperty("aadhaarxmlnamematchscore")
        private String aadhaarXmlNameMatchScore;

        @JsonProperty("aadhaarxmlfacematchStatus")
        private String aadhaarXmlFaceMatchStatus;

        @JsonProperty("aadhaarxmlnamematch")
        private String aadhaarXmlNameMatch;

        @JsonProperty("aadhaarxmlnamematchStatus")
        private String aadhaarXmlNameMatchStatus;

        private String productkey;

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

        @JsonProperty("ckycNamematchstatus")
        private String ckycNameMatchStatus;

        @JsonProperty("ckycfacematchstatus")
        private String ckycFaceMatchStatus;

        @JsonProperty("ckycNamematchscore")
        private String ckycNameMatchScore;

        @JsonProperty("ckycfacematchscore")
        private String ckycFaceMatchScore;

        private String amlStatus;
        private String amlstatusCode;
        private String bestMatchName;
        private String bestMatchScore;
        private String amlThreshold;

        @JsonProperty("document_type_ids")
        private String documentTypeIds;

        @JsonProperty("document_keys")
        private String documentKeys;

        private String rejectReason;

        @JsonProperty("ReasonForReject")
        private String reasonForReject;

        private String workflowName;
        private String status;
}
