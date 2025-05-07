package com.bharatpe.lending.lendingplatform.underwriting.dto.reresponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class UpdateBureauConsentResponse {
    private String mobile;
    private Boolean bureauConsent;
    private String source;
    private String createdAt;
    private String consentDate;
    private boolean consentExpired;
    private boolean isConsentGettingExpiredInTDays;
    private String bureauMobile;
}
