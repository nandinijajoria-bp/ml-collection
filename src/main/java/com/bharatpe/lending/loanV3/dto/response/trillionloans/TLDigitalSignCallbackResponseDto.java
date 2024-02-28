package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TLDigitalSignCallbackResponseDto {
        private List<String> entities;
        private PayloadDTO payload;
        private Long createdAt;
        private String id;
        private String event;
        private byte[] bytesPdfContent;

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
        public static class PayloadDTO {
                private DocumentDTO document;
        }
        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
        public static class DocumentDTO {
                private Long updatedAt;
                private SignRequestDetailsDTO signRequestDetails;
                private Map<String, Object> attachedEstampDetails;
                private String fileName;
                private String agreementStatus;
                private String id;
                private List<SigningPartyDTO> signingParties;
                private Map<String, Object> others;
        }
        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
        public static class SignRequestDetailsDTO {
                private String identifier;
                private Long expireOn;
                private String name;
                private Long requestedOn;
                private String requesterType;
        }
        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
        public static class SigningPartyDTO {
                private Boolean hasDependents;
                private String signatureMode;
                private String identifier;
                private String reason;
                private String type;
                private String aadhaarMode;
                private String signatureType;
                private Long updatedAt;
                private Long expireOn;
                private String name;
                private Boolean hasAllSigned;
        }
}
