package com.bharatpe.lending.loanV3.dto.request.payu;

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
public class PayUCreateLeadRequestDTO {

    @JsonProperty("pan")
    private String pan;

    @JsonProperty("email")
    private String email;

    @JsonProperty("mobile")
    private String mobile;

    @JsonProperty("channel_code")
    private String channelCode;

    @JsonProperty("external_ref_id")
    private String externalRefId;

    @JsonProperty("entity_type")
    private String entityType;

    @JsonProperty("gstin")
    private String gstin;

    @JsonProperty("company_pan")
    private String companyPan;

    @JsonProperty("is_mobile_verified")
    private boolean isMobileVerified;

    @JsonProperty("is_email_verified")
    private boolean isEmailVerified;

    @JsonProperty("compliance")
    private ComplianceDTO compliance;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComplianceDTO {

        @JsonProperty("bureau_consent")
        private boolean bureauConsent;

        @JsonProperty("general_consent")
        private boolean generalConsent;

        @JsonProperty("kyc_consent")
        private boolean kycConsent;
    }

}
