package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TLEKycCallbackResponseDto {

        public List<String> entities;
        public String createdAt;
        public String id;
        public String event;
        public Payload payload;


        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Payload {

            @JsonProperty("kyc_request")
            public KycRequest kycRequest;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class KycRequest {
            public String id;
            public String status;
            public Object type;
            public Object state;
            @JsonProperty("transaction_id")
            public String transactionId;
            public Object others;

            @JsonProperty("reference_id")
            public String referenceId;

            @JsonProperty("acton_ref")
            public Object actonRef;
            @JsonProperty("customer_name")
            public Object customerName;
            @JsonProperty("customer_identifier")
            public Object customerIdentifier;
            @JsonProperty("kyc_request_id")
            public Object kycRequestId;
            @JsonProperty("shared_documents")
            public Object sharedDocuments;
        }
}


