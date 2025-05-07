package com.bharatpe.lending.lendingplatform.underwriting.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class UpdateBureauConsentRequest {
    private Boolean consentExpired;
    private String bureauMobile;
}
