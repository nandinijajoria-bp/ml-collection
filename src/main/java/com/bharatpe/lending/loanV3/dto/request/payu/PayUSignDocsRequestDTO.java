package com.bharatpe.lending.loanV3.dto.request.payu;

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
public class PayUSignDocsRequestDTO {

    @JsonProperty("application-id")
    private String applicationId;

    @JsonProperty("request_details")
    private List<RequestDetails> requestDetails;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RequestDetails {

        @JsonProperty("document_details")
        private DocumentDetails documentDetails;

        @JsonProperty("acceptance_details")
        private AcceptanceDetails acceptanceDetails;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class DocumentDetails {

            @JsonProperty("document_id")
            private String documentId;

            private String type;
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class AcceptanceDetails {

            @JsonProperty("signing_details")
            private SigningDetails signingDetails;

            @Data
            @AllArgsConstructor
            @NoArgsConstructor
            @Builder
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public static class SigningDetails {

                private String date;

                private String time;

                private String ip;

                @JsonProperty("mode_of_signing")
                private String modeOfSigning;

                private String otp;

            }

        }

    }

}
