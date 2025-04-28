package com.bharatpe.lending.lendingplatform.nbfc.dto.response;

import com.bharatpe.lending.lendingplatform.nbfc.enums.KYCType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KYCResponse {
    private String statusCode;
    private String digiLockerUrl;
    private String leadId;
    private KYCType kycType;
}
