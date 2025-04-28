package com.bharatpe.lending.lendingplatform.nbfc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateLeadResponse {
    private String clientId;
    private String leadId;
    private String statusCode;
    private String smbId;
    private String dedupeStatus;
    private BigDecimal allowableExposure;
}
