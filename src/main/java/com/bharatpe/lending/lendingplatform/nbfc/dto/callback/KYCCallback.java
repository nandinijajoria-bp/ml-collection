package com.bharatpe.lending.lendingplatform.nbfc.dto.callback;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.AadhaarDetails;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.SelfieDetails;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KYCType;
import com.bharatpe.lending.lendingplatform.nbfc.enums.KycCallbackStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class KYCCallback {
    private KycCallbackStatus status;
    private String leadId;
    private AadhaarDetails aadhaarDetails;
    private SelfieDetails selfieDetails;
    private KYCType kycType;
}
