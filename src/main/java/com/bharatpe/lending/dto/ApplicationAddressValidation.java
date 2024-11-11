package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Data
@ToString
@Builder
//@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ApplicationAddressValidation {
    private Boolean hasAValidAddress;
    private Long applicationId;
}
