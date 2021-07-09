package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateApplicationRequest {
    private Long applicationId;
    private String category;
    private String offerType;
    private AddressDetails addressDetails;
    private ProfessionalDetails professionalDetails;
    private AdditionalDetails additionalDetails;
    private String latitude;
    private String longitude;
}
