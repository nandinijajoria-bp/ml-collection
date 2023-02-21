package com.bharatpe.lending.loanV3.dto;

import com.bharatpe.lending.loanV2.dto.AdditionalDetails;
import com.bharatpe.lending.loanV2.dto.AddressDetails;
import com.bharatpe.lending.loanV2.dto.ProfessionalDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateApplicationRequestV3 {
    private Long applicationId;
    private String category;
    private String offerType;
    private AddressDetails addressDetails;
    private ProfessionalDetails professionalDetails;
    private AdditionalDetails additionalDetails;
    private String latitude;
    private String longitude;
    private String businessName;
    private Integer ediModel;
}

