package com.bharatpe.lending.loanV3.dto.request.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroUpdateLeadRequest {
    private String id;
    private String product;
    private UgroCreateLeadRequest.ProfileData profileData;
    private UgroCreateLeadRequest.AcquisitionPlatformData acquisitionPlatformData;
    private UgroCreateLeadRequest.UdyamRegistrationFields udyamRegistrationFields;
}


