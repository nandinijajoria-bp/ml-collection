package com.bharatpe.lending.loanV3.dto.response.usfb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanOnboardingCallbackResponseDTO {
    private String eventName;
    private String partnerApplicationId;
    private String partnerId;
    private Payload payload;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payload {
        private String leadId;
        private String decisionStatus;
        @JsonProperty("kyc_approved")
        private String kycApproved;
        @JsonProperty("fcu_approved")
        private String fcuApproved;
        @JsonProperty("vkyc_approved")
        private String vkycApproved;
        @JsonProperty("final_approval_status")
        private String finalApprovalStatus;
    }
}
