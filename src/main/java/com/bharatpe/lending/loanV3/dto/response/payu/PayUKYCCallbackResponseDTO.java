package com.bharatpe.lending.loanV3.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayUKYCCallbackResponseDTO {

    private String mac;
    private CallbackData data;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CallbackData {

        @JsonProperty("event_type")
        private String eventType;

        @JsonProperty("application_id")
        private String applicationId;

        @JsonProperty("request_id")
        private String requestId;

        @JsonProperty("event_details")
        private CallbackEventDetails eventDetails;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class CallbackEventDetails {

            private String status;

            private String state;

            @JsonProperty("updated_at")
            private String updatedAt;
        }

    }

}
